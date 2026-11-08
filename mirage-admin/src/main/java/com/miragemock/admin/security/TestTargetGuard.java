package com.miragemock.admin.security;

import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试执行器 SSRF 防护：校验目标主机是否允许访问。
 *
 * <p>解析主机名后逐 IP 校验，覆盖「直接 IP」与「域名解析」两种情形。
 *
 * <p><b>DNS rebinding 防护</b>：仅「先解析校验、再按域名连接」存在 TOCTOU 窗口
 * （校验时解析到公网、连接时解析到内网）。因此 TCP 连接场景必须使用
 * {@link #resolveAllowed(String)} 返回的**已校验 IP** 建立连接，
 * 使校验结果与实际连接目标严格一致。
 */
@Component
public class TestTargetGuard {

    /**
     * 始终拦截的高危地址（云元数据 / 实例元数据服务）。
     *
     * <p>这些地址与 {@code block-private} 开关无关——即使允许访问私网，
     * 元数据服务仍可能泄露 IAM 临时凭证。
     */
    private static final Set<String> ALWAYS_BLOCKED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "169.254.169.254",       // AWS / Azure / GCP / 阿里云 IMDS
            "169.254.170.2",         // AWS ECS 任务元数据
            "100.100.100.200",       // 阿里云 ECS 元数据
            "fd00:ec2::254",         // AWS IPv6 IMDS
            "168.63.129.16",         // Azure WireServer（可下发证书/配置）
            "metadata.google.internal",
            "metadata.goog",
            "instance-data")));

    private final TestTargetProperties props;

    public TestTargetGuard(TestTargetProperties props) {
        this.props = props;
    }

    /** 校验目标主机；不允许则抛 BizException。host 为空直接放行（交由后续连接报错）。 */
    public void assertAllowed(String host) {
        if (host == null || host.isEmpty()) {
            return;
        }
        Set<String> blocked = blockedLower();
        String h = host.toLowerCase();
        if (blocked.contains(h) || ALWAYS_BLOCKED.contains(h)) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标地址被策略禁止访问：" + host);
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                assertAddressAllowed(addr, host, blocked);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // DNS 解析失败：交由后续连接步骤报错，不在此阻断
        }
    }

    /**
     * 校验已解析出的 IP（供 HTTP/TCP 连接复用，避免重复解析带来的 rebinding 窗口）。
     */
    public void assertAllowed(InetAddress addr, String host) {
        assertAddressAllowed(addr, host, blockedLower());
    }

    /**
     * 解析主机名并校验，**返回已校验的地址**。
     *
     * <p>调用方必须用返回的地址建立连接（而不是再用域名连接），
     * 否则「校验时解析到的 IP」与「实际连接的 IP」可能不同（DNS rebinding）。
     *
     * @throws BizException 目标被策略禁止
     */
    public InetAddress resolveAllowed(String host) {
        if (host == null || host.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标主机为空");
        }
        Set<String> blocked = blockedLower();
        String h = host.toLowerCase();
        if (blocked.contains(h) || ALWAYS_BLOCKED.contains(h)) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标地址被策略禁止访问：" + host);
        }
        InetAddress[] all;
        try {
            all = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标主机无法解析：" + host);
        }
        if (all == null || all.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标主机无法解析：" + host);
        }
        BizException firstError = null;
        for (InetAddress addr : all) {
            try {
                assertAddressAllowed(addr, host, blocked);
                return addr;
            } catch (BizException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        throw firstError != null ? firstError
                : new BizException(ResultCode.BAD_REQUEST, "目标地址被策略禁止访问：" + host);
    }

    private void assertAddressAllowed(InetAddress addr, String host, Set<String> blocked) {
        String ip = addr.getHostAddress();
        if (blocked.contains(ip) || ALWAYS_BLOCKED.contains(ip.toLowerCase())) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标地址被策略禁止访问：" + host + " (" + ip + ")");
        }
        if (props.isBlockPrivate() && isPrivate(addr)) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "目标地址解析到内网/回环地址，被策略拦截：" + host + " (" + ip + ")");
        }
    }

    private boolean isPrivate(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }
        // IPv6 唯一本地地址 fc00::/7（Java 无内置判定）
        String ip = addr.getHostAddress().toLowerCase();
        return ip.startsWith("fc") || ip.startsWith("fd");
    }

    private Set<String> blockedLower() {
        List<String> list = props.getBlockedHosts();
        Set<String> set = new HashSet<>();
        if (list != null) {
            for (String b : list) {
                if (b != null) {
                    set.add(b.toLowerCase().trim());
                }
            }
        }
        return set;
    }
}
