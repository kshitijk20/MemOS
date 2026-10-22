package com.memos.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "citations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private MessageEntity message;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_chunk_id", nullable = false)
    private UUID documentChunkId;

    @Column(name = "source_title", nullable = false, length = 500)
    private String sourceTitle;

    @Column(nullable = false, columnDefinition = "text")
    private String snippet;

    @Column(nullable = false)
    private double score;

    @Column(name = "source_number")
    private Integer sourceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
