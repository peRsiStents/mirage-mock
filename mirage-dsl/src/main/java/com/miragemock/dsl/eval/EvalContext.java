package com.miragemock.dsl.eval;

import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;

import java.util.Map;

/**
 * 模板求值上下文：变量（path.* / 已渲染字段引用 / 请求字段）+ 密钥解析器 + 序列提供者 + 项目 id。
 */
public class EvalContext {

    private final Map<String, Object> variables;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;
    private final Long projectId;

    public EvalContext(Map<String, Object> variables, SecretResolver secretResolver) {
        this(variables, secretResolver, null, null);
    }

    public EvalContext(Map<String, Object> variables, SecretResolver secretResolver,
                       SeqProvider seqProvider, Long projectId) {
        this.variables = variables;
        this.secretResolver = secretResolver;
        this.seqProvider = seqProvider;
        this.projectId = projectId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    /** 取变量值；不存在返回 null */
    public Object getVariable(String name) {
        return variables == null ? null : variables.get(name);
    }

    public boolean hasVariable(String name) {
        return variables != null && variables.containsKey(name);
    }

    public SecretResolver getSecretResolver() {
        return secretResolver;
    }

    public SeqProvider getSeqProvider() {
        return seqProvider;
    }

    public Long getProjectId() {
        return projectId;
    }
}
