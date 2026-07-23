package com.miragemock.admin.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.ProjectMapper;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.Project;
import com.miragemock.common.model.MatchCondition;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.cache.CompiledInterface;
import com.miragemock.core.cache.CompiledRule;
import com.miragemock.core.cache.ProjectSnapshot;
import com.miragemock.core.cache.RuleSnapshotLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则快照加载器实现：从 DB 编译接口与规则为运行时快照。
 */
@Component
public class RuleSnapshotLoaderImpl implements RuleSnapshotLoader {

    private final ProjectMapper projectMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;

    @Autowired
    public RuleSnapshotLoaderImpl(ProjectMapper projectMapper, ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper) {
        this.projectMapper = projectMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
    }

    @Override
    public Map<Long, ProjectSnapshot> loadAll() {
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getStatus, 1));
        Map<Long, ProjectSnapshot> result = new HashMap<>();
        for (Project p : projects) {
            result.put(p.getId(), loadSnapshot(p));
        }
        return result;
    }

    @Override
    public ProjectSnapshot loadProject(long projectId) {
        Project p = projectMapper.selectById(projectId);
        return p == null ? null : loadSnapshot(p);
    }

    @Override
    public Long resolveProjectIdByCode(String code) {
        Project p = projectMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getCode, code));
        return p == null ? null : p.getId();
    }

    @Override
    public List<Long> allProjectIds() {
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<>());
        return projects.stream().map(Project::getId).collect(Collectors.toList());
    }

    private ProjectSnapshot loadSnapshot(Project p) {
        List<ApiInterface> interfaces = interfaceMapper.selectList(
                new LambdaQueryWrapper<ApiInterface>()
                        .eq(ApiInterface::getProjectId, p.getId())
                        .eq(ApiInterface::getStatus, 1)
                        .orderByAsc(ApiInterface::getId));
        List<CompiledInterface> compiled = new ArrayList<>();
        for (ApiInterface iface : interfaces) {
            List<MockRule> rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<MockRule>()
                            .eq(MockRule::getInterfaceId, iface.getId())
                            .eq(MockRule::getStatus, 1)
                            .orderByAsc(MockRule::getPriority)
                            .orderByAsc(MockRule::getId));
            List<CompiledRule> compiledRules = new ArrayList<>();
            for (MockRule rule : rules) {
                compiledRules.add(compileRule(rule));
            }
            compiled.add(new CompiledInterface(iface, compiledRules));
        }
        return new ProjectSnapshot(p.getId(), p.getCode(), compiled);
    }

    private CompiledRule compileRule(MockRule rule) {
        List<MatchCondition> conditions = parseConditions(rule.getMatchCondition());
        JsonNode template = parseTemplate(rule.getResponseTemplate());
        return new CompiledRule(rule, conditions, template);
    }

    private List<MatchCondition> parseConditions(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        try {
            List<MatchCondition> list = JsonUtils.fromJson(json, new TypeReference<List<MatchCondition>>() {
            });
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private JsonNode parseTemplate(String json) {
        if (json == null || json.trim().isEmpty()) {
            return JsonUtils.mapper().nullNode();
        }
        try {
            return JsonUtils.readTree(json);
        } catch (Exception e) {
            return JsonUtils.mapper().nullNode();
        }
    }
}
