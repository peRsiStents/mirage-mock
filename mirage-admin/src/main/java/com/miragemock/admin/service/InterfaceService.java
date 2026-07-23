package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.exception.BizException;
import com.miragemock.core.cache.RuleCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InterfaceService {

    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final RuleCache ruleCache;

    @Autowired
    public InterfaceService(ApiInterfaceMapper interfaceMapper, MockRuleMapper ruleMapper, RuleCache ruleCache) {
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.ruleCache = ruleCache;
    }

    public List<ApiInterface> list(Long projectId) {
        return interfaceMapper.selectList(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .orderByAsc(ApiInterface::getId));
    }

    public ApiInterface get(Long id) {
        ApiInterface iface = interfaceMapper.selectById(id);
        if (iface == null) {
            throw new BizException(ResultCode.INTERFACE_NOT_FOUND);
        }
        return iface;
    }

    @Transactional
    public ApiInterface create(ApiInterface iface) {
        if (iface.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (iface.getStatus() == null) {
            iface.setStatus(Constants.STATUS_ENABLED);
        }
        if (iface.getProtocol() == null) {
            iface.setProtocol("HTTP");
        }
        ensureUniquePath(iface.getProjectId(), iface.getHttpMethod(), iface.getHttpPath(), null);
        interfaceMapper.insert(iface);
        ruleCache.invalidate(iface.getProjectId());
        return iface;
    }

    @Transactional
    public ApiInterface update(Long id, ApiInterface patch) {
        ApiInterface exists = get(id);
        if (patch.getName() != null) {
            exists.setName(patch.getName());
        }
        if (patch.getProtocol() != null) {
            exists.setProtocol(patch.getProtocol());
        }
        if (patch.getHttpMethod() != null) {
            exists.setHttpMethod(patch.getHttpMethod());
        }
        if (patch.getHttpPath() != null) {
            exists.setHttpPath(patch.getHttpPath());
        }
        if (patch.getStatus() != null) {
            exists.setStatus(patch.getStatus());
        }
        if (patch.getRemark() != null) {
            exists.setRemark(patch.getRemark());
        }
        ensureUniquePath(exists.getProjectId(), exists.getHttpMethod(), exists.getHttpPath(), id);
        interfaceMapper.updateById(exists);
        ruleCache.invalidate(exists.getProjectId());
        return exists;
    }

    /** 同项目内 (httpMethod, httpPath) 必须唯一，否则路由歧义。仅约束 HTTP 接口。 */
    private void ensureUniquePath(Long projectId, String method, String path, Long excludeId) {
        if (method == null || method.isEmpty() || path == null || path.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<ApiInterface> q = new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .eq(ApiInterface::getHttpMethod, method)
                .eq(ApiInterface::getHttpPath, path);
        if (excludeId != null) {
            q.ne(ApiInterface::getId, excludeId);
        }
        Long c = interfaceMapper.selectCount(q);
        if (c != null && c > 0) {
            throw new BizException(ResultCode.CONFLICT, "接口路径已存在：" + method + " " + path);
        }
    }

    @Transactional
    public void delete(Long id) {
        ApiInterface exists = get(id);
        ruleMapper.delete(new LambdaQueryWrapper<MockRule>().eq(MockRule::getInterfaceId, id));
        interfaceMapper.deleteById(id);
        ruleCache.invalidate(exists.getProjectId());
    }
}
