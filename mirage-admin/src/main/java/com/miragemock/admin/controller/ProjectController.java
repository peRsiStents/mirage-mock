package com.miragemock.admin.controller;

import com.miragemock.admin.dto.MemberRequest;
import com.miragemock.admin.service.ProjectService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.Project;
import com.miragemock.common.entity.ProjectMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<Project>> list() {
        return Result.ok(projectService.list());
    }

    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.ok(projectService.create(project));
    }

    /** 一键生成示例项目（含 HTTP 接口与 Mock 规则），供新用户快速体验 */
    @PostMapping("/sample")
    public Result<Project> createSample() {
        return Result.ok(projectService.createSample());
    }

    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.ok(projectService.get(id));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return Result.ok(projectService.update(id, project));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    public Result<List<ProjectMember>> members(@PathVariable Long id) {
        return Result.ok(projectService.members(id));
    }

    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id, @RequestBody MemberRequest request) {
        projectService.addMember(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return Result.ok();
    }

    /**
     * 查看 CI 令牌：仅项目管理员可见。
     *
     * <p>令牌等价于「免 JWT 触发本项目场景运行」的凭据，因此只对项目管理员开放，
     * 且不随项目列表/详情返回（Project.ciToken 已 @JsonIgnore）。
     */
    @GetMapping("/{id}/ci-token")
    public Result<Map<String, Object>> ciToken(@PathVariable Long id) {
        return Result.ok(projectService.getCiToken(id));
    }

    /** 轮换 CI 令牌：旧令牌立即失效。 */
    @PostMapping("/{id}/ci-token/rotate")
    public Result<Map<String, Object>> rotateCiToken(@PathVariable Long id) {
        return Result.ok(projectService.rotateCiToken(id));
    }
}
