package com.miragemock.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则内存缓存：projectId → ProjectSnapshot。热刷新通过 {@link #invalidate(long)} 触发重载。
 *
 * <p>不在 @PostConstruct 预加载（彼时 Flyway 尚未建表）；启动期由 DataInitializer 在建表后
 * 调用 {@link #reloadAll()} 预热，首次访问亦通过 ensureInit 懒加载兜底。
 */
@Component
public class RuleCache {

    private static final Logger log = LoggerFactory.getLogger(RuleCache.class);

    private final RuleSnapshotLoader loader;

    private final Map<Long, ProjectSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Long> codeIndex = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    @Autowired
    public RuleCache(RuleSnapshotLoader loader) {
        this.loader = loader;
    }

    public synchronized void reloadAll() {
        try {
            Map<Long, ProjectSnapshot> all = loader.loadAll();
            snapshots.clear();
            if (all != null) {
                snapshots.putAll(all);
            }
            rebuildCodeIndex();
            initialized = true;
            log.info("规则缓存已加载，项目数: {}", snapshots.size());
        } catch (Exception e) {
            log.error("规则缓存加载失败", e);
        }
    }

    public synchronized void invalidate(long projectId) {
        try {
            ProjectSnapshot ps = loader.loadProject(projectId);
            if (ps != null) {
                snapshots.put(projectId, ps);
            } else {
                snapshots.remove(projectId);
            }
            rebuildCodeIndex();
        } catch (Exception e) {
            log.error("规则缓存刷新失败, projectId={}", projectId, e);
        }
    }

    private void rebuildCodeIndex() {
        codeIndex.clear();
        for (ProjectSnapshot ps : snapshots.values()) {
            if (ps.getProjectCode() != null) {
                codeIndex.put(ps.getProjectCode(), ps.getProjectId());
            }
        }
    }

    private void ensureInit() {
        if (!initialized) {
            reloadAll();
        }
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
