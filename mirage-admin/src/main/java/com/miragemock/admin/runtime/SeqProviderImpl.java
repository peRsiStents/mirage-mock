package com.miragemock.admin.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miragemock.admin.mapper.MockSequenceMapper;
import com.miragemock.common.entity.MockSequence;
import com.miragemock.dsl.spi.SeqProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目级自增序列提供者：DB 持久，按 (projectId, name) 加锁自增。
 */
@Component
public class SeqProviderImpl implements SeqProvider {

    private final MockSequenceMapper sequenceMapper;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public SeqProviderImpl(MockSequenceMapper sequenceMapper) {
        this.sequenceMapper = sequenceMapper;
    }

    @Override
    public long next(long projectId, String name, long startValue) {
        String lockKey = projectId + ":" + name;
        Object lock = locks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            MockSequence seq = sequenceMapper.selectOne(
                    new LambdaQueryWrapper<MockSequence>()
                            .eq(MockSequence::getProjectId, projectId)
                            .eq(MockSequence::getSeqName, name));
            if (seq == null) {
                seq = new MockSequence();
                seq.setProjectId(projectId);
                seq.setSeqName(name);
                seq.setCurrentValue(startValue);
                sequenceMapper.insert(seq);
                return startValue;
            }
            long next = seq.getCurrentValue() + 1;
            seq.setCurrentValue(next);
            sequenceMapper.updateById(seq);
            return next;
        }
    }
}
