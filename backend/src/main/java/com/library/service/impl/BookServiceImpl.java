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
import com.library.entity.BookType;
import com.library.entity.Borrow;
import com.library.entity.PayOrder;
import com.library.entity.PurchaseOrder;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BookMapper;
import com.library.mapper.BookReserveMapper;
import com.library.mapper.BookTypeMapper;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PayOrderMapper;
import com.library.mapper.PurchaseOrderMapper;
import com.library.service.BookItemService;
import com.library.service.BookService;
import com.library.util.FileUtil;
import com.library.util.UserContext;
import com.library.vo.BookDetailVO;
import com.library.vo.BookVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图书服务实现
 */
@Service
public class BookServiceImpl implements BookService {

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private BookTypeMapper bookTypeMapper;

    @Autowired
    private BorrowMapper borrowMapper;

    @Autowired
    private BookReserveMapper bookReserveMapper;

    @Autowired
    private BookItemMapper bookItemMapper;

    @Autowired
    private BookItemService bookItemService;

    @Autowired
    private PayOrderMapper payOrderMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private FileUtil fileUtil;

    @Override
    public Page<BookVO> pageBooks(Integer page, Integer size, String keyword, Long typeId) {
        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Book::getName, keyword)
                    .or().like(Book::getAuthor, keyword)
                    .or().like(Book::getIsbn, keyword));
        }
        if (typeId != null) {
            wrapper.eq(Book::getTypeId, typeId);
        }
        wrapper.orderByDesc(Book::getCreateTime);
        Page<Book> bookPage = bookMapper.selectPage(PageUtil.of(page, size), wrapper);

        // 批量查询分类名
        List<Long> typeIds = bookPage.getRecords().stream()
                .map(Book::getTypeId).distinct().collect(Collectors.toList());
        Map<Long, String> typeNameMap = typeIds.isEmpty() ? Map.of() :
                bookTypeMapper.selectBatchIds(typeIds).stream()
                        .collect(Collectors.toMap(BookType::getId, BookType::getName));

        Page<BookVO> voPage = new Page<>(bookPage.getCurrent(), bookPage.getSize(), bookPage.getTotal());
        List<BookVO> voList = bookPage.getRecords().stream().map(b -> {
            BookVO vo = new BookVO();
            BeanUtils.copyProperties(b, vo);
            vo.setTypeName(typeNameMap.getOrDefault(b.getTypeId(), "未分类"));
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public BookVO getById(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        BookVO vo = new BookVO();
        BeanUtils.copyProperties(book, vo);
        if (book.getTypeId() != null) {
            BookType type = bookTypeMapper.selectById(book.getTypeId());
            vo.setTypeName(type == null ? "未分类" : type.getName());
        }
        return vo;
    }

    @Override
    public BookDetailVO getDetail(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        BookDetailVO vo = new BookDetailVO();
        BeanUtils.copyProperties(book, vo);
        if (book.getTypeId() != null) {
            BookType type = bookTypeMapper.selectById(book.getTypeId());
            vo.setTypeName(type == null ? "未分类" : type.getName());
        }

        Long userId = UserContext.getUserId();
        // 未登录（或异常）时只返回基本信息，userStatus 为 0
        if (userId == null) {
            vo.setUserStatus(0);
            vo.setPurchased(false);
            return vo;
        }

        // 购买标记先算出来，它不影响下面任何分支的判断。
        // 买过只是既成事实，读者仍可继续借阅，也可以再买一本
        Long bought = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getUserId, userId)
                .eq(PurchaseOrder::getBookId, id)
                .eq(PurchaseOrder::getStatus, 1));
        vo.setPurchased(bought != null && bought > 0);

        // userStatus 只描述「正在进行的事」：借阅中 > 待取书(已付) > 待支付 > 预约(已通知) > 预约(排队)

        // 1. 进行中的借阅（借阅中 / 待取书）
        Borrow activeBorrow = borrowMapper.selectOne(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, userId)
                .eq(Borrow::getBookId, id)
                .in(Borrow::getStatus,
                        Constants.BORROW_STATUS_BORROWING,
                        Constants.BORROW_STATUS_PENDING_PICKUP)
                .orderByDesc(Borrow::getId)
                .last("LIMIT 1"));

        if (activeBorrow != null) {
            if (activeBorrow.getStatus() == Constants.BORROW_STATUS_BORROWING) {
                vo.setUserStatus(1);
                return vo;
            }
            PayOrder pay = activeBorrow.getPayOrderNo() == null ? null
                    : payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                            .eq(PayOrder::getOrderNo, activeBorrow.getPayOrderNo()));
            vo.setUserStatus(pay != null && pay.getStatus() == Constants.PAY_STATUS_UNPAID ? 3 : 2);
            return vo;
        }

        // 2. 预约
        BookReserve myReserve = bookReserveMapper.selectOne(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getUserId, userId)
                .eq(BookReserve::getBookId, id)
                .in(BookReserve::getStatus,
                        Constants.RESERVE_STATUS_QUEUING, Constants.RESERVE_STATUS_NOTIFIED)
                .orderByDesc(BookReserve::getId)
                .last("LIMIT 1"));
        if (myReserve != null) {
            if (myReserve.getStatus() == Constants.RESERVE_STATUS_NOTIFIED) {
                vo.setUserStatus(5);
            } else {
                vo.setUserStatus(4);
                Long pos = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                        .eq(BookReserve::getBookId, id)
                        .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_QUEUING)
                        .le(BookReserve::getId, myReserve.getId()));
                vo.setQueueNo(pos == null ? 1 : pos.intValue());
            }
            return vo;
        }

        vo.setUserStatus(0);
        return vo;
    }

    @Override
    public void add(Book book) {
        if (!StringUtils.hasText(book.getName())) {
            throw new BusinessException("书名不能为空");
        }
        if (book.getPrice() == null || book.getPrice().signum() < 0) {
            throw new BusinessException("售价不能为负");
        }
        if (book.getStock() == null || book.getStock() < 0) {
            book.setStock(0);
        }
        bookMapper.insert(book);
        // 建书时填的册数直接生成对应数量的副本。
        // 否则会出现「列表显示有货、借阅提示已全部借出」——因为 stock 是数字，副本才是可借的实体
        if (book.getStock() != null && book.getStock() > 0) {
            bookItemService.addItems(book.getId(), book.getStock(), null);
        }
    }

    @Override
    public void update(Book book) {
        if (book.getId() == null) {
            throw new BusinessException("图书id不能为空");
        }
        // stock 是由副本统计出来的派生值，不允许直接改。
        // 置 null 后 MyBatis-Plus 会跳过该字段，库存只能通过「副本管理」增减
        book.setStock(null);
        bookMapper.updateById(book);
    }

    @Override
    public void delete(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        // 存在未结清借阅（待取书 / 借阅中）时禁止删除，否则借阅单会指向一本不存在的书
        Long borrowing = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getBookId, id)
                .in(Borrow::getStatus, Constants.BORROW_STATUS_PENDING_PICKUP, Constants.BORROW_STATUS_BORROWING));
        if (borrowing != null && borrowing > 0) {
            throw new BusinessException("该图书仍有 " + borrowing + " 条未结清的借阅记录（待取书/借阅中），无法删除");
        }
        // 存在未完成的购买单时禁止删除。
        // 购买单连着具体副本（item_id），副本被级联删掉后订单就悬空了——
        // 读者已付钱、到馆扫码却只得到「副本不存在」，只能取消退款。
        Long pendingPurchase = purchaseOrderMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getBookId, id)
                .and(w -> w.eq(PurchaseOrder::getStatus, 0)
                        .or(x -> x.eq(PurchaseOrder::getStatus, 1)
                                .eq(PurchaseOrder::getPickupStatus, Constants.PICKUP_STATUS_PENDING))));
        if (pendingPurchase != null && pendingPurchase > 0) {
            throw new BusinessException("该图书仍有 " + pendingPurchase
                    + " 条未完成的购买订单（待支付/已付款待取书），无法删除");
        }
        // 存在排队中/待借阅预约时禁止删除，否则会留下无法兑现的悬挂预约
        Long reserving = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getBookId, id)
                .in(BookReserve::getStatus, Constants.RESERVE_STATUS_QUEUING, Constants.RESERVE_STATUS_NOTIFIED));
        if (reserving != null && reserving > 0) {
            throw new BusinessException("该图书仍有 " + reserving + " 条未处理的预约记录，无法删除");
        }
        // 级联逻辑删除仍属于馆藏的副本。
        // 只置为「下架」是不够的——管理端副本列表仍会查到这些孤儿记录，显示成「（图书已下架）」。
        // 已售出 / 已买断的副本保留：它们已经不属于馆藏，是交易凭证，删掉会丢失销售侧的可追溯性
        bookItemMapper.delete(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBookId, id)
                .in(BookItem::getStatus,
                        Constants.ITEM_STATUS_IN_LIBRARY,
                        Constants.ITEM_STATUS_RESERVED,
                        Constants.ITEM_STATUS_OFFLINE,
                        Constants.ITEM_STATUS_DAMAGED,
                        Constants.ITEM_STATUS_LOST));
        bookMapper.deleteById(id);
    }

    @Override
    public String uploadCover(MultipartFile file) {
        return fileUtil.saveCover(file);
    }
}
