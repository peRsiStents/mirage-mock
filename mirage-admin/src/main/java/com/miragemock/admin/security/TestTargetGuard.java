package com.miragemock.admin.security;

import com.miragemock.common.api.ResultCode;
import com.miragemock.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 测试执行器 SSRF 防护：校验目标主机是否允许访问。
 *
 * <p>解析主机名后逐 IP 校验，覆盖「直接 IP」与「域名解析」两种情形。
 * 注：DNS rebinding（校验时解析到公网、连接时解析到内网）为残留边角，需网络层（隔离网络/安全组）兜底。
 */
@Component
public class TestTargetGuard {

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
        if (blocked.contains(h)) {
            throw new BizException(ResultCode.BAD_REQUEST, "目标地址被策略禁止访问：" + host);
        }
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                String ip = addr.getHostAddress();
                if (blocked.contains(ip)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "目标地址被策略禁止访问：" + host + " (" + ip + ")");
                }
                if (props.isBlockPrivate() && isPrivate(addr)) {
                    throw new BizException(ResultCode.BAD_REQUEST,
                            "目标地址解析到内网/回环地址，被策略拦截：" + host + " (" + ip + ")");
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // DNS 解析失败：交由后续连接步骤报错，不在此阻断
        }
    }

    private boolean isPrivate(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }
        // IPv6 唯一本地地址 fc00::/7（Java 无内置判定）
        String ip = addr.getHostAddress();
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
