package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.user.User;
import com.memos.infrastructure.persistence.entity.UserEntity;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toDomain(UserEntity entity);

    UserEntity toEntity(User domain);
}
