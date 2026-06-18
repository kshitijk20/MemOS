package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.memos.domain.conversation.Message;
import com.memos.infrastructure.persistence.entity.MessageEntity;

@Mapper(componentModel = "spring", uses = CitationMapper.class)
public interface MessageMapper {

    Message toDomain(MessageEntity entity);

    @Mapping(target = "citations", ignore = true)
    MessageEntity toEntity(Message domain);
}
