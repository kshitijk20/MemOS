package com.memos.infrastructure.persistence.adapter;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.memos.domain.common.PageResult;
import com.memos.domain.conversation.Citation;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.infrastructure.persistence.PageMappingUtil;
import com.memos.infrastructure.persistence.entity.CitationEntity;
import com.memos.infrastructure.persistence.entity.MessageEntity;
import com.memos.infrastructure.persistence.mapper.CitationMapper;
import com.memos.infrastructure.persistence.mapper.MessageMapper;
import com.memos.infrastructure.persistence.repository.MessageJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageRepositoryAdapter implements MessageRepositoryPort {

    private final MessageJpaRepository jpaRepository;
    private final MessageMapper mapper;
    private final CitationMapper citationMapper;

    @Override
    public Message save(Message message) {
        MessageEntity entity = mapper.toEntity(message);
        if (message.getCitations() != null) {
            for (Citation citation : message.getCitations()) {
                CitationEntity citationEntity = citationMapper.toEntity(citation);
                entity.addCitation(citationEntity);
            }
        }
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Message> findByConversationId(UUID conversationId, com.memos.domain.common.PageRequest pageRequest) {
        return PageMappingUtil.toPageResult(
                jpaRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, PageMappingUtil.toPageable(pageRequest)),
                mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> findRecentByConversationId(UUID conversationId, int limit) {
        List<MessageEntity> recentDescending = jpaRepository.findRecentByConversationId(
                conversationId, PageRequest.of(0, limit));
        List<Message> messages = recentDescending.stream().map(mapper::toDomain).toList();
        return Collections.unmodifiableList(reversed(messages));
    }

    @Override
    public long countByConversationId(UUID conversationId) {
        return jpaRepository.countByConversationId(conversationId);
    }

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new java.util.ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }
}
