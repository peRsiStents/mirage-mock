package com.miragemock.admin.controller;

import com.miragemock.admin.dto.ScenarioRunResult;
import com.miragemock.admin.mapper.ProjectMapper;
import com.miragemock.admin.mapper.TestScenarioMapper;
import com.miragemock.admin.service.ScenarioService;
import com.miragemock.common.api.Result;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.entity.Project;
import com.miragemock.common.entity.TestScenario;
import com.miragemock.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CI headless 运行接口（免 JWT，凭 project.ciToken 鉴权）。
 * 供 CI/CD 流水线或定时外部系统调用：POST /api/v1/ci/scenarios/{id}/run?token=&env=
 */
@RestController
@RequestMapping("/api/v1/ci")
public class CiController {

    private final ScenarioService scenarioService;
    private final TestScenarioMapper scenarioMapper;
    private final ProjectMapper projectMapper;

    @Autowired
    public CiController(ScenarioService scenarioService, TestScenarioMapper scenarioMapper,
                        ProjectMapper projectMapper) {
        this.scenarioService = scenarioService;
        this.scenarioMapper = scenarioMapper;
        this.projectMapper = projectMapper;
    }

    @PostMapping("/scenarios/{id}/run")
    public Result<ScenarioRunResult> run(@PathVariable Long id,
                                         @RequestParam String token,
                                         @RequestParam(required = false) Long env) {
        TestScenario sc = scenarioMapper.selectById(id);
        if (sc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场景不存在");
        }
        Project p = projectMapper.selectById(sc.getProjectId());
        if (p == null || p.getCiToken() == null || !p.getCiToken().equals(token)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "CI token 无效");
        }
        return Result.ok(scenarioService.runScenario(id, env));
    }
}
