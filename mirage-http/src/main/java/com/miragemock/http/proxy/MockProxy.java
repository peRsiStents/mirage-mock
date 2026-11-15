package com.miragemock.http.proxy;

import com.miragemock.core.engine.MockEngine;
import com.miragemock.core.match.RequestSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 录制回放代理 SPI（HTTP 层调用，由 mirage-admin 提供真实实现；未提供时降级为空实现，功能关闭）。
 *
 * <p>触发时机：规则未命中时，若接口配置了录制回放（record_mode 1/2）与上游地址：</p>
 * <ul>
 *   <li>{@link #replay}：回放模式，按请求签名返回已录制快照；</li>
 *   <li>{@link #forward}：录制/回放模式，转发上游并按需保存快照。</li>
 * </ul>
 */
public interface MockProxy {

    /** 回放查询；无录制快照返回 null */
    MockProxyResult replay(MockEngine.ProxyHint hint, RequestSnapshot req);

    /** 转发上游（录制模式同时保存快照）；转发失败返回 null */
    MockProxyResult forward(MockEngine.ProxyHint hint, RequestSnapshot req);

    /** 默认空实现：未装配 admin 实现时功能关闭 */
    @Component
    @ConditionalOnMissingBean(MockProxy.class)
    class NoOp implements MockProxy {
        @Override
        public MockProxyResult replay(MockEngine.ProxyHint hint, RequestSnapshot req) {
            return null;
        }

        @Override
        public MockProxyResult forward(MockEngine.ProxyHint hint, RequestSnapshot req) {
            return null;
        }
    }
}
