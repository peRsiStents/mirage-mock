package com.miragemock.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miragemock.common.entity.TestScenario;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestScenarioMapper extends BaseMapper<TestScenario> {
}
