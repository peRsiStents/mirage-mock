package com.miragemock.dsl.eval;

import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * 模板求值上下文：变量（path.* / 已渲染字段引用 / 请求字段）+ 密钥解析器 + 序列提供者 + 项目 id。
 */
public class EvalContext {

    private final Map<String, Object> variables;
    private final SecretResolver secretResolver;
    private final SeqProvider seqProvider;
    private final Long projectId;

    /**
     * 内部暂存区：供生成器函数之间传递中间结果（如 idcard.cn 生成后供 idcard.birthdate 复用）。
     * 与用户变量表分离，避免内部键污染变量命名空间（用户恰好定义同名变量时会被读到）。
     */
    private final Map<String, Object> internal = new HashMap<>();

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

    /** 内部暂存区取值；不存在返回 null */
    public Object getInternal(String key) {
        return internal.get(key);
    }

    /** 写入内部暂存区 */
    public void putInternal(String key, Object value) {
        internal.put(key, value);
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
