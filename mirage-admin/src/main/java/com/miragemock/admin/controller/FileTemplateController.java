package com.miragemock.admin.controller;

import com.miragemock.admin.dto.FileGenRequest;
import com.miragemock.admin.service.FileTemplateService;
import com.miragemock.common.api.Result;
import com.miragemock.common.entity.FileTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;

/**
 * 批量文件生成：模板 CRUD + 预览 + 生成下载。
 */
@RestController
@RequestMapping("/api/v1")
public class FileTemplateController {

    private final FileTemplateService service;

    @Autowired
    public FileTemplateController(FileTemplateService service) {
        this.service = service;
    }

    @GetMapping("/projects/{pid}/file-templates")
    public Result<List<FileTemplate>> list(@PathVariable Long pid) {
        return Result.ok(service.list(pid));
    }

    @PostMapping("/projects/{pid}/file-templates")
    public Result<FileTemplate> create(@PathVariable Long pid, @RequestBody FileTemplate t) {
        t.setProjectId(pid);
        return Result.ok(service.create(t));
    }

    @PutMapping("/file-templates/{id}")
    public Result<FileTemplate> update(@PathVariable Long id, @RequestBody FileTemplate t) {
        return Result.ok(service.update(id, t));
    }

    @DeleteMapping("/file-templates/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/file-templates/preview")
    public Result<List<String>> preview(@RequestBody FileGenRequest req) {
        return Result.ok(service.preview(req));
    }

    /** 生成并返回文件流（按 encoding 编码字节） */
    @PostMapping("/file-templates/generate")
    public ResponseEntity<byte[]> generate(@RequestBody FileGenRequest req) throws Exception {
        FileTemplateService.GeneratedFile gf = service.generate(req);
        String fileName = URLEncoder.encode(gf.getName() + "." + gf.getExt(), "UTF-8").replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);
        byte[] body = gf.getText().getBytes(Charset.forName(gf.getEncoding()));
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
