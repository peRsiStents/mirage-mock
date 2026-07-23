package com.miragemock.dsl.spi;

/**
 * 项目级自增序列提供者（${seq(name,start)}）。由持久层实现，保证规则重启不回退。
 */
public interface SeqProvider {

    /**
     * 取下一个序列值。若序列不存在则从 startValue 开始。
     *
     * @param projectId  项目 id
     * @param name       序列名
     * @param startValue 起始值（序列首次创建时使用）
     * @return 当前自增后的值
     */
    long next(long projectId, String name, long startValue);
}
