package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.TestEnvironmentMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TestEnvironment;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 测试环境管理（项目级）：baseUrl + 变量集。变量并入 ${var.name}，相对URL 拼 baseUrl。
 */
@Service
public class EnvironmentService {

    private final TestEnvironmentMapper envMapper;

    @Autowired
    public EnvironmentService(TestEnvironmentMapper envMapper) {
        this.envMapper = envMapper;
    }

    public List<TestEnvironment> list(Long projectId) {
        return envMapper.selectList(new LambdaQueryWrapper<TestEnvironment>()
                .eq(TestEnvironment::getProjectId, projectId)
                .orderByDesc(TestEnvironment::getCreateTime));
    }

    public TestEnvironment get(Long id) {
        TestEnvironment e = envMapper.selectById(id);
        if (e == null) {
            throw new BizException(ResultCode.NOT_FOUND, "环境不存在");
        }
        return e;
    }

    @Transactional
    public TestEnvironment create(TestEnvironment e) {
        if (e.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (e.getStatus() == null) {
            e.setStatus(Constants.STATUS_ENABLED);
        }
        envMapper.insert(e);
        return e;
    }

    @Transactional
    public TestEnvironment update(Long id, TestEnvironment patch) {
        TestEnvironment exists = get(id);
        if (patch.getName() != null) exists.setName(patch.getName());
        if (patch.getBaseUrl() != null) exists.setBaseUrl(patch.getBaseUrl());
        if (patch.getVariables() != null) exists.setVariables(patch.getVariables());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        envMapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        envMapper.deleteById(id);
    }
}
