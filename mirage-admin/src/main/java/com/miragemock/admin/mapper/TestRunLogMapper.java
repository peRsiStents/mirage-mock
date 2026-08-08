package com.miragemock.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miragemock.common.entity.TestRunLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TestRunLogMapper extends BaseMapper<TestRunLog> {

    /** 按天聚合：运行数 / 通过数（通过率趋势用）。d=yyyy-mm-dd */
    @Select("select DATE(create_time) d, count(*) cnt, sum(case when passed=1 then 1 else 0 end) ok "
            + "from test_run_log where project_id=#{pid} and create_time>=#{since} "
            + "group by DATE(create_time) order by d")
    List<Map<String, Object>> dailyStats(@Param("pid") Long pid, @Param("since") LocalDateTime since);

    /** 失败最多的用例 Top5（近 N 天）：case_id + 失败次数 */
    @Select("select case_id cid, count(*) cnt from test_run_log "
            + "where project_id=#{pid} and passed=0 and create_time>=#{since} "
            + "group by case_id order by cnt desc limit 5")
    List<Map<String, Object>> topFailCases(@Param("pid") Long pid, @Param("since") LocalDateTime since);
}
