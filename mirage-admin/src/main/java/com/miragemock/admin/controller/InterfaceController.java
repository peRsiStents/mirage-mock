package com.miragemock.admin.controller;

import com.miragemock.admin.service.InterfaceService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.ApiInterface;
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

@RestController
@RequestMapping("/api/v1")
public class InterfaceController {

    private final InterfaceService interfaceService;

    @Autowired
    public InterfaceController(InterfaceService interfaceService) {
        this.interfaceService = interfaceService;
    }

    @GetMapping("/projects/{pid}/interfaces")
    public Result<List<ApiInterface>> list(@PathVariable Long pid) {
        return Result.ok(interfaceService.list(pid));
    }

    @PostMapping("/projects/{pid}/interfaces")
    public Result<ApiInterface> create(@PathVariable Long pid, @RequestBody ApiInterface iface) {
        iface.setProjectId(pid);
        return Result.ok(interfaceService.create(iface));
    }

    @GetMapping("/interfaces/{id}")
    public Result<ApiInterface> get(@PathVariable Long id) {
        return Result.ok(interfaceService.get(id));
    }

    @PutMapping("/interfaces/{id}")
    public Result<ApiInterface> update(@PathVariable Long id, @RequestBody ApiInterface iface) {
        return Result.ok(interfaceService.update(id, iface));
    }

    @DeleteMapping("/interfaces/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        interfaceService.delete(id);
        return Result.ok();
    }
}
