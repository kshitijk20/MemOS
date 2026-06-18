package com.memos.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.infrastructure.persistence.PageMappingUtil;
import com.memos.infrastructure.persistence.mapper.ConversationMapper;
import com.memos.infrastructure.persistence.repository.ConversationJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConversationRepositoryAdapter implements ConversationRepositoryPort {

    private final ConversationJpaRepository jpaRepository;
    private final ConversationMapper mapper;

    @Override
    public Conversation save(Conversation conversation) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(conversation)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Conversation> findAllByUserId(UUID userId, PageRequest pageRequest) {
        return PageMappingUtil.toPageResult(
                jpaRepository.findAllByUserId(userId, PageMappingUtil.toPageable(pageRequest)),
                mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.existsByIdAndUserId(id, userId);
    }
}
