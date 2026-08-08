package com.miragemock.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miragemock.common.entity.MockRequestLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MockRequestLogMapper extends BaseMapper<MockRequestLog> {

    /** 按天聚合：请求量 / 命中数 / 平均耗时（工作台趋势用）。d=yyyy-mm-dd */
    @Select("select DATE(create_time) d, count(*) cnt, sum(case when matched=1 then 1 else 0 end) hit, "
            + "coalesce(avg(cost_ms),0) avgcost from mock_request_log "
            + "where project_id=#{pid} and create_time>=#{since} group by DATE(create_time) order by d")
    List<Map<String, Object>> dailyStats(@Param("pid") Long pid, @Param("since") LocalDateTime since);
}
