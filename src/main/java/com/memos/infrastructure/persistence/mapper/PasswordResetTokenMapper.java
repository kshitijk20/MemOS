package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.user.PasswordResetToken;
import com.memos.infrastructure.persistence.entity.PasswordResetTokenEntity;

@Mapper(componentModel = "spring")
public interface PasswordResetTokenMapper {

    PasswordResetToken toDomain(PasswordResetTokenEntity entity);

    PasswordResetTokenEntity toEntity(PasswordResetToken domain);
}
