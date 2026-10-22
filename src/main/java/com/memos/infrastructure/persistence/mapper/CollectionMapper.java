package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.collection.Collection;
import com.memos.infrastructure.persistence.entity.CollectionEntity;

@Mapper(componentModel = "spring")
public interface CollectionMapper {

    Collection toDomain(CollectionEntity entity);

    CollectionEntity toEntity(Collection domain);
}
