package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miragemock.admin.mapper.MockRequestLogMapper;
import com.miragemock.common.api.PageResult;
import com.miragemock.common.entity.MockRequestLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final MockRequestLogMapper logMapper;

    @Autowired
    public LogService(MockRequestLogMapper logMapper) {
        this.logMapper = logMapper;
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
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
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
