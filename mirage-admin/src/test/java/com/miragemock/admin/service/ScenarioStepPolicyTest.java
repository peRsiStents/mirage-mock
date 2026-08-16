package com.miragemock.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景步骤策略单测：执行条件（always/passed/failed/status==/status!=/非法值）与重试参数。
 */
class ScenarioStepPolicyTest {

    // ============ 执行条件 ============

    @Test
    void nullOrAlwaysRuns() {
        assertTrue(ScenarioStepPolicy.shouldRun(null, null, null));
        assertTrue(ScenarioStepPolicy.shouldRun("", false, 500));
        assertTrue(ScenarioStepPolicy.shouldRun("always", false, 500));
        assertTrue(ScenarioStepPolicy.shouldRun("ALWAYS", false, 500));
    }

    @Test
    void firstStepAlwaysRuns() {
        // 首步（lastPassed=null）无条件执行，即使配置了 passed
        assertTrue(ScenarioStepPolicy.shouldRun("passed", null, null));
        assertTrue(ScenarioStepPolicy.shouldRun("failed", null, null));
    }

    @Test
    void passedCondition() {
        assertTrue(ScenarioStepPolicy.shouldRun("passed", true, 200));
        assertFalse(ScenarioStepPolicy.shouldRun("passed", false, 500));
    }

    @Test
    void failedCondition() {
        assertTrue(ScenarioStepPolicy.shouldRun("failed", false, 500));
        assertFalse(ScenarioStepPolicy.shouldRun("failed", true, 200));
    }

    @Test
    void statusCondition() {
        assertTrue(ScenarioStepPolicy.shouldRun("status==200", true, 200));
        assertFalse(ScenarioStepPolicy.shouldRun("status==200", true, 500));
        assertTrue(ScenarioStepPolicy.shouldRun("status!=500", true, 200));
        assertFalse(ScenarioStepPolicy.shouldRun("status!=500", true, 500));
        // 上一步无状态（TCP/错误）
        assertFalse(ScenarioStepPolicy.shouldRun("status==200", true, null));
        assertTrue(ScenarioStepPolicy.shouldRun("status!=200", true, null));
    }

    @Test
    void unknownConditionRunsToAvoidSkip() {
        assertTrue(ScenarioStepPolicy.shouldRun("garbage", false, 500));
        assertTrue(ScenarioStepPolicy.shouldRun("status==abc", false, 500)); // 非法数字按恒真
    }

    // ============ 重试参数 ============

    @Test
    void retriesParsed() {
        assertEquals(0, ScenarioStepPolicy.retries(null));
        assertEquals(0, ScenarioStepPolicy.retries(-1));
        assertEquals(3, ScenarioStepPolicy.retries(3));
    }

    @Test
    void retryDelayParsed() {
        assertEquals(1000L, ScenarioStepPolicy.retryDelayMs(null));
        assertEquals(1000L, ScenarioStepPolicy.retryDelayMs(-5));
        assertEquals(500L, ScenarioStepPolicy.retryDelayMs(500));
        assertEquals(0L, ScenarioStepPolicy.retryDelayMs(0));
    }
}
