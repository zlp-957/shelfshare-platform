package com.library.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.library.entity.PasswordResetRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PasswordResetRequestMapper extends BaseMapper<PasswordResetRequest> {
}
