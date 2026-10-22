package com.memos.web.collection;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memos.application.collection.CollectionApplicationService;
import com.memos.infrastructure.security.CustomUserPrincipal;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Collections")
@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CollectionController {

    private final CollectionApplicationService collectionApplicationService;

    @GetMapping
    public ResponseEntity<List<CollectionResponse>> list(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(collectionApplicationService.list(principal.getId()).stream()
                .map(CollectionResponse::from)
                .toList());
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> create(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateCollectionRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(CollectionResponse.from(
                collectionApplicationService.create(principal.getId(), request.name(), request.documentIds())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CollectionResponse> update(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCollectionRequest request) {

        return ResponseEntity.ok(CollectionResponse.from(
                collectionApplicationService.update(principal.getId(), id, request.name(), request.documentIds())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        collectionApplicationService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
