package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.Book;
import com.library.entity.BookItem;
import com.library.entity.BookReserve;
import com.library.entity.PayOrder;
import com.library.entity.PurchaseOrder;
import com.library.entity.User;
import com.library.event.PaySuccessEvent;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BookMapper;
import com.library.mapper.BookReserveMapper;
import com.library.mapper.PurchaseOrderMapper;
import com.library.mapper.UserMapper;
import com.library.service.PayService;
import com.library.service.PurchaseService;
import com.library.service.AgreementService;
import com.library.service.ReserveService;
import com.library.util.OrderNoGenerator;
import com.library.util.UserContext;
import com.library.vo.PayVO;
import com.library.vo.PurchaseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 购买服务实现
 * <p>
 * 购买与借阅的区别：借阅是付押金、书要还；购买是付书款、书归用户、不退。
 */
@Slf4j
@Service
public class PurchaseServiceImpl implements PurchaseService {

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private BookItemMapper bookItemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PayService payService;
    @Autowired
    private AgreementService agreementService;
    @Autowired
    private ReserveService reserveService;
    @Autowired
    private OrderNoGenerator orderNoGenerator;
    @Autowired
    private BookReserveMapper bookReserveMapper;

    @Override
    @Transactional
    public PayVO buy(Long bookId) {
        if (bookId == null) {
            throw new BusinessException("请选择要购买的图书");
        }
        Long userId = UserContext.getUserId();
        // 锁用户行，串行化同一用户的并发购买校验
        if (userMapper.lockById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        // 掏钱之前必须已知晓规则
        agreementService.assertAgreed(userId, Constants.AGREEMENT_TYPE_PURCHASE);
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        if (book.getPrice() == null || book.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该图书未设置售价");
        }
        // 这里刻意不再校验「是否已购买过」：
        // 买过只是既成事实，不代表失去再买一本（送人/收藏）的权利。
        // 库存由副本数量天然限流，不需要额外拦截

        // 预约优先权：与借阅走同一套规则。
        // 少了这一步，「直接购买」就能把预约读者唯一的一册抢走，
        // 到书通知等于白发，读者来了发现书没了
        reserveService.assertBorrowAllowed(userId, bookId,
                book.getStock() == null ? 0 : book.getStock());

        // 已有未完成的购买单时：还能付就复用，已过期就释放。
        // 不能直接新建——每建一单就锁一册副本，用户连点几次会把库存锁光
        PurchaseOrder pendingOrder = findPendingOrder(userId, bookId);
        if (pendingOrder != null) {
            PayOrder pendingPay = pendingOrder.getPayOrderNo() == null ? null
                    : payService.getByOrderNo(pendingOrder.getPayOrderNo());
            if (pendingPay != null
                    && pendingPay.getStatus() == Constants.PAY_STATUS_UNPAID
                    && pendingPay.getExpireTime() != null
                    && pendingPay.getExpireTime().isAfter(LocalDateTime.now())) {
                return toPayVO(pendingPay);
            }
            releaseOrder(pendingOrder, "重新下单，释放上一笔未支付订单");
        }

        // 取一册在馆副本
        List<BookItem> available = bookItemMapper.selectList(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBookId, bookId)
                .eq(BookItem::getStatus, Constants.ITEM_STATUS_IN_LIBRARY)
                .orderByAsc(BookItem::getBarcode)
                .last("LIMIT 1"));
        if (available.isEmpty()) {
            throw new BusinessException("该书暂时无货，可先提交预约");
        }
        BookItem item = available.get(0);
        // 锁定副本：在馆 -> 已预留，防止同时被别人借走或买走
        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_IN_LIBRARY, Constants.ITEM_STATUS_RESERVED) == 0) {
            throw new BusinessException("该册已被他人抢先锁定，请重试");
        }

        LocalDateTime now = LocalDateTime.now();
        PurchaseOrder order = new PurchaseOrder();
        order.setUserId(userId);
        order.setBookId(bookId);
        order.setItemId(item.getId());
        order.setOrderNo(orderNoGenerator.next("PR"));
        order.setBuyType(Constants.BUY_TYPE_DIRECT);
        order.setPrice(book.getPrice());
        order.setStatus(0);
        order.setCreateTime(now);
        purchaseOrderMapper.insert(order);

        PayOrder payOrder = payService.createOrder(userId, Constants.PAY_BIZ_PURCHASE,
                order.getId(), book.getPrice());
        order.setPayOrderNo(payOrder.getOrderNo());
        purchaseOrderMapper.updateById(order);

        bookItemMapper.refreshStock(bookId);

        return toPayVO(payOrder);
    }

    /**
     * 支付成功回调：把锁定的副本转为已售出
     * <p>
     * 用事件而不是让 PayService 直接调用，避免支付模块与业务模块互相依赖。
     * 监听器与支付入账处在同一事务，任何一步失败都会整体回滚。
     */
    @EventListener
    @Transactional
    public void onPaySuccess(PaySuccessEvent event) {
        PayOrder payOrder = event.getPayOrder();
        // 购买单 + 合并订单都可能有购买记录
        boolean combined = payOrder.getBizType() == Constants.PAY_BIZ_COMBINED;
        if (payOrder.getBizType() != Constants.PAY_BIZ_PURCHASE && !combined) {
            return;
        }

        List<PurchaseOrder> orders;
        if (combined) {
            // 合并订单：按支付单号反查所有待支付购买单
            orders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getPayOrderNo, payOrder.getOrderNo())
                    .eq(PurchaseOrder::getStatus, 0));
        } else {
            PurchaseOrder single = purchaseOrderMapper.selectById(payOrder.getBizId());
            orders = single == null ? new ArrayList<>() : java.util.Collections.singletonList(single);
        }

        for (PurchaseOrder order : orders) {
            if (order == null) {
                continue;
            }
            // 幂等：只有待支付的单才能推进；推进后进入「待取书」
            int updated = purchaseOrderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getId, order.getId())
                    .eq(PurchaseOrder::getStatus, 0)
                    .set(PurchaseOrder::getStatus, 1)
                    .set(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_PENDING));
            if (updated == 0) {
                continue;
            }
            // 副本保持「已预留」，不直接转「已售出」：
            // 钱付了但书还在馆里等用户来拿，这个阶段用户可以反悔退款，
            // 只有到馆核销取书后才算真正售出
            log.info("购买付款完成，进入待取书 purchaseOrderId={} itemId={} price={}",
                    order.getId(), order.getItemId(), order.getPrice());
        }
    }

    @Override
    public List<PurchaseVO> myOrders() {
        Long userId = UserContext.getUserId();
        return listByUser(userId);
    }

    @Override
    public List<PurchaseVO> listByUser(Long userId) {
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getUserId, userId)
                .orderByDesc(PurchaseOrder::getCreateTime));
        return convert(orders);
    }

    @Override
    public Page<PurchaseVO> pageOrders(Integer page, Integer size, String keyword, Integer buyType) {
        int pageNo = PageUtil.normalizePage(page);
        int pageSize = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        if (buyType != null) {
            wrapper.eq(PurchaseOrder::getBuyType, buyType);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .and(w -> w.like(User::getAccount, kw).or().like(User::getName, kw)))
                    .stream().map(User::getId).collect(Collectors.toList());
            List<Long> matchedBookIds = bookMapper.selectList(new LambdaQueryWrapper<Book>()
                            .select(Book::getId)
                            .like(Book::getName, kw))
                    .stream().map(Book::getId).collect(Collectors.toList());
            boolean noRelated = matchedUserIds.isEmpty() && matchedBookIds.isEmpty();
            wrapper.and(w -> {
                boolean empty = true;
                if (!noRelated) {
                    if (!matchedUserIds.isEmpty()) {
                        w.in(PurchaseOrder::getUserId, matchedUserIds);
                        empty = false;
                    }
                    if (!matchedBookIds.isEmpty()) {
                        if (!empty) {
                            w.or();
                        }
                        w.in(PurchaseOrder::getBookId, matchedBookIds);
                        empty = false;
                    }
                }
                if (!empty) {
                    w.or();
                }
                w.like(PurchaseOrder::getOrderNo, kw);
            });
        }
        wrapper.orderByDesc(PurchaseOrder::getCreateTime);
        Page<PurchaseOrder> orderPage = purchaseOrderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Page<PurchaseVO> voPage = new Page<>(pageNo, pageSize, orderPage.getTotal());
        voPage.setRecords(convert(orderPage.getRecords()));
        return voPage;
    }

    private List<PurchaseVO> convert(List<PurchaseOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = orders.stream().map(PurchaseOrder::getUserId).distinct().collect(Collectors.toList());
        List<Long> bookIds = orders.stream().map(PurchaseOrder::getBookId).distinct().collect(Collectors.toList());
        List<Long> itemIds = orders.stream().map(PurchaseOrder::getItemId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Book> bookMap = bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));
        Map<Long, BookItem> itemMap = itemIds.isEmpty() ? java.util.Collections.emptyMap()
                : bookItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(BookItem::getId, i -> i));

        return orders.stream().map(o -> {
            PurchaseVO vo = new PurchaseVO();
            vo.setId(o.getId());
            vo.setUserId(o.getUserId());
            vo.setBookId(o.getBookId());
            vo.setOrderNo(o.getOrderNo());
            vo.setBuyType(o.getBuyType());
            vo.setPrice(o.getPrice());
            vo.setCreateTime(o.getCreateTime());
            vo.setStatus(o.getStatus());
            vo.setPickupStatus(o.getPickupStatus());
            vo.setPickupTime(o.getPickupTime());
            vo.setStatusText(orderStatusText(o));
            // 只有「已支付 + 待取书」才允许取消退款
            vo.setCancelable(o.getStatus() != null && o.getStatus() == 1
                    && o.getPickupStatus() != null
                    && o.getPickupStatus() == Constants.PICKUP_STATUS_PENDING);
            if (o.getItemId() != null) {
                BookItem item = itemMap.get(o.getItemId());
                if (item != null) {
                    vo.setBarcode(item.getBarcode());
                }
            }
            User u = userMap.get(o.getUserId());
            if (u != null) {
                vo.setUserAccount(u.getAccount());
                vo.setUserName(u.getName());
            }
            Book book = bookMap.get(o.getBookId());
            if (book != null) {
                vo.setBookName(book.getName());
                vo.setBookCover(book.getCover());
            } else {
                vo.setBookName("（图书已下架）");
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 订单展示状态：待支付 / 待取书 / 已取书 / 已取消
     */
    private String orderStatusText(PurchaseOrder o) {
        if (o.getStatus() == null) {
            return "未知";
        }
        if (o.getStatus() == 0) {
            return "待支付";
        }
        if (o.getStatus() == 2) {
            return "已取消";
        }
        // status == 1
        return o.getPickupStatus() != null && o.getPickupStatus() == Constants.PICKUP_STATUS_DONE
                ? "已取书" : "待取书";
    }

    @Override
    @Transactional
    public void cancelByPayOrder(String payOrderNo) {
        if (!StringUtils.hasText(payOrderNo)) {
            return;
        }
        // 合并订单一个支付单对应多条购买单，逐条释放
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getPayOrderNo, payOrderNo));
        Set<Long> bookIds = new LinkedHashSet<>();
        for (PurchaseOrder order : orders) {
            releaseOrder(order, "支付超时，订单已自动关闭");
            if (order.getBookId() != null) {
                bookIds.add(order.getBookId());
            }
        }
        // 副本真的回到在馆了，才叫号。少了这一步，排队读者永远收不到到书通知：
        // 书在架上，人却一直停在「排队中」
        for (Long bookId : bookIds) {
            reserveService.wakeUpQueue(bookId);
        }
    }

    /**
     * 查该用户对这本书是否存在待支付的购买单
     */
    private PurchaseOrder findPendingOrder(Long userId, Long bookId) {
        List<PurchaseOrder> list = purchaseOrderMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getUserId, userId)
                        .eq(PurchaseOrder::getBookId, bookId)
                        .eq(PurchaseOrder::getStatus, 0)
                        .orderByDesc(PurchaseOrder::getId)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 取消一笔待支付购买单，并把锁定的副本释放回在馆。
     * <p>
     * 不释放的话，超时未支付的订单会永久占着一册库存，别人看不到也借不到。
     */
    private void releaseOrder(PurchaseOrder order, String reason) {
        if (order == null) {
            return;
        }
        int updated = purchaseOrderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, order.getId())
                .eq(PurchaseOrder::getStatus, 0)
                .set(PurchaseOrder::getStatus, 2));
        if (updated == 0) {
            return;
        }
        if (order.getItemId() != null) {
            bookItemMapper.changeStatus(order.getItemId(),
                    Constants.ITEM_STATUS_RESERVED, Constants.ITEM_STATUS_IN_LIBRARY);
            bookItemMapper.refreshStock(order.getBookId());
        }
        // 这里刻意不叫号：本方法也被「重新下单」复用，那种场景下副本马上会被
        // 同一个用户重新锁定，此时叫号等于通知读者来取一本已经不存在的书。
        // 真正回到流通的路径（超时关单、取消待取书）由调用方另行唤醒。
        log.info("购买单已取消 orderId={} itemId={} reason={}",
                order.getId(), order.getItemId(), reason);
    }

    // ==================== 待取书阶段：取消退款 / 到馆核销 ====================

    @Override
    @Transactional
    public void cancelPending(Long orderId) {
        Long userId = UserContext.getUserId();
        PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 1 || order.getPickupStatus() == null
                || order.getPickupStatus() != Constants.PICKUP_STATUS_PENDING) {
            throw new BusinessException("只有「待取书」的订单才能取消");
        }

        // 幂等闸门：仍待取书才能改成已取消
        int updated = purchaseOrderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, orderId)
                .eq(PurchaseOrder::getStatus, 1)
                .eq(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_PENDING)
                .set(PurchaseOrder::getStatus, 2));
        if (updated == 0) {
            throw new BusinessException("该订单已处理，请刷新后查看");
        }

        // 退款失败直接抛错回滚，避免出现「订单取消了、钱却没退」
        BigDecimal price = order.getPrice() == null ? BigDecimal.ZERO : order.getPrice();
        if (order.getPayOrderNo() != null && price.compareTo(BigDecimal.ZERO) > 0) {
            try {
                payService.refund(order.getPayOrderNo(), price,
                        "取消购买（未取书）", order.getId() + "_P1");
            } catch (Exception e) {
                log.error("取消购买订单退款失败 orderId={} 金额={}", orderId, price, e);
                throw new BusinessException("退款失败，请稍后重试");
            }
        }

        // 书还在馆里，原样释放回馆藏，可以继续卖给别人
        if (order.getItemId() != null) {
            bookItemMapper.changeStatus(order.getItemId(),
                    Constants.ITEM_STATUS_RESERVED, Constants.ITEM_STATUS_IN_LIBRARY);
            bookItemMapper.refreshStock(order.getBookId());
            // 副本真的回到流通了才叫号：不叫的话书在架上，排队的人却一直停在「排队中」
            reserveService.wakeUpQueue(order.getBookId());
        }
        log.info("购买订单已取消并退款 orderId={} bookId={} 金额={}",
                orderId, order.getBookId(), price);
    }

    @Override
    @Transactional
    public PurchaseVO pickup(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            throw new BusinessException("请扫描或输入副本条码");
        }
        BookItem item = bookItemMapper.selectOne(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBarcode, barcode.trim()));
        if (item == null) {
            throw new BusinessException("副本不存在，请检查条码");
        }
        PurchaseOrder order = purchaseOrderMapper.selectOne(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getItemId, item.getId())
                .eq(PurchaseOrder::getStatus, 1)
                .eq(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_PENDING)
                .orderByDesc(PurchaseOrder::getId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException("未找到该副本的待取书订单，请确认是不是这本书");
        }
        return pickupByOrderId(order.getId());
    }

    @Override
    @Transactional
    public PurchaseVO pickupByOrderId(Long orderId) {
        PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 1 || order.getPickupStatus() == null
                || order.getPickupStatus() != Constants.PICKUP_STATUS_PENDING) {
            throw new BusinessException("该订单不在待取书状态");
        }
        if (order.getItemId() == null) {
            throw new BusinessException("该订单没有关联副本，无法核销");
        }
        BookItem item = bookItemMapper.selectById(order.getItemId());
        if (item == null) {
            throw new BusinessException("副本不存在");
        }
        if (item.getStatus() != Constants.ITEM_STATUS_RESERVED) {
            throw new BusinessException("副本「" + item.getBarcode() + "」不在待取书状态");
        }

        // 副本 已预留 -> 已售出（此时才真正从馆藏移除）
        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_RESERVED, Constants.ITEM_STATUS_SOLD) == 0) {
            throw new BusinessException("核销失败，该副本状态已被其他操作改变");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = purchaseOrderMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, order.getId())
                .eq(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_PENDING)
                .set(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_DONE)
                .set(PurchaseOrder::getPickupTime, now));
        if (updated == 0) {
            throw new BusinessException("核销失败，订单状态已被改变");
        }
        bookItemMapper.refreshStock(order.getBookId());
        // 读者可能是收到到书通知后改买下来的，这条预约已经兑现；
        // 不结算的话「我的预约」里会继续挂着一个已无意义的待借阅倒计时
        settleReserve(order.getUserId(), order.getBookId());

        order.setPickupStatus(Constants.PICKUP_STATUS_DONE);
        order.setPickupTime(now);
        log.info("购买取书核销完成 orderId={} barcode={}", order.getId(), item.getBarcode());
        return convert(java.util.Collections.singletonList(order)).get(0);
    }

    /**
     * 购买完成（书已归读者）后，把他对这本书还没结束的预约置为已借阅。
     * <p>
     * 和借出核销同一个道理：预约的目的就是「拿到这本书」，
     * 无论最后是借到还是买到，这条预约都不该继续占着队列或显示待借阅。
     */
    private void settleReserve(Long userId, Long bookId) {
        List<BookReserve> list = bookReserveMapper.selectList(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getUserId, userId)
                .eq(BookReserve::getBookId, bookId)
                .in(BookReserve::getStatus,
                        Constants.RESERVE_STATUS_QUEUING, Constants.RESERVE_STATUS_NOTIFIED));
        for (BookReserve reserve : list) {
            reserve.setStatus(Constants.RESERVE_STATUS_BORROWED);
            bookReserveMapper.updateById(reserve);
        }
    }

    private PayVO toPayVO(PayOrder payOrder) {
        PayVO vo = new PayVO();
        vo.setOrderNo(payOrder.getOrderNo());
        vo.setAmount(payOrder.getAmount());
        vo.setStatus(payOrder.getStatus());
        vo.setExpireTime(payOrder.getExpireTime());
        if (payOrder.getStatus() == Constants.PAY_STATUS_UNPAID) {
            vo.setPayForm(payService.buildPayForm(payOrder.getOrderNo()));
        }
        return vo;
    }
}
