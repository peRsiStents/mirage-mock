package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.RuleRequest;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.exception.BizException;
import com.miragemock.common.util.JsonUtils;
import com.miragemock.core.cache.RuleCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RuleService {

    private final MockRuleMapper ruleMapper;
    private final InterfaceService interfaceService;
    private final RuleCache ruleCache;

    @Autowired
    public RuleService(MockRuleMapper ruleMapper, InterfaceService interfaceService, RuleCache ruleCache) {
        this.ruleMapper = ruleMapper;
        this.interfaceService = interfaceService;
        this.ruleCache = ruleCache;
    }

    public List<MockRule> list(Long interfaceId) {
        // /interfaces/{iid}/rules 不在 pid 拦截器覆盖范围，这里通过接口归属校验成员权限
        interfaceService.get(interfaceId);
        return ruleMapper.selectList(new LambdaQueryWrapper<MockRule>()
                .eq(MockRule::getInterfaceId, interfaceId)
                .orderByAsc(MockRule::getPriority)
                .orderByAsc(MockRule::getId));
    }

    public MockRule get(Long id) {
        MockRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ResultCode.RULE_NOT_FOUND);
        }
        // 通过所属接口的项目校验成员权限（admin 直通）
        interfaceService.get(rule.getInterfaceId());
        return rule;
    }

    @Transactional
    public MockRule create(Long interfaceId, RuleRequest req) {
        ApiInterface iface = interfaceService.get(interfaceId);
        MockRule rule = toEntity(req, new MockRule());
        rule.setInterfaceId(interfaceId);
        ruleMapper.insert(rule);
        ruleCache.invalidate(iface.getProjectId());
        return rule;
    }

    @Transactional
    public MockRule update(Long id, RuleRequest req) {
        MockRule exists = get(id);
        ApiInterface iface = interfaceService.get(exists.getInterfaceId());
        toEntity(req, exists);
        ruleMapper.updateById(exists);
        ruleCache.invalidate(iface.getProjectId());
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        MockRule exists = get(id);
        ApiInterface iface = interfaceService.get(exists.getInterfaceId());
        ruleMapper.deleteById(id);
        ruleCache.invalidate(iface.getProjectId());
    }

    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<MockRule> rules = ruleMapper.selectBatchIds(ids);
        ruleMapper.deleteBatchIds(ids);
        Set<Long> projectIds = new HashSet<>();
        for (MockRule r : rules) {
            ApiInterface iface = interfaceService.get(r.getInterfaceId());
            if (iface != null) {
                projectIds.add(iface.getProjectId());
            }
        }
        for (Long pid : projectIds) {
            ruleCache.invalidate(pid);
        }
    }

    /** 仅改优先级（列表上移/下移用），避免整体 toEntity 覆盖其它字段。 */
    @Transactional
    public void setPriority(Long id, Integer priority) {
        MockRule exists = get(id);
        exists.setPriority(priority);
        ruleMapper.updateById(exists);
        ApiInterface iface = interfaceService.get(exists.getInterfaceId());
        if (iface != null) {
            ruleCache.invalidate(iface.getProjectId());
        }
    }

    /** 批量启用/停用。 */
    @Transactional
    public void setBatchStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<MockRule> rules = ruleMapper.selectBatchIds(ids);
        Set<Long> projectIds = new HashSet<>();
        for (MockRule r : rules) {
            r.setStatus(status);
            ruleMapper.updateById(r);
            ApiInterface iface = interfaceService.get(r.getInterfaceId());
            if (iface != null) {
                projectIds.add(iface.getProjectId());
            }
        }
        for (Long pid : projectIds) {
            ruleCache.invalidate(pid);
        }
    }

    @Transactional
    public MockRule toggle(Long id) {
        MockRule exists = get(id);
        ApiInterface iface = interfaceService.get(exists.getInterfaceId());
        int next = (exists.getStatus() != null && exists.getStatus() == Constants.STATUS_ENABLED)
                ? Constants.STATUS_DISABLED : Constants.STATUS_ENABLED;
        exists.setStatus(next);
        ruleMapper.updateById(exists);
        ruleCache.invalidate(iface.getProjectId());
        return exists;
    }

    private MockRule toEntity(RuleRequest req, MockRule rule) {
        if (req.getName() != null) {
            rule.setName(req.getName());
        }
        rule.setPriority(req.getPriority() == null ? Constants.DEFAULT_PRIORITY : req.getPriority());
        rule.setMatchCondition(toJsonText(req.getMatchCondition(), "[]"));
        rule.setResponseTemplate(toJsonText(req.getResponseTemplate(), "{}"));
        rule.setDelayType(req.getDelayType() == null ? "NONE" : req.getDelayType());
        rule.setDelayMs(req.getDelayMs());
        rule.setDelayMinMs(req.getDelayMinMs());
        rule.setDelayMaxMs(req.getDelayMaxMs());
        rule.setFaultType(req.getFaultType() == null ? "NONE" : req.getFaultType());
        rule.setFaultConfig(toJsonText(req.getFaultConfig(), null));
        if (req.getStatus() != null) {
            rule.setStatus(req.getStatus());
        } else if (rule.getStatus() == null) {
            rule.setStatus(Constants.STATUS_ENABLED);
        }
        return rule;
    }

    private String toJsonText(Object obj, String defaultVal) {
        if (obj == null) {
            return defaultVal;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        return JsonUtils.toJson(obj);
    }
}
