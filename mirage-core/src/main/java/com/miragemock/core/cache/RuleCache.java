package com.miragemock.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 规则内存缓存：projectId → ProjectSnapshot。热刷新通过 {@link #invalidate(long)} 触发重载。
 *
 * <p>快照与编码索引均为 volatile 引用 + copy-on-write 整体替换（重建到新 Map 后一次赋值），
 * 读侧无锁且不存在 clear+put 空窗。
 *
 * <p>不在 @PostConstruct 预加载（彼时 Flyway 尚未建表）；启动期由 DataInitializer 在建表后
 * 调用 {@link #reloadAll()} 预热，首次访问亦通过 ensureInit 懒加载兜底（失败退避 5 秒）。
 */
@Component
public class RuleCache {

    private static final Logger log = LoggerFactory.getLogger(RuleCache.class);

    /** 初始化/全量重载失败后的重试退避窗口：窗口内不重试，避免 DB 故障时每请求串行全量重载 */
    private static final long RETRY_BACKOFF_MS = 5_000L;

    private final RuleSnapshotLoader loader;

    /** 项目快照（volatile 引用，整体替换，发布后不再修改） */
    private volatile Map<Long, ProjectSnapshot> snapshots = Collections.emptyMap();
    /** 项目编码 → 项目 id 索引（与 snapshots 同批原子替换） */
    private volatile Map<String, Long> codeIndex = Collections.emptyMap();
    private volatile boolean initialized = false;
    /** 最近一次加载失败时间戳（毫秒）；0 表示无失败记录 */
    private volatile long lastFailureAt = 0L;

    @Autowired
    public RuleCache(RuleSnapshotLoader loader) {
        this.loader = loader;
    }

    public synchronized void reloadAll() {
        try {
            Map<Long, ProjectSnapshot> all = loader.loadAll();
            Map<Long, ProjectSnapshot> next = all == null ? new HashMap<>() : new HashMap<>(all);
            swap(next);
            log.info("规则缓存已加载，项目数: {}", snapshots.size());
        } catch (Exception e) {
            // 保留旧快照（copy-on-write 未替换），记录失败时间进入退避窗口
            lastFailureAt = System.currentTimeMillis();
            log.error("规则缓存加载失败", e);
        }
    }

    public synchronized void invalidate(long projectId) {
        try {
            ProjectSnapshot ps = loader.loadProject(projectId);
            // 新 Map 副本 + put/remove + 原子赋值，消除 clear+put 空窗
            Map<Long, ProjectSnapshot> next = new HashMap<>(snapshots);
            if (ps != null) {
                next.put(projectId, ps);
            } else {
                next.remove(projectId);
            }
            swap(next);
        } catch (Exception e) {
            log.error("规则缓存刷新失败, projectId={}", projectId, e);
        }
    }

    /** 重建编码索引后与新快照一起整体原子替换 */
    private void swap(Map<Long, ProjectSnapshot> next) {
        Map<String, Long> index = new HashMap<>();
        for (ProjectSnapshot ps : next.values()) {
            if (ps.getProjectCode() != null) {
                index.put(ps.getProjectCode(), ps.getProjectId());
            }
        }
        this.codeIndex = Collections.unmodifiableMap(index);
        this.snapshots = Collections.unmodifiableMap(next);
        this.initialized = true;
    }

    private void ensureInit() {
        if (initialized) {
            return;
        }
        // 初始化失败退避：窗口内不重试，直接复用现状（空/旧快照）
        if (System.currentTimeMillis() - lastFailureAt < RETRY_BACKOFF_MS) {
            return;
        }
        reloadAll();
    }

    public ProjectSnapshot getProject(Long projectId) {
        ensureInit();
        return projectId == null ? null : snapshots.get(projectId);
    }

    public ProjectSnapshot getByCode(String code) {
        ensureInit();
        if (code == null) {
            return null;
        }
        Long id = codeIndex.get(code);
        return id == null ? null : snapshots.get(id);
    }

    public Collection<ProjectSnapshot> all() {
        ensureInit();
        return snapshots.values();
    }

    /** 仅一个项目时返回它，用于无显式项目标识时的默认路由 */
    public ProjectSnapshot singleProjectOrDefault() {
        ensureInit();
        if (snapshots.size() == 1) {
            return snapshots.values().iterator().next();
        }
        return null;
    }
}
