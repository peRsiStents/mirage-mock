package com.miragemock.core.cache;

import java.util.List;
import java.util.Map;

/**
 * 规则快照加载器：从持久层编译接口/规则为运行时快照。由 mirage-admin 实现。
 */
public interface RuleSnapshotLoader {

    /** 加载全部启用项目的快照 */
    Map<Long, ProjectSnapshot> loadAll();

    /** 加载单个项目快照（含未启用接口的项目也返回，接口本身按状态过滤） */
    ProjectSnapshot loadProject(long projectId);

    /** 按项目编码解析项目 id */
    Long resolveProjectIdByCode(String code);

    /** 全部项目 id（用于默认项目选择等） */
    List<Long> allProjectIds();
}
