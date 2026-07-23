package com.miragemock.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.dto.FileGenRequest;
import com.miragemock.admin.mapper.FileTemplateMapper;
import com.miragemock.common.api.ResultCode;
import com.miragemock.common.constant.Constants;
import com.miragemock.common.entity.FileTemplate;
import com.miragemock.common.exception.BizException;
import com.miragemock.dsl.eval.EvalContext;
import com.miragemock.dsl.eval.ExprException;
import com.miragemock.dsl.eval.ExpressionEvaluator;
import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 批量文件生成：逐行用 ExpressionEvaluator.evalTemplate 求值行模板，复用 DSL 生成器（含 seq/加密）。
 */
@Service
public class FileTemplateService {

    private static final int MAX_ROWS = 100_000;
    private static final int PREVIEW_LIMIT = 10;

    private final FileTemplateMapper mapper;
    private final ExpressionEvaluator evaluator;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;

    @Autowired
    public FileTemplateService(FileTemplateMapper mapper, ExpressionEvaluator evaluator,
                               SecretResolver secretResolver, SeqProvider seqProvider) {
        this.mapper = mapper;
        this.evaluator = evaluator;
        this.secretResolver = secretResolver;
        this.seqProvider = seqProvider;
    }

    public List<FileTemplate> list(Long projectId) {
        return mapper.selectList(new LambdaQueryWrapper<FileTemplate>()
                .eq(FileTemplate::getProjectId, projectId)
                .orderByDesc(FileTemplate::getCreateTime));
    }

    public FileTemplate get(Long id) {
        FileTemplate t = mapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND, "文件模板不存在");
        }
        return t;
    }

    @Transactional
    public FileTemplate create(FileTemplate t) {
        if (t.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        normalize(t);
        mapper.insert(t);
        return t;
    }

    @Transactional
    public FileTemplate update(Long id, FileTemplate patch) {
        FileTemplate exists = get(id);
        if (patch.getName() != null) exists.setName(patch.getName());
        if (patch.getHeaderLine() != null) exists.setHeaderLine(patch.getHeaderLine());
        if (patch.getRowTemplate() != null) exists.setRowTemplate(patch.getRowTemplate());
        if (patch.getRowCount() != null) exists.setRowCount(patch.getRowCount());
        if (patch.getEncoding() != null) exists.setEncoding(patch.getEncoding());
        if (patch.getLineSeparator() != null) exists.setLineSeparator(patch.getLineSeparator());
        if (patch.getFileExt() != null) exists.setFileExt(patch.getFileExt());
        if (patch.getStatus() != null) exists.setStatus(patch.getStatus());
        if (patch.getRemark() != null) exists.setRemark(patch.getRemark());
        normalize(exists);
        mapper.updateById(exists);
        return exists;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        mapper.deleteById(id);
    }

    /** 生成完整文件文本（rowCount 行 + 可选首行） */
    public GeneratedFile generate(FileGenRequest req) {
        normalizeReq(req);
        EvalContext ctx = newContext(req.getProjectId());
        ctx.getVariables().put("count", req.getRowCount());
        String sep = "LF".equalsIgnoreCase(req.getLineSeparator()) ? "\n" : "\r\n";
        StringBuilder sb = new StringBuilder();
        if (isNonEmpty(req.getHeaderLine())) {
            sb.append(eval(req.getHeaderLine(), ctx)).append(sep);
        }
        int n = req.getRowCount();
        for (int i = 0; i < n; i++) {
            sb.append(eval(req.getRowTemplate(), ctx)).append(sep);
        }
        return new GeneratedFile(name(req), ext(req), enc(req), sb.toString());
    }

    /** 预览：首行 + 前若干行（注意：含 ${seq} 时预览也会消费序列值） */
    public List<String> preview(FileGenRequest req) {
        normalizeReq(req);
        EvalContext ctx = newContext(req.getProjectId());
        ctx.getVariables().put("count", req.getRowCount());
        List<String> lines = new ArrayList<>();
        if (isNonEmpty(req.getHeaderLine())) {
            lines.add(eval(req.getHeaderLine(), ctx));
        }
        int n = Math.min(req.getRowCount() == null ? 0 : req.getRowCount(), PREVIEW_LIMIT);
        for (int i = 0; i < n; i++) {
            lines.add(eval(req.getRowTemplate(), ctx));
        }
        return lines;
    }

    private String eval(String tpl, EvalContext ctx) {
        try {
            Object v = evaluator.evalTemplate(tpl, ctx);
            return v == null ? "" : ExpressionEvaluator.stringify(v);
        } catch (ExprException e) {
            throw new BizException(ResultCode.EXPRESSION_ERROR, "模板求值失败: " + e.getMessage());
        }
    }

    private EvalContext newContext(Long projectId) {
        return new EvalContext(new HashMap<>(), secretResolver, seqProvider, projectId);
    }

    private void normalize(FileTemplate t) {
        if (t.getStatus() == null) t.setStatus(Constants.STATUS_ENABLED);
        if (t.getEncoding() == null || t.getEncoding().isEmpty()) t.setEncoding("GBK");
        if (t.getLineSeparator() == null || t.getLineSeparator().isEmpty()) t.setLineSeparator("CRLF");
        if (t.getFileExt() == null || t.getFileExt().isEmpty()) t.setFileExt("txt");
        if (t.getRowCount() == null || t.getRowCount() <= 0) t.setRowCount(100);
        if (t.getRowCount() > MAX_ROWS) t.setRowCount(MAX_ROWS);
    }

    private void normalizeReq(FileGenRequest r) {
        if (r.getProjectId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "projectId 不能为空");
        }
        if (r.getRowTemplate() == null || r.getRowTemplate().trim().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "行模板不能为空");
        }
        if (r.getRowCount() == null || r.getRowCount() <= 0) r.setRowCount(100);
        if (r.getRowCount() > MAX_ROWS) r.setRowCount(MAX_ROWS);
        if (r.getEncoding() == null || r.getEncoding().isEmpty()) r.setEncoding("GBK");
        if (r.getLineSeparator() == null || r.getLineSeparator().isEmpty()) r.setLineSeparator("CRLF");
        if (r.getFileExt() == null || r.getFileExt().isEmpty()) r.setFileExt("txt");
    }

    private boolean isNonEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String name(FileGenRequest r) {
        return (r.getName() == null || r.getName().trim().isEmpty()) ? "mirage-file" : r.getName();
    }

    private String ext(FileGenRequest r) {
        return r.getFileExt() == null || r.getFileExt().isEmpty() ? "txt" : r.getFileExt();
    }

    private String enc(FileGenRequest r) {
        return r.getEncoding() == null || r.getEncoding().isEmpty() ? "GBK" : r.getEncoding();
    }

    /** 生成结果（供 Controller 编码为文件流） */
    public static final class GeneratedFile {
        private final String name;
        private final String ext;
        private final String encoding;
        private final String text;

        public GeneratedFile(String name, String ext, String encoding, String text) {
            this.name = name;
            this.ext = ext;
            this.encoding = encoding;
            this.text = text;
        }

        public String getName() { return name; }
        public String getExt() { return ext; }
        public String getEncoding() { return encoding; }
        public String getText() { return text; }
    }
}
