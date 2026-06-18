package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.memos.domain.conversation.Citation;
import com.memos.infrastructure.persistence.entity.CitationEntity;

@Mapper(componentModel = "spring")
public interface CitationMapper {

    @Mapping(target = "messageId", source = "message.id")
    Citation toDomain(CitationEntity entity);

    @Mapping(target = "message", ignore = true)
    CitationEntity toEntity(Citation domain);
}
