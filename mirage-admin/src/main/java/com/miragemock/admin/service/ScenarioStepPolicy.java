package com.miragemock.admin.service;

/**
 * 场景步骤执行策略（纯逻辑，可单测）：
 * <ul>
 *   <li>执行条件：引用上一步结果决定本步骤是否执行（跳过不计失败）；</li>
 *   <li>重试：步骤失败后按配置自动重试（由调用方驱动，本类仅提供参数）。</li>
 * </ul>
 */
public final class ScenarioStepPolicy {

    private ScenarioStepPolicy() {
    }

    /**
     * 求值步骤执行条件。
     *
     * @param condition  步骤条件表达式（空/always=恒真；passed/failed；status==200/status!=200）
     * @param lastPassed 上一步是否通过；首个步骤为 null
     * @param lastStatus 上一步 HTTP 状态码；无响应/非 HTTP 为 null
     * @return true=执行本步骤；false=跳过
     */
    public static boolean shouldRun(String condition, Boolean lastPassed, Integer lastStatus) {
        if (condition == null) {
            return true;
        }
        String c = condition.trim();
        if (c.isEmpty() || "always".equalsIgnoreCase(c)) {
            return true;
        }
        if (lastPassed == null) {
            // 首个步骤：无条件执行
            return true;
        }
        if ("passed".equalsIgnoreCase(c)) {
            return Boolean.TRUE.equals(lastPassed);
        }
        if ("failed".equalsIgnoreCase(c)) {
            return !Boolean.TRUE.equals(lastPassed);
        }
        if (c.startsWith("status==")) {
            Integer v = parseInt(c.substring("status==".length()).trim());
            if (v == null) {
                return true; // 非法数字按恒真处理，避免误跳过
            }
            return lastStatus != null && lastStatus.intValue() == v;
        }
        if (c.startsWith("status!=")) {
            Integer v = parseInt(c.substring("status!=".length()).trim());
            if (v == null) {
                return true; // 非法数字按恒真处理，避免误跳过
            }
            return lastStatus == null || lastStatus.intValue() != v;
        }
        // 无法识别的条件按恒真处理，避免误跳过
        return true;
    }

    /**
     * 重试次数（非负）。
     *
     * @param retryCount 配置值（null 视为 0）
     */
    public static int retries(Integer retryCount) {
        if (retryCount == null || retryCount < 0) {
            return 0;
        }
        return retryCount;
    }

    /**
     * 重试间隔毫秒（默认 1000，至少 0）。
     */
    public static long retryDelayMs(Integer retryDelayMs) {
        if (retryDelayMs == null || retryDelayMs < 0) {
            return 1000L;
        }
        return retryDelayMs;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
