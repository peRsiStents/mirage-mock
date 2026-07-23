package com.miragemock.dsl.spi;

/**
 * 密钥别名解析器：在模板求值时按别名取得已解密的密钥材料。
 *
 * <p>由持久层（mirage-admin）实现并注入到求值上下文。
 */
public interface SecretResolver {

    /**
     * @param alias     密钥别名
     * @param projectId 所属项目 id（密钥按项目隔离）
     * @return 密钥材料；不存在时返回 null
     */
    KeyMaterial resolve(String alias, Long projectId);
}
