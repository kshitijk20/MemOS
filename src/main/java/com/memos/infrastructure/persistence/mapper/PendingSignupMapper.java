package com.memos.infrastructure.persistence.mapper;

import org.mapstruct.Mapper;

import com.memos.domain.user.PendingSignup;
import com.memos.infrastructure.persistence.entity.PendingSignupEntity;

@Mapper(componentModel = "spring")
public interface PendingSignupMapper {

    PendingSignup toDomain(PendingSignupEntity entity);

    PendingSignupEntity toEntity(PendingSignup domain);
}
