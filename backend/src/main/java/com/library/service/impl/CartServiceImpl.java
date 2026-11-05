package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.entity.Book;
import com.library.entity.BookItem;
import com.library.entity.BookReserve;
import com.library.entity.Borrow;
import com.library.entity.PayOrder;
import com.library.entity.PurchaseOrder;
import com.library.entity.User;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BookMapper;
import com.library.mapper.BookReserveMapper;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PurchaseOrderMapper;
import com.library.mapper.UserMapper;
import com.library.service.AgreementService;
import com.library.service.CartService;
import com.library.service.PayService;
import com.library.service.ReserveService;
import com.library.util.OrderNoGenerator;
import com.library.util.UserContext;
import com.library.vo.CartItemVO;
import com.library.vo.PayVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车服务实现
 * <p>
 * 合并结算的核心不变量：N 条业务单共享同一个支付单号（pay_order_no）。
 * 支付成功后，借阅监听器与购买监听器各自按 pay_order_no 反查、逐条幂等推进。
 */
@Slf4j
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private BorrowMapper borrowMapper;
    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private BookItemMapper bookItemMapper;
    @Autowired
    private BookReserveMapper bookReserveMapper;
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

    @Value("${library.borrow.max-count:5}")
    private int maxCount;
    @Value("${library.borrow.days:30}")
    private int borrowDays;

    @Override
    @Transactional
    public PayVO checkout(List<CartItemVO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("购物车为空，请先添加图书");
        }
        Long userId = UserContext.getUserId();
        // 锁用户行，串行化同一用户的并发结算校验
        if (userMapper.lockById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        // 协议按本次实际涉及的方式校验：只借不买的人不该被《购买须知》拦住。
        // 之前无条件要求两条都签，而结算页一次只有一种方式、也只弹一条协议，
        // 新读者就卡死在收银台，提示还指不到缺的那份。
        boolean hasBorrow = false;
        boolean hasBuy = false;
        for (CartItemVO item : items) {
            if ("buy".equals(item.getMode())) {
                hasBuy = true;
            } else {
                hasBorrow = true;
            }
        }
        if (hasBorrow) {
            agreementService.assertAgreed(userId, Constants.AGREEMENT_TYPE_BORROW);
        }
        if (hasBuy) {
            agreementService.assertAgreed(userId, Constants.AGREEMENT_TYPE_PURCHASE);
        }

        // 已占用的借阅额度（待取书 + 借阅中）
        long occupied = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, userId)
                .in(Borrow::getStatus,
                        Constants.BORROW_STATUS_PENDING_PICKUP, Constants.BORROW_STATUS_BORROWING));

        int borrowCount = 0;
        BigDecimal totalDeposit = BigDecimal.ZERO;
        BigDecimal totalPrice = BigDecimal.ZERO;
        List<Borrow> newBorrows = new ArrayList<>();
        List<PurchaseOrder> newOrders = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (CartItemVO item : items) {
            Long bookId = item.getBookId();
            String mode = item.getMode() == null ? "borrow" : item.getMode();
            Book book = bookMapper.selectById(bookId);
            if (book == null) {
                throw new BusinessException("图书不存在");
            }

            if ("buy".equals(mode)) {
                // 购买校验：只拦未支付的重复单，不拦「以前买过」。
                // 买过再买一本是用户自己的选择，库存由副本数天然限流
                if (book.getPrice() == null || book.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("《" + book.getName() + "》未设置售价");
                }
                Long pending = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getUserId, userId)
                        .eq(PurchaseOrder::getBookId, bookId)
                        .eq(PurchaseOrder::getStatus, 0));
                if (pending != null && pending > 0) {
                    throw new BusinessException("《" + book.getName() + "》已有待支付订单");
                }
                // 预约优先权：购买同样要让位给已收到到书通知的读者，
                // 否则购物车就成了抢书的旁路
                reserveService.assertBorrowAllowed(userId, bookId,
                        book.getStock() == null ? 0 : book.getStock());
                BookItem buyItem = lockAvailableCopy(bookId);
                PurchaseOrder order = new PurchaseOrder();
                order.setUserId(userId);
                order.setBookId(bookId);
                order.setItemId(buyItem.getId());
                order.setOrderNo(orderNoGenerator.next("PR"));
                order.setBuyType(Constants.BUY_TYPE_DIRECT);
                order.setPrice(book.getPrice());
                order.setStatus(0);
                order.setCreateTime(now);
                purchaseOrderMapper.insert(order);
                newOrders.add(order);
                totalPrice = totalPrice.add(book.getPrice());
            } else {
                // 借阅校验
                borrowCount++;
                if (occupied + borrowCount > maxCount) {
                    throw new BusinessException("借阅数量将超过上限（" + maxCount + " 本），请减少购物车中的图书");
                }
                if (book.getPrice() == null || book.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("《" + book.getName() + "》未设置定价，暂不可借阅");
                }
                // 同一本书不允许重复借
                Long sameBook = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                        .eq(Borrow::getUserId, userId)
                        .eq(Borrow::getBookId, bookId)
                        .in(Borrow::getStatus,
                                Constants.BORROW_STATUS_PENDING_PICKUP, Constants.BORROW_STATUS_BORROWING));
                if (sameBook != null && sameBook > 0) {
                    throw new BusinessException("您已借阅或预订了《" + book.getName() + "》");
                }
                // 预约优先权：与 /borrow/apply 共用同一套校验。
                // 少了这一步，购物车就是绕过预约队列的后门——已通知取书的人会被别人截胡
                reserveService.assertBorrowAllowed(userId, bookId,
                        book.getStock() == null ? 0 : book.getStock());
                BookItem borrowItem = lockAvailableCopy(bookId);
                Borrow borrow = new Borrow();
                borrow.setUserId(userId);
                borrow.setBookId(bookId);
                borrow.setItemId(borrowItem.getId());
                borrow.setBorrowTime(now);
                borrow.setDeadlineTime(now.plusDays(borrowDays));
                borrow.setDepositAmount(book.getPrice());
                borrow.setDeductAmount(BigDecimal.ZERO);
                borrow.setRefundAmount(BigDecimal.ZERO);
                borrow.setStatus(Constants.BORROW_STATUS_PENDING_PICKUP);
                borrow.setIsOverdue(0);
                borrow.setCreateTime(now);
                borrow.setUpdateTime(now);
                borrowMapper.insert(borrow);
                newBorrows.add(borrow);
                totalDeposit = totalDeposit.add(book.getPrice());
            }
        }

        // 总金额 = 押金 + 书款
        BigDecimal totalAmount = totalDeposit.add(totalPrice);
        // 建一个合并支付单
        PayOrder payOrder = payService.createOrder(userId, Constants.PAY_BIZ_COMBINED,
                null, totalAmount);

        // 回填支付单号给所有业务单
        for (Borrow b : newBorrows) {
            b.setPayOrderNo(payOrder.getOrderNo());
            borrowMapper.updateById(b);
        }
        for (PurchaseOrder o : newOrders) {
            o.setPayOrderNo(payOrder.getOrderNo());
            purchaseOrderMapper.updateById(o);
        }

        // 刷新涉及的库存
        for (CartItemVO item : items) {
            bookItemMapper.refreshStock(item.getBookId());
        }

        PayVO vo = new PayVO();
        vo.setOrderNo(payOrder.getOrderNo());
        vo.setAmount(totalAmount);
        vo.setStatus(payOrder.getStatus());
        vo.setExpireTime(payOrder.getExpireTime());
        vo.setPayForm(payService.buildPayForm(payOrder.getOrderNo()));
        return vo;
    }

    /**
     * 锁定一册在馆副本（在馆 -> 已预留），并发抢同一册时只有一个成功
     */
    private BookItem lockAvailableCopy(Long bookId) {
        List<BookItem> available = bookItemMapper.selectList(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBookId, bookId)
                .eq(BookItem::getStatus, Constants.ITEM_STATUS_IN_LIBRARY)
                .orderByAsc(BookItem::getBarcode)
                .last("LIMIT 1"));
        if (available.isEmpty()) {
            Book book = bookMapper.selectById(bookId);
            throw new BusinessException("《" + (book == null ? "该书" : book.getName()) + "》暂时无货");
        }
        BookItem item = available.get(0);
        if (bookItemMapper.changeStatus(item.getId(),
                Constants.ITEM_STATUS_IN_LIBRARY, Constants.ITEM_STATUS_RESERVED) == 0) {
            throw new BusinessException("《" + item.getBarcode() + "》已被他人抢先锁定，请重试");
        }
        return item;
    }
}
