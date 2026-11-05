package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 对用户行加排他锁（须在事务内调用）。
     * 用于把"重复借阅 / 借阅上限 / 重复购买"这类"先 count 再写"的校验串行化，
     * 避免同一用户并发请求全部通过校验。
     *
     * 注意：必须是事务中的第一条 SQL，后续普通查询才能读到其他事务已提交的数据。
     */
    @Select("SELECT id FROM user WHERE id = #{userId} AND is_delete = 0 FOR UPDATE")
    Long lockById(@Param("userId") Long userId);
}
