package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.library.entity.Borrow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BorrowMapper extends BaseMapper<Borrow> {

    /**
     * 在借押金总额：只统计「已付款」的待取书与借阅中。
     * <p>
     * 下单未支付的单据虽然占着副本，但钱还没到账，不能计入占用中的担保金，
     * 否则统计口径会把「未收到的钱」算进业务规模里。
     */
    @Select("SELECT COALESCE(SUM(b.deposit_amount), 0) "
            + "FROM borrow b JOIN pay_order p ON b.pay_order_no = p.order_no "
            + "WHERE b.status IN (0, 1) AND p.status = 1")
    BigDecimal sumDepositInUse();

    /**
     * 查出「支付成功超过 N 小时仍未到馆取书」的借阅单。
     * <p>
     * 时限的起点必须是 <b>pay_time</b>，不是 borrow_time。
     * 旧实现按 borrow_time（下单时刻）判断：用户下单后犹豫 14 分钟才付款，
     * 他的取书窗口就凭空少了 14 分钟，和规则里写的「支付后 2 小时」对不上。
     * <p>
     * status IN (1,4)：已支付、部分退款都算有效付款；
     * 未支付的单（pay_time 为 NULL）不在此列，它们由 15 分钟超时关单逻辑处理。
     */
    @Select("SELECT b.* FROM borrow b JOIN pay_order p ON b.pay_order_no = p.order_no "
            + "WHERE b.status = 0 AND p.status IN (1, 4) "
            + "AND p.pay_time IS NOT NULL AND p.pay_time < #{threshold}")
    List<Borrow> selectPickupTimeout(@Param("threshold") LocalDateTime threshold);

    /**
     * 管理端借阅列表：关键词直接 JOIN 过滤，不再「先查一批 id 再 IN」。
     * <p>
     * 旧写法要把所有匹配的 user_id、book_id 拉到内存再拼成 IN 列表。
     * 用户量上万后这个 IN 会有几千个元素，执行计划退化成全表扫描 + 排序，
     * 而且 ids 越多 SQL 文本越长，MySQL 解析开销也跟着涨。
     * 交给数据库做 JOIN，索引（uk_account / idx_name / idx_book）才能用上。
     */
    @Select("<script>"
            + "SELECT b.* FROM borrow b "
            + "LEFT JOIN `user` u ON b.user_id = u.id "
            + "LEFT JOIN book bk ON b.book_id = bk.id "
            + "WHERE 1 = 1 "
            + "<if test='status != null'> AND b.status = #{status}</if> "
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (u.account LIKE CONCAT('%', #{keyword}, '%') "
            + "   OR u.name LIKE CONCAT('%', #{keyword}, '%') "
            + "   OR bk.name LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if> "
            + "ORDER BY b.id DESC"
            + "</script>")
    IPage<Borrow> pageWithKeyword(IPage<Borrow> page,
                                  @Param("status") Integer status,
                                  @Param("keyword") String keyword);
}
