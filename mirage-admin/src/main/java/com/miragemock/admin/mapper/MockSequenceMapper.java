package com.miragemock.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miragemock.common.entity.MockSequence;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MockSequenceMapper extends BaseMapper<MockSequence> {
}
