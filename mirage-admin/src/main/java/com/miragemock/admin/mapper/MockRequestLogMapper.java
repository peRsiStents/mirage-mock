package com.miragemock.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miragemock.common.entity.MockRequestLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MockRequestLogMapper extends BaseMapper<MockRequestLog> {
}
