package com.miragemock.admin.controller;

import com.miragemock.admin.service.TcpListenerService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.TcpListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class TcpListenerController {

    private final TcpListenerService listenerService;

    @Autowired
    public TcpListenerController(TcpListenerService listenerService) {
        this.listenerService = listenerService;
    }

    @GetMapping("/projects/{pid}/listeners")
    public Result<List<TcpListener>> list(@PathVariable Long pid) {
        return Result.ok(listenerService.list(pid));
    }

    @PostMapping("/projects/{pid}/listeners")
    public Result<TcpListener> create(@PathVariable Long pid, @RequestBody TcpListener listener) {
        listener.setProjectId(pid);
        return Result.ok(listenerService.create(listener));
    }

    @GetMapping("/listeners/{id}")
    public Result<TcpListener> get(@PathVariable Long id) {
        return Result.ok(listenerService.get(id));
    }

    @PutMapping("/listeners/{id}")
    public Result<TcpListener> update(@PathVariable Long id, @RequestBody TcpListener listener) {
        return Result.ok(listenerService.update(id, listener));
    }

    @DeleteMapping("/listeners/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        listenerService.delete(id);
        return Result.ok();
    }

    @PostMapping("/listeners/{id}/start")
    public Result<TcpListener> start(@PathVariable Long id) {
        return Result.ok(listenerService.start(id));
    }

    @PostMapping("/listeners/{id}/stop")
    public Result<TcpListener> stop(@PathVariable Long id) {
        return Result.ok(listenerService.stop(id));
    }

    @GetMapping("/listeners/{id}/status")
    public Result<Map<String, Object>> status(@PathVariable Long id) {
        Map<String, Object> m = new HashMap<>();
        m.put("running", listenerService.isRunning(id));
        return Result.ok(m);
    }
}
