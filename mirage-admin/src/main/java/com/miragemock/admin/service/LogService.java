package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRequestLogMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.ProjectMapper;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRequestLog;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final MockRequestLogMapper logMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final ProjectMapper projectMapper;

    @Autowired
    public LogService(MockRequestLogMapper logMapper, ApiInterfaceMapper interfaceMapper,
                      MockRuleMapper ruleMapper, ProjectMapper projectMapper) {
        this.logMapper = logMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.projectMapper = projectMapper;
    }

    public PageResult<MockRequestLog> query(Long projectId, Long interfaceId, Integer matched,
                                            Long from, Long to, long page, long size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 500) {
            size = 20;
        }
        LambdaQueryWrapper<MockRequestLog> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(MockRequestLog::getProjectId, projectId);
        }
        if (interfaceId != null) {
            wrapper.eq(MockRequestLog::getInterfaceId, interfaceId);
        }
        if (matched != null) {
            wrapper.eq(MockRequestLog::getMatched, matched);
        }
        if (from != null) {
            wrapper.ge(MockRequestLog::getCreateTime, toLocalDateTime(from));
        }
        if (to != null) {
            wrapper.le(MockRequestLog::getCreateTime, toLocalDateTime(to));
        }
        wrapper.orderByDesc(MockRequestLog::getCreateTime);

        Page<MockRequestLog> result = logMapper.selectPage(new Page<>(page, size), wrapper);
        enrichNames(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 回填项目/接口/规则名称，前端展示名称而非 id */
    private void enrichNames(List<MockRequestLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> ifaceIds = new HashSet<>();
        Set<Long> ruleIds = new HashSet<>();
        Set<Long> projIds = new HashSet<>();
        for (MockRequestLog l : records) {
            if (l.getInterfaceId() != null) ifaceIds.add(l.getInterfaceId());
            if (l.getRuleId() != null) ruleIds.add(l.getRuleId());
            if (l.getProjectId() != null) projIds.add(l.getProjectId());
        }
        Map<Long, String> ifaceNames = ifaceIds.isEmpty() ? new HashMap<>()
                : toNameMap(interfaceMapper.selectBatchIds(ifaceIds), ApiInterface::getId, ApiInterface::getName);
        Map<Long, String> ruleNames = ruleIds.isEmpty() ? new HashMap<>()
                : toNameMap(ruleMapper.selectBatchIds(ruleIds), MockRule::getId, MockRule::getName);
        Map<Long, String> projNames = projIds.isEmpty() ? new HashMap<>()
                : toNameMap(projectMapper.selectBatchIds(projIds), Project::getId, Project::getName);
        for (MockRequestLog l : records) {
            l.setInterfaceName(ifaceNames.get(l.getInterfaceId()));
            l.setRuleName(ruleNames.get(l.getRuleId()));
            l.setProjectName(projNames.get(l.getProjectId()));
        }
    }

    private <T> Map<Long, String> toNameMap(List<T> list, java.util.function.Function<T, Long> idFn,
                                            java.util.function.Function<T, String> nameFn) {
        Map<Long, String> m = new HashMap<>();
        if (list == null) {
            return m;
        }
        for (T t : list) {
            Long id = idFn.apply(t);
            if (id != null) {
                m.put(id, nameFn.apply(t));
            }
        }
        return m;
    }

    /** 每天凌晨 3 点清理 7 天前的日志 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = logMapper.delete(new LambdaQueryWrapper<MockRequestLog>()
                .lt(MockRequestLog::getCreateTime, threshold));
        log.info("清理 7 天前请求日志，删除 {} 条", deleted);
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        return new Date(epochMillis).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // 保留 list 备用
    public List<MockRequestLog> list(Long projectId) {
        return logMapper.selectList(new LambdaQueryWrapper<MockRequestLog>()
                .eq(MockRequestLog::getProjectId, projectId)
                .orderByDesc(MockRequestLog::getCreateTime)
                .last("limit 100"));
    }
}
