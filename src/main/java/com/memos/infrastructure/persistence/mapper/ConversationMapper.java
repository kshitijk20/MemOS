package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.conversation.Conversation;
import com.memos.infrastructure.persistence.entity.ConversationEntity;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    Conversation toDomain(ConversationEntity entity);

    ConversationEntity toEntity(Conversation domain);
}
