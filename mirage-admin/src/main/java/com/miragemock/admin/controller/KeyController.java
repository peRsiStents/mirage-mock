package com.miragemock.admin.controller;

import com.miragemock.admin.dto.KeyRequest;
import com.miragemock.admin.dto.Sm2KeyResult;
import com.miragemock.admin.service.KeyService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class KeyController {

    private final KeyService keyService;

    @Autowired
    public KeyController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping("/projects/{pid}/keys")
    public Result<List<SecretKey>> list(@PathVariable Long pid) {
        return Result.ok(keyService.list(pid));
    }

    @PostMapping("/projects/{pid}/keys")
    public Result<SecretKey> create(@PathVariable Long pid, @RequestBody KeyRequest request) {
        return Result.ok(keyService.create(pid, request));
    }

    @PostMapping("/projects/{pid}/keys/sm2/generate")
    public Result<Sm2KeyResult> generateSm2(@PathVariable Long pid, @RequestParam String alias) {
        return Result.ok(keyService.generateSm2(pid, alias));
    }

    @DeleteMapping("/keys/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        keyService.delete(id);
        return Result.ok();
    }
}
