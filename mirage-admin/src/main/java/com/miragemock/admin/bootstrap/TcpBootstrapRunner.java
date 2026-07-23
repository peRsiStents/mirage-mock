package com.miragemock.admin.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.TcpListenerMapper;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.TcpListener;
import com.miragemock.tcp.TcpServerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 启动期绑定所有已启用的 TCP 监听器（在建表与种子化之后执行）。
 */
@Component
@Order(200)
public class TcpBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TcpBootstrapRunner.class);

    private final TcpListenerMapper listenerMapper;
    private final TcpServerManager serverManager;

    @Autowired
    public TcpBootstrapRunner(TcpListenerMapper listenerMapper, TcpServerManager serverManager) {
        this.listenerMapper = listenerMapper;
        this.serverManager = serverManager;
    }

    @Override
    public void run(String... args) {
        List<TcpListener> enabled = listenerMapper.selectList(
                new LambdaQueryWrapper<TcpListener>().eq(TcpListener::getStatus, Constants.STATUS_ENABLED));
        if (!enabled.isEmpty()) {
            serverManager.startAll(enabled);
            String ports = enabled.stream().map(l -> String.valueOf(l.getPort())).collect(Collectors.joining(","));
            log.info("已启动 TCP 监听器，端口: {}", ports);
        }
    }
}
