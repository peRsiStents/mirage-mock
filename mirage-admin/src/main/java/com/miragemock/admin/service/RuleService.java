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

import java.util.List;

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
