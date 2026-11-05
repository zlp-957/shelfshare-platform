package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.library.common.Constants;
import com.library.entity.BookItem;
import com.library.entity.BookReserve;
import com.library.entity.Borrow;
import com.library.entity.User;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BookMapper;
import com.library.mapper.BookReserveMapper;
import com.library.mapper.BorrowMapper;
import com.library.mapper.PurchaseOrderMapper;
import com.library.mapper.UserMapper;
import com.library.service.StatService;
import com.library.vo.StatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统计服务实现
 */
@Service
public class StatServiceImpl implements StatService {

    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private BookItemMapper bookItemMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private BorrowMapper borrowMapper;
    @Autowired
    private BookReserveMapper bookReserveMapper;
    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Override
    public StatVO stat() {
        StatVO vo = new StatVO();
        vo.setBookCount(bookMapper.selectCount(null));
        vo.setItemCount(bookItemMapper.selectCount(null));
        vo.setAvailableItemCount(bookItemMapper.selectCount(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getStatus, Constants.ITEM_STATUS_IN_LIBRARY)));
        vo.setReaderCount(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, Constants.ROLE_READER)));
        vo.setBorrowingCount(borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BORROWING)));
        vo.setPendingPickupCount(borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_PENDING_PICKUP)));
        vo.setBoughtOutCount(borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, Constants.BORROW_STATUS_BOUGHT_OUT)));
        vo.setQueuingReserveCount(bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_QUEUING)));

        vo.setPurchaseCount(purchaseOrderMapper.countPaid());
        vo.setTotalPurchaseAmount(purchaseOrderMapper.sumPaidAmount());

        // 在借押金总额：只算已付款的待取书与借阅中，未支付的不算
        vo.setDepositInUse(borrowMapper.sumDepositInUse());
        return vo;
    }
}
