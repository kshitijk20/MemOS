package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.document.Document;
import com.memos.infrastructure.persistence.entity.DocumentEntity;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    Document toDomain(DocumentEntity entity);

    DocumentEntity toEntity(Document domain);
}
