package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.document.DocumentChunk;
import com.memos.infrastructure.persistence.entity.DocumentChunkEntity;

@Mapper(componentModel = "spring")
public interface DocumentChunkMapper {

    DocumentChunk toDomain(DocumentChunkEntity entity);

    DocumentChunkEntity toEntity(DocumentChunk domain);
}
