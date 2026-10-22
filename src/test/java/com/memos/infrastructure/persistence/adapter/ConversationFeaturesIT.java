package com.memos.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
import com.memos.domain.common.PageRequest;
import com.memos.domain.conversation.Citation;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.FeedbackEntry;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageFeedback;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.user.Role;
import com.memos.infrastructure.persistence.entity.DocumentEntity;
import com.memos.infrastructure.persistence.entity.UserEntity;
import com.memos.infrastructure.persistence.repository.DocumentJpaRepository;
import com.memos.infrastructure.persistence.repository.UserJpaRepository;

/** Feedback, answer metadata, citation numbers and collections against a real Postgres (V12-V14). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ConversationFeaturesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MessageRepositoryPort messageRepositoryPort;

    @Autowired
    private ConversationRepositoryPort conversationRepositoryPort;

    @Autowired
    private CollectionRepositoryPort collectionRepositoryPort;

    @Autowired
    private DocumentChunkRepositoryPort documentChunkRepositoryPort;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DocumentJpaRepository documentJpaRepository;

    @Test
    void feedbackIsListedWithTheQuestionThatProducedTheAnswer() {
        UUID ownerId = createUser("feedback-owner@example.com");
        UUID otherOwnerId = createUser("feedback-other@example.com");
        UUID conversationId = createConversation(ownerId, null);
        UUID otherConversationId = createConversation(otherOwnerId, null);

        save(conversationId, MessageRole.USER, "first question");
        Message firstAnswer = save(conversationId, MessageRole.ASSISTANT, "first answer");
        save(conversationId, MessageRole.USER, "second question");
        Message secondAnswer = save(conversationId, MessageRole.ASSISTANT, "second answer");
        Message unrated = save(conversationId, MessageRole.ASSISTANT, "never rated");
        save(otherConversationId, MessageRole.USER, "someone else's question");
        Message otherAnswer = save(otherConversationId, MessageRole.ASSISTANT, "someone else's answer");

        Instant now = Instant.now();
        messageRepositoryPort.updateFeedback(firstAnswer.getId(), MessageFeedback.UP, null, now.minusSeconds(60));
        messageRepositoryPort.updateFeedback(secondAnswer.getId(), MessageFeedback.DOWN, "outdated", now);
        messageRepositoryPort.updateFeedback(otherAnswer.getId(), MessageFeedback.DOWN, null, now);

        List<FeedbackEntry> all = messageRepositoryPort
                .findFeedbackByUserId(ownerId, null, PageRequest.of(0, 10)).items();
        assertThat(all).extracting(FeedbackEntry::answer).containsExactly("second answer", "first answer");

        var downOnly = messageRepositoryPort.findFeedbackByUserId(ownerId, MessageFeedback.DOWN, PageRequest.of(0, 10));
        assertThat(downOnly.totalElements()).isEqualTo(1);
        FeedbackEntry entry = downOnly.items().get(0);
        assertThat(entry.question()).isEqualTo("second question");
        assertThat(entry.comment()).isEqualTo("outdated");
        assertThat(entry.feedback()).isEqualTo(MessageFeedback.DOWN);
        assertThat(entry.feedbackAt()).isNotNull();
        assertThat(entry.conversationTitle()).isEqualTo("Chat");

        assertThat(messageRepositoryPort.countFeedbackByUserId(ownerId, MessageFeedback.UP)).isEqualTo(1);
        assertThat(messageRepositoryPort.countFeedbackByUserId(ownerId, MessageFeedback.DOWN)).isEqualTo(1);

        messageRepositoryPort.updateFeedback(secondAnswer.getId(), null, null, null);
        assertThat(messageRepositoryPort.countFeedbackByUserId(ownerId, MessageFeedback.DOWN)).isZero();
        assertThat(messageRepositoryPort.findByIdAndConversationId(unrated.getId(), conversationId))
                .get().extracting(Message::getFeedback).isNull();
    }

    @Test
    void answerMetadataAndCitationNumbersRoundTrip() {
        UUID ownerId = createUser("metadata-owner@example.com");
        UUID conversationId = createConversation(ownerId, null);
        UUID documentId = createDocument(ownerId);
        DocumentChunk chunk = documentChunkRepositoryPort.save(DocumentChunk.builder()
                .documentId(documentId).chunkIndex(0).content("text").embedding(new float[768])
                .tokenCount(1).metadata(Map.of()).createdAt(Instant.now()).build());

        Message saved = messageRepositoryPort.save(Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content("answer [2]")
                .unanswered(false)
                .searchQuery("standalone query")
                .citations(List.of(Citation.builder()
                        .documentId(documentId).documentChunkId(chunk.getId()).sourceTitle("doc")
                        .snippet("text").score(0.9).sourceNumber(2).createdAt(Instant.now()).build()))
                .createdAt(Instant.now())
                .build());

        Message loaded = messageRepositoryPort.findByIdAndConversationId(saved.getId(), conversationId).orElseThrow();
        assertThat(loaded.getSearchQuery()).isEqualTo("standalone query");
        assertThat(loaded.isUnanswered()).isFalse();
        assertThat(loaded.getCitations()).extracting(Citation::getSourceNumber).containsExactly(2);
    }

    @Test
    void collectionsKeepMembershipAndDropDeletedDocuments() {
        UUID ownerId = createUser("collection-owner@example.com");
        UUID kept = createDocument(ownerId);
        UUID removed = createDocument(ownerId);

        Collection collection = collectionRepositoryPort.save(Collection.builder()
                .ownerId(ownerId).name("HR policies").documentIds(Set.of(kept, removed))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        assertThat(collectionRepositoryPort.existsByOwnerIdAndNameIgnoreCase(ownerId, "hr POLICIES")).isTrue();

        documentJpaRepository.deleteById(removed);

        Collection reloaded = collectionRepositoryPort.findByIdAndOwnerId(collection.getId(), ownerId).orElseThrow();
        assertThat(reloaded.getDocumentIds()).containsExactly(kept);
        assertThat(collectionRepositoryPort.findAllByOwnerId(ownerId)).extracting(Collection::getName)
                .containsExactly("HR policies");
    }

    @Test
    void deletingACollectionUnscopesItsConversations() {
        UUID ownerId = createUser("collection-delete-owner@example.com");
        Collection collection = collectionRepositoryPort.save(Collection.builder()
                .ownerId(ownerId).name("Project X").documentIds(Set.of())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        UUID conversationId = createConversation(ownerId, collection.getId());

        collectionRepositoryPort.deleteById(collection.getId());

        Conversation conversation = conversationRepositoryPort.findByIdAndUserId(conversationId, ownerId).orElseThrow();
        assertThat(conversation.getCollectionId()).isNull();
    }

    private Message save(UUID conversationId, MessageRole role, String content) {
        // Distinct timestamps so "question before answer" ordering is deterministic.
        sleepMillis(5);
        return messageRepositoryPort.save(Message.builder()
                .conversationId(conversationId).role(role).content(content).createdAt(Instant.now()).build());
    }

    private UUID createConversation(UUID ownerId, UUID collectionId) {
        return conversationRepositoryPort.save(Conversation.builder()
                .userId(ownerId).title("Chat").provider(AiProvider.GEMINI).documentScope(List.of())
                .collectionId(collectionId).createdAt(Instant.now()).updatedAt(Instant.now()).build()).getId();
    }

    private UUID createUser(String email) {
        Instant now = Instant.now();
        return userJpaRepository.save(UserEntity.builder()
                .email(email).passwordHash("hashed").fullName("Test User").role(Role.USER).enabled(true)
                .createdAt(now).updatedAt(now).build()).getId();
    }

    private UUID createDocument(UUID ownerId) {
        Instant now = Instant.now();
        DocumentEntity document = documentJpaRepository.save(DocumentEntity.builder()
                .ownerId(ownerId).title("Doc").sourceType(DocumentSourceType.MARKDOWN).sourceUri("/tmp/d.md")
                .status(DocumentStatus.COMPLETED).metadata(Map.of()).createdAt(now).updatedAt(now).build());
        return document.getId();
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
