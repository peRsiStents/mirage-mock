package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.ApiInterfaceMapper;
import com.miragemock.admin.mapper.MockRuleMapper;
import com.miragemock.admin.mapper.TcpListenerMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.ApiInterface;
import com.miragemock.common.entity.MockRule;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.common.exception.BizException;
import com.miragemock.tcp.TcpServerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TcpListenerService {

    private final TcpListenerMapper listenerMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final MockRuleMapper ruleMapper;
    private final TcpServerManager serverManager;

    @Autowired
    public TcpListenerService(TcpListenerMapper listenerMapper, ApiInterfaceMapper interfaceMapper,
                              MockRuleMapper ruleMapper, TcpServerManager serverManager) {
        this.listenerMapper = listenerMapper;
        this.interfaceMapper = interfaceMapper;
        this.ruleMapper = ruleMapper;
        this.serverManager = serverManager;
    }

    public List<TcpListener> list(Long projectId) {
        return listenerMapper.selectList(new LambdaQueryWrapper<TcpListener>()
                .eq(projectId != null, TcpListener::getProjectId, projectId)
                .orderByAsc(TcpListener::getId));
    }

    public TcpListener get(Long id) {
        TcpListener l = listenerMapper.selectById(id);
        if (l == null) {
            throw new BizException(ResultCode.NOT_FOUND, "TCP 监听器不存在");
        }
        return l;
    }

    @Transactional
    public TcpListener create(TcpListener listener) {
        if (listener.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (listener.getStatus() == null) {
            listener.setStatus(Constants.STATUS_ENABLED);
        }
        if (listener.getConnMode() == null) {
            listener.setConnMode("LONG");
        }
        if (listener.getMatchMode() == null) {
            listener.setMatchMode("SYNC");
        }
        if (listener.getMessageFormat() == null) {
            listener.setMessageFormat("json");
        }
        listenerMapper.insert(listener);
        if (listener.getStatus() == Constants.STATUS_ENABLED) {
            serverManager.start(listener);
        }
        return listener;
    }

    @Transactional
    public TcpListener update(Long id, TcpListener patch) {
        TcpListener exists = get(id);
        if (patch.getName() != null) {
            exists.setName(patch.getName());
        }
        if (patch.getPort() != null) {
            exists.setPort(patch.getPort());
        }
        if (patch.getConnMode() != null) {
            exists.setConnMode(patch.getConnMode());
        }
        if (patch.getFrameConfig() != null) {
            exists.setFrameConfig(patch.getFrameConfig());
        }
        if (patch.getMessageFormat() != null) {
            exists.setMessageFormat(patch.getMessageFormat());
        }
        if (patch.getMessageFormatConfig() != null) {
            exists.setMessageFormatConfig(patch.getMessageFormatConfig());
        }
        if (patch.getRouteExtract() != null) {
            exists.setRouteExtract(patch.getRouteExtract());
        }
        if (patch.getSerialExtract() != null) {
            exists.setSerialExtract(patch.getSerialExtract());
        }
        if (patch.getMatchMode() != null) {
            exists.setMatchMode(patch.getMatchMode());
        }
        if (patch.getPushConfig() != null) {
            exists.setPushConfig(patch.getPushConfig());
        }
        if (patch.getStatus() != null) {
            exists.setStatus(patch.getStatus());
        }
        listenerMapper.updateById(exists);
        serverManager.restart(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        serverManager.stop(id);
        // 级联清理该监听器下的 TCP 接口与规则
        List<ApiInterface> interfaces = interfaceMapper.selectList(
                new LambdaQueryWrapper<ApiInterface>().eq(ApiInterface::getTcpListenerId, id));
        for (ApiInterface iface : interfaces) {
            ruleMapper.delete(new LambdaQueryWrapper<MockRule>().eq(MockRule::getInterfaceId, iface.getId()));
        }
        if (!interfaces.isEmpty()) {
            interfaceMapper.deleteBatchIds(collectIds(interfaces));
        }
        listenerMapper.deleteById(id);
    }

    @Transactional
    public TcpListener start(Long id) {
        TcpListener l = get(id);
        l.setStatus(Constants.STATUS_ENABLED);
        listenerMapper.updateById(l);
        serverManager.restart(l);
        return l;
    }

    @Transactional
    public TcpListener stop(Long id) {
        TcpListener l = get(id);
        l.setStatus(Constants.STATUS_DISABLED);
        listenerMapper.updateById(l);
        serverManager.stop(id);
        return l;
    }

    public boolean isRunning(Long id) {
        return serverManager.isRunning(id);
    }

    private java.util.List<Long> collectIds(List<ApiInterface> interfaces) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (ApiInterface i : interfaces) {
            ids.add(i.getId());
        }
        return ids;
    }
}
