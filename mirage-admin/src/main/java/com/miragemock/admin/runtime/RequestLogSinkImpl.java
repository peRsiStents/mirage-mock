package com.miragemock.admin.runtime;

import com.miragemock.admin.mapper.MockRequestLogMapper;
import com.miragemock.common.entity.MockRequestLog;
import com.miragemock.core.log.RequestLogEntry;
import com.miragemock.core.log.RequestLogSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 请求日志异步落库。
 */
@Component
public class RequestLogSinkImpl implements RequestLogSink {

    private static final Logger log = LoggerFactory.getLogger(RequestLogSinkImpl.class);

    private final MockRequestLogMapper logMapper;
    private final ExecutorService executor;

    @Autowired
    public RequestLogSinkImpl(MockRequestLogMapper logMapper) {
        this.logMapper = logMapper;
        this.executor = new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @Override
    public void append(RequestLogEntry entry) {
        executor.execute(() -> {
            try {
                MockRequestLog entity = new MockRequestLog();
                entity.setProjectId(entry.getProjectId());
                entity.setInterfaceId(entry.getInterfaceId());
                entity.setRuleId(entry.getRuleId());
                entity.setProtocol(entry.getProtocol());
                entity.setClientAddr(entry.getClientAddr());
                entity.setRequestRaw(entry.getRequestRaw());
                entity.setRequestParsed(entry.getRequestParsed());
                entity.setResponseRaw(entry.getResponseRaw());
                entity.setMatched(entry.isMatched() ? 1 : 0);
                entity.setCostMs(entry.getCostMs());
                logMapper.insert(entity);
            } catch (Exception e) {
                log.debug("请求日志写入失败: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
