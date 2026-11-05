package com.library.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.common.BusinessException;
import com.library.common.Constants;
import com.library.common.PageUtil;
import com.library.entity.*;
import com.library.mapper.BookMapper;
import com.library.mapper.BookReserveMapper;
import com.library.mapper.UserMapper;
import com.library.service.AgreementService;
import com.library.service.MessageService;
import com.library.service.ReserveService;
import com.library.util.UserContext;
import com.library.vo.ReserveVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图书预约服务实现
 */
@Service
public class ReserveServiceImpl implements ReserveService {

    @Autowired
    private BookReserveMapper bookReserveMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AgreementService agreementService;

    @Autowired
    private MessageService messageService;

    /** 预约通知后的有效天数，统一取自配置，避免各处硬编码不一致 */
    @Value("${library.reserve.valid-days:5}")
    private int reserveValidDays;

    @Override
    @Transactional
    public void reserve(Long bookId) {
        if (bookId == null) {
            throw new BusinessException("请选择要预约的图书");
        }
        Long userId = UserContext.getUserId();
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        // 预约同样属于"占用名额"，与借阅/购买保持一致，必须先知晓规则
        agreementService.assertAgreed(userId, Constants.AGREEMENT_TYPE_BORROW);
        if (book.getStock() != null && book.getStock() > 0) {
            throw new BusinessException("图书有库存，可直接借阅，无需预约");
        }
        // 是否已有排队中/已通知的预约
        Long exists = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getUserId, userId)
                .eq(BookReserve::getBookId, bookId)
                .in(BookReserve::getStatus, Constants.RESERVE_STATUS_QUEUING, Constants.RESERVE_STATUS_NOTIFIED));
        if (exists != null && exists > 0) {
            throw new BusinessException("您已预约该图书，请勿重复预约");
        }
        BookReserve reserve = new BookReserve();
        reserve.setUserId(userId);
        reserve.setBookId(bookId);
        reserve.setReserveTime(LocalDateTime.now());
        reserve.setStatus(Constants.RESERVE_STATUS_QUEUING);
        reserve.setCreateTime(LocalDateTime.now());
        bookReserveMapper.insert(reserve);
    }

    @Override
    @Transactional
    public void cancel(Long reserveId) {
        Long userId = UserContext.getUserId();
        BookReserve reserve = bookReserveMapper.selectById(reserveId);
        if (reserve == null || !reserve.getUserId().equals(userId)) {
            throw new BusinessException("预约记录不存在");
        }
        if (reserve.getStatus() != Constants.RESERVE_STATUS_QUEUING
                && reserve.getStatus() != Constants.RESERVE_STATUS_NOTIFIED) {
            throw new BusinessException("当前状态不可取消");
        }
        boolean wasNotified = reserve.getStatus() == Constants.RESERVE_STATUS_NOTIFIED;
        reserve.setStatus(Constants.RESERVE_STATUS_CANCELED);
        bookReserveMapper.updateById(reserve);
        // 取消的是"待借阅"名额时，名额与库存空出来了，必须立刻唤醒下一位，否则队列停滞
        if (wasNotified) {
            wakeUpQueue(reserve.getBookId());
        }
    }

    @Override
    public void assertBorrowAllowed(Long userId, Long bookId, int stock) {
        if (userId == null || bookId == null) {
            return;
        }
        List<BookReserve> notifiedList = bookReserveMapper.selectList(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getBookId, bookId)
                .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_NOTIFIED)
                .gt(BookReserve::getValidDeadline, LocalDateTime.now()));
        int notifiedCount = notifiedList.size();
        if (notifiedCount <= 0) {
            return;
        }
        boolean iAmNotified = notifiedList.stream().anyMatch(r -> userId.equals(r.getUserId()));
        if (iAmNotified || notifiedCount < stock) {
            return;
        }
        // 已在队列中的读者被拦住时提示要准确，否则「请先提交预约排队」会让排队的人以为没排上
        Long myReserve = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getBookId, bookId)
                .eq(BookReserve::getUserId, userId)
                .in(BookReserve::getStatus,
                        Constants.RESERVE_STATUS_QUEUING, Constants.RESERVE_STATUS_NOTIFIED));
        if (myReserve != null && myReserve > 0) {
            throw new BusinessException("该书库存已被预约读者优先占用，您在排队中，请等待到书通知");
        }
        throw new BusinessException("该书已有读者预约优先，可提交预约排队");
    }

    @Override
    public void wakeUpQueue(Long bookId) {
        if (bookId == null) {
            return;
        }
        Book book = bookMapper.selectById(bookId);
        if (book == null || book.getStock() == null || book.getStock() <= 0) {
            return;
        }
        // 每条「待借阅」预约各占一册，剩下的才是还能再唤醒的名额。
        // 只唤醒队首一人是不够的：管理员一次补 N 册时，剩下 N-1 册会空着，
        // 而队列里的人仍被预约优先权挡住，等于书在架上有货却借不走
        Long notified = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getBookId, bookId)
                .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_NOTIFIED)
                .gt(BookReserve::getValidDeadline, LocalDateTime.now()));
        int slots = book.getStock() - (notified == null ? 0 : notified.intValue());
        if (slots <= 0) {
            return;
        }

        List<BookReserve> queuing = bookReserveMapper.selectList(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getBookId, bookId)
                .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_QUEUING)
                .orderByAsc(BookReserve::getReserveTime)
                .orderByAsc(BookReserve::getId));
        if (queuing.isEmpty()) {
            return;
        }

        // 名额与排队人数取小，按 FIFO 顺序逐个通知
        LocalDateTime deadline = LocalDateTime.now().plusDays(reserveValidDays);
        int wake = Math.min(slots, queuing.size());
        for (int i = 0; i < wake; i++) {
            BookReserve reserve = queuing.get(i);
            reserve.setStatus(Constants.RESERVE_STATUS_NOTIFIED);
            reserve.setValidDeadline(deadline);
            bookReserveMapper.updateById(reserve);

            // 改状态不等于通知到人。
            // 预约有效期只有 valid-days 天（默认 5），过期就顺延给下一位；
            // 如果只是在「我的预约」里把状态从「排队中」改成「待借阅」，
            // 用户不主动去翻就根本不知道自己排到了，5 天后书给了别人还一头雾水。
            // 幂等键用预约单 id：一次唤醒对应一条通知，重复调用不会刷屏
            messageService.send(reserve.getUserId(), MessageService.TYPE_RESERVE,
                    "预约的图书已到馆",
                    "您预约的《" + book.getName() + "》已到馆，请在 " + reserveValidDays
                            + " 天内到馆取书，逾期将顺延给下一位读者。",
                    reserve.getId(), "RESERVE:" + reserve.getId());
        }
    }

    @Override
    public List<ReserveVO> myReserves() {
        Long userId = UserContext.getUserId();
        List<BookReserve> reserves = bookReserveMapper.selectList(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getUserId, userId)
                .orderByDesc(BookReserve::getCreateTime));
        return convert(reserves, userId);
    }

    @Override
    public Page<ReserveVO> pageReserves(Integer page, Integer size, String keyword, Integer status) {
        int pageNo = PageUtil.normalizePage(page);
        int pageSize = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<BookReserve> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(BookReserve::getStatus, status);
        }
        // 关键词过滤下沉到 SQL，保证 total 与记录数一致
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
            if (matchedUserIds.isEmpty() && matchedBookIds.isEmpty()) {
                return new Page<>(pageNo, pageSize, 0);
            }
            wrapper.and(w -> {
                boolean empty = true;
                if (!matchedUserIds.isEmpty()) {
                    w.in(BookReserve::getUserId, matchedUserIds);
                    empty = false;
                }
                if (!matchedBookIds.isEmpty()) {
                    if (!empty) {
                        w.or();
                    }
                    w.in(BookReserve::getBookId, matchedBookIds);
                }
            });
        }
        wrapper.orderByDesc(BookReserve::getCreateTime);
        Page<BookReserve> reservePage = bookReserveMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        Page<ReserveVO> voPage = new Page<>(pageNo, pageSize, reservePage.getTotal());
        voPage.setRecords(convert(reservePage.getRecords(), null));
        return voPage;
    }

    private List<ReserveVO> convert(List<BookReserve> reserves, Long currentUserId) {
        if (reserves.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = reserves.stream().map(BookReserve::getUserId).distinct().collect(Collectors.toList());
        List<Long> bookIds = reserves.stream().map(BookReserve::getBookId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Book> bookMap = bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));

        return reserves.stream().map(r -> {
            ReserveVO vo = new ReserveVO();
            vo.setId(r.getId());
            vo.setUserId(r.getUserId());
            vo.setBookId(r.getBookId());
            vo.setReserveTime(r.getReserveTime());
            vo.setStatus(r.getStatus());
            vo.setValidDeadline(r.getValidDeadline());
            User u = userMap.get(r.getUserId());
            if (u != null) {
                vo.setUserAccount(u.getAccount());
                vo.setUserName(u.getName());
            }
            Book book = bookMap.get(r.getBookId());
            if (book != null) {
                vo.setBookName(book.getName());
                vo.setBookCover(book.getCover());
            } else {
                vo.setBookName("（图书已下架）");
            }
            // 计算排队位次：预约时间相同则按 id 先后，保证同秒预约位次稳定不并列
            if (r.getStatus() == Constants.RESERVE_STATUS_QUEUING) {
                Long queueNo = bookReserveMapper.selectCount(new LambdaQueryWrapper<BookReserve>()
                        .eq(BookReserve::getBookId, r.getBookId())
                        .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_QUEUING)
                        .and(w -> w.lt(BookReserve::getReserveTime, r.getReserveTime())
                                .or(x -> x.eq(BookReserve::getReserveTime, r.getReserveTime())
                                        .le(BookReserve::getId, r.getId()))));
                vo.setQueueNo(queueNo == null ? 1 : Math.max(1, queueNo.intValue()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void processExpired() {
        LocalDateTime now = LocalDateTime.now();
        // 查询待借阅状态但已过有效期的预约
        List<BookReserve> expired = bookReserveMapper.selectList(new LambdaQueryWrapper<BookReserve>()
                .eq(BookReserve::getStatus, Constants.RESERVE_STATUS_NOTIFIED)
                .isNotNull(BookReserve::getValidDeadline)
                .lt(BookReserve::getValidDeadline, now));
        if (expired.isEmpty()) {
            return;
        }
        // 按书去重：同一本书可能有多条过期的「待借阅」，
        // 逐条唤醒会把队列里好几个人同时置为「待借阅」，名额就超发了。
        // wakeUpQueue 内部按「库存 - 已通知数」算名额，天然不会超发
        Set<Long> touchedBookIds = new LinkedHashSet<>();
        for (BookReserve reserve : expired) {
            reserve.setStatus(Constants.RESERVE_STATUS_EXPIRED);
            bookReserveMapper.updateById(reserve);
            touchedBookIds.add(reserve.getBookId());
        }
        for (Long bookId : touchedBookIds) {
            wakeUpQueue(bookId);
        }
    }
}
