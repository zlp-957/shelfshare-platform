package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.Book;
import com.library.entity.BookItem;
import com.library.mapper.BookItemMapper;
import com.library.mapper.BookMapper;
import com.library.service.BookItemService;
import com.library.service.ReserveService;
import com.library.vo.BookItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图书副本服务实现
 */
@Service
public class BookItemServiceImpl implements BookItemService {

    @Autowired
    private BookItemMapper bookItemMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private ReserveService reserveService;

    @Override
    @Transactional
    public void addItems(Long bookId, int count, String location) {
        if (bookId == null) {
            throw new BusinessException("请选择图书");
        }
        if (count < 1 || count > 100) {
            throw new BusinessException("单次新增册数需在 1～100 之间");
        }
        // 先锁书目行，再算条码序号。
        // 并发新增同一本书的副本时，多个请求会各自读到同一个「最大序号」，
        // 插入时必然撞 uk_barcode 唯一键，表现出来就是一大半请求报「系统繁忙」
        if (bookMapper.lockById(bookId) == null) {
            throw new BusinessException("图书不存在");
        }
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        String prefix = "B" + String.format("%03d", bookId);
        int maxSeq = currentMaxSeq(bookId, prefix);
        for (int i = 1; i <= count; i++) {
            BookItem item = new BookItem();
            item.setBookId(bookId);
            item.setBarcode(prefix + String.format("%03d", maxSeq + i));
            item.setStatus(Constants.ITEM_STATUS_IN_LIBRARY);
            item.setLocation(location);
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            bookItemMapper.insert(item);
        }
        bookItemMapper.refreshStock(bookId);
        // 补货后必须唤醒预约队列，否则售罄书排着队的人永远收不到通知
        reserveService.wakeUpQueue(bookId);
    }

    /**
     * 求当前最大册序号。
     * 条码规则：B + 书目id(3位) + 册序号(3位)，如 B001001。
     * 用最大序号而不是总数，避免中间有副本被删除后条码重复。
     */
    private int currentMaxSeq(Long bookId, String prefix) {
        List<BookItem> existing = bookItemMapper.selectList(
                new LambdaQueryWrapper<BookItem>().eq(BookItem::getBookId, bookId));
        int max = 0;
        for (BookItem item : existing) {
            String barcode = item.getBarcode();
            if (barcode != null && barcode.startsWith(prefix) && barcode.length() == prefix.length() + 3) {
                try {
                    max = Math.max(max, Integer.parseInt(barcode.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非标准条码忽略
                }
            }
        }
        return max;
    }

    @Override
    public List<BookItemVO> listByBook(Long bookId) {
        List<BookItem> items = bookItemMapper.selectList(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBookId, bookId)
                .orderByAsc(BookItem::getBarcode));
        return convert(items);
    }

    @Override
    public Page<BookItemVO> page(Integer page, Integer size, Long bookId, Integer status, String keyword) {
        int pageNo = PageUtil.normalizePage(page);
        int pageSize = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<BookItem> wrapper = new LambdaQueryWrapper<>();
        if (bookId != null) {
            wrapper.eq(BookItem::getBookId, bookId);
        }
        if (status != null) {
            wrapper.eq(BookItem::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 条码直接匹配，书名先查出书目id再过滤
            List<Long> bookIds = bookMapper.selectList(new LambdaQueryWrapper<Book>()
                            .select(Book::getId)
                            .like(Book::getName, kw))
                    .stream().map(Book::getId).collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(BookItem::getBarcode, kw);
                if (!bookIds.isEmpty()) {
                    w.or().in(BookItem::getBookId, bookIds);
                }
            });
        }
        wrapper.orderByAsc(BookItem::getBarcode);
        Page<BookItem> itemPage = bookItemMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Page<BookItemVO> voPage = new Page<>(pageNo, pageSize, itemPage.getTotal());
        voPage.setRecords(convert(itemPage.getRecords()));
        return voPage;
    }

    @Override
    @Transactional
    public void updateStatus(Long itemId, Integer status, String remark) {
        BookItem item = bookItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("副本不存在");
        }
        // 只允许管理员手工标记这几种终态/异常态；借出、售出、买断由业务流程驱动
        if (status != Constants.ITEM_STATUS_LOST
                && status != Constants.ITEM_STATUS_DAMAGED
                && status != Constants.ITEM_STATUS_OFFLINE
                && status != Constants.ITEM_STATUS_IN_LIBRARY) {
            throw new BusinessException("不支持手工设置该状态");
        }
        if (item.getStatus() == Constants.ITEM_STATUS_BORROWED
                || item.getStatus() == Constants.ITEM_STATUS_RESERVED) {
            throw new BusinessException("该副本正在借阅或已被预留，请先处理业务单据");
        }
        item.setStatus(status);
        if (StringUtils.hasText(remark)) {
            item.setRemark(remark.trim());
        }
        item.setUpdateTime(LocalDateTime.now());
        bookItemMapper.updateById(item);
        bookItemMapper.refreshStock(item.getBookId());
        // 从破损/下架恢复为在馆，等于凭空多出可借库存，同样要唤醒队列
        if (status == Constants.ITEM_STATUS_IN_LIBRARY) {
            reserveService.wakeUpQueue(item.getBookId());
        }
    }

    @Override
    public BookItemVO getByBarcode(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            return null;
        }
        BookItem item = bookItemMapper.selectOne(new LambdaQueryWrapper<BookItem>()
                .eq(BookItem::getBarcode, barcode.trim()));
        if (item == null) {
            return null;
        }
        List<BookItemVO> list = convert(java.util.Collections.singletonList(item));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<BookItemVO> convert(List<BookItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> bookIds = items.stream().map(BookItem::getBookId).distinct().collect(Collectors.toList());
        Map<Long, Book> bookMap = bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));

        return items.stream().map(item -> {
            BookItemVO vo = new BookItemVO();
            vo.setId(item.getId());
            vo.setBookId(item.getBookId());
            vo.setBarcode(item.getBarcode());
            vo.setStatus(item.getStatus());
            vo.setStatusText(statusText(item.getStatus()));
            vo.setLocation(item.getLocation());
            vo.setRemark(item.getRemark());
            vo.setCreateTime(item.getCreateTime());
            Book book = bookMap.get(item.getBookId());
            vo.setBookName(book == null ? "（图书已下架）" : book.getName());
            return vo;
        }).collect(Collectors.toList());
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case Constants.ITEM_STATUS_IN_LIBRARY:
                return "在馆";
            case Constants.ITEM_STATUS_BORROWED:
                return "借出中";
            case Constants.ITEM_STATUS_SOLD:
                return "已售出";
            case Constants.ITEM_STATUS_BOUGHT_OUT:
                return "已买断";
            case Constants.ITEM_STATUS_LOST:
                return "遗失";
            case Constants.ITEM_STATUS_DAMAGED:
                return "破损";
            case Constants.ITEM_STATUS_OFFLINE:
                return "下架";
            case Constants.ITEM_STATUS_RESERVED:
                return "已预留";
            default:
                return "未知";
        }
    }
}
