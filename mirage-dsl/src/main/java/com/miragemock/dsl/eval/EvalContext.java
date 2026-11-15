package com.miragemock.dsl.eval;

import com.miragemock.dsl.spi.SecretResolver;
import com.miragemock.dsl.spi.SeqProvider;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 模板求值上下文：变量（path.* / 已渲染字段引用 / 请求字段）+ 密钥解析器 + 序列提供者 + 项目 id。
 *
 * <p>严格模式（{@link #setStrict}）：表达式主体位置的未知裸标识符直接抛错（帮助发现字段名/变量拼写错误）；
 * 函数参数位置的裸标识符（如 {@code string(alpha_num,32)} 的字符集 token、{@code uuid(nodash)}）仍按字面量处理。</p>
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

    private boolean strict = false;
    private int inArgsDepth = 0;
    private final Set<String> unknownVars = new LinkedHashSet<>();

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

    // ============ 严格模式 ============

    public EvalContext setStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    public boolean isStrict() {
        return strict;
    }

    /** 进入函数参数求值（参数内的裸 token 允许按字面量处理） */
    public void pushInArgs() {
        inArgsDepth++;
    }

    public void popInArgs() {
        inArgsDepth--;
    }

    public boolean isInArgs() {
        return inArgsDepth > 0;
    }

    /** 本上下文求值过程中出现的未知变量名（严格模式抛出前已记录，便于诊断） */
    public Set<String> getUnknownVars() {
        return unknownVars;
    }

    public void recordUnknownVar(String name) {
        unknownVars.add(name);
    }
}
