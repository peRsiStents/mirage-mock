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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规则快照加载器实现：从 DB 编译接口与规则为运行时快照。
 */
@Component
public class RuleSnapshotLoaderImpl implements RuleSnapshotLoader {

    private static final Logger log = LoggerFactory.getLogger(RuleSnapshotLoaderImpl.class);

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
        // 项目未启用（停用）时不提供服务：与 loadAll 的口径保持一致，
        // 否则停用后 invalidate 反而会把快照重新载入，Mock 继续对外吐数据。
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            return null;
        }
        return loadSnapshot(p);
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
        // 路由顺序：按路径模式具体性排序，避免通配路径遮蔽精确路径
        // （先建 /api/** 再建 /api/order 时，后者永远命中不到）
        interfaces.sort(Comparator.comparingInt((ApiInterface i) -> specificity(i.getHttpPath()))
                .thenComparing(ApiInterface::getId));
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
                try {
                    compiledRules.add(compileRule(rule));
                } catch (IllegalArgumentException e) {
                    // 规则编译失败（匹配条件非法 / 正则非法）：跳过该条规则，
                    // 不能退化成「无条件匹配」的兜底规则，否则会吞掉接口全部流量
                    log.error("规则编译失败，已跳过（id={}, name={}, interfaceId={}）：{}",
                            rule.getId(), rule.getName(), rule.getInterfaceId(), e.getMessage());
                }
            }
            compiled.add(new CompiledInterface(iface, compiledRules));
        }
        return new ProjectSnapshot(p.getId(), p.getCode(), compiled);
    }

    /**
     * 路径模式具体性评分：值越小越具体（越优先匹配）。
     * 无通配符=0，单层通配（{var} / *）=1，** 结尾=2。
     */
    private int specificity(String path) {
        if (path == null || path.isEmpty()) {
            return 2;
        }
        if (path.endsWith("/**")) {
            return 2;
        }
        if (path.indexOf('{') >= 0 || path.indexOf('*') >= 0) {
            return 1;
        }
        return 0;
    }

    private CompiledRule compileRule(MockRule rule) {
        List<MatchCondition> conditions = parseConditions(rule.getMatchCondition());
        JsonNode template = parseTemplate(rule.getResponseTemplate());
        Map<Integer, Pattern> regexPatterns = precompileRegex(conditions);
        return new CompiledRule(rule, conditions, template, regexPatterns);
    }

    /**
     * 预编译 regex 条件的正则：加载期编译，失败则整条规则编译失败（跳过），
     * 避免运行期每请求重新编译、也避免灾难回溯正则拖垮匹配线程。
     */
    private Map<Integer, Pattern> precompileRegex(List<MatchCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Pattern> map = new HashMap<>();
        for (int i = 0; i < conditions.size(); i++) {
            MatchCondition c = conditions.get(i);
            if (c == null || !"regex".equalsIgnoreCase(c.getOp())) {
                continue;
            }
            String expected = c.getValue() == null ? null : String.valueOf(c.getValue());
            if (expected == null || expected.isEmpty()) {
                continue;
            }
            try {
                map.put(i, Pattern.compile(expected));
            } catch (Exception e) {
                throw new IllegalArgumentException("regex 条件非法: " + expected, e);
            }
        }
        return map;
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
            // 匹配条件解析失败属于配置错误：抛出让调用方跳过该规则，
            // 而不是返回空列表（空列表 = 无条件匹配 = 恒真兜底）
            throw new IllegalArgumentException("match_condition 解析失败: " + e.getMessage(), e);
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
