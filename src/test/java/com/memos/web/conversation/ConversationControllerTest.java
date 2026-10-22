package com.memos.web.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.memos.application.conversation.ConversationApplicationService;
import com.memos.application.rag.RagQueryService;
import com.memos.domain.ai.AiProvider;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.conversation.Citation;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.Message;
import com.memos.domain.conversation.MessageRole;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.infrastructure.security.JwtService;
import com.memos.infrastructure.security.UserDetailsServiceImpl;

@WebMvcTest(value = ConversationController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationApplicationService conversationApplicationService;

    @MockitoBean
    private RagQueryService ragQueryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private final UUID userId = UUID.randomUUID();

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        User user = User.builder()
                .id(userId)
                .email("alice@example.com")
                .passwordHash("hashed")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Conversation conversation(UUID id) {
        Instant now = Instant.now();
        return Conversation.builder()
                .id(id)
                .userId(userId)
                .title("Test conversation")
                .provider(AiProvider.GEMINI)
                .model("gemini-2.0-flash")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void createReturnsCreatedConversation() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(conversationApplicationService.create(eq(userId), eq("My chat"), eq(AiProvider.GEMINI), any(), any()))
                .thenReturn(conversation(conversationId));

        mockMvc.perform(post("/api/v1/conversations")
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My chat","provider":"GEMINI"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.title").value("Test conversation"));
    }

    @Test
    void listReturnsPagedConversations() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(conversationApplicationService.list(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(conversation(conversationId)), 1, 0, 20));

        mockMvc.perform(get("/api/v1/conversations").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(conversationId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReturnsConversationForOwner() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(conversationApplicationService.get(userId, conversationId)).thenReturn(conversation(conversationId));

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId.toString()));
    }

    @Test
    void getReturnsNotFoundWhenConversationMissing() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(conversationApplicationService.get(userId, conversationId))
                .thenThrow(new ResourceNotFoundException("Conversation", conversationId));

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateScopeChangesDocumentFilter() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Conversation updated = conversation(conversationId).toBuilder()
                .documentScope(List.of(documentId))
                .build();
        when(conversationApplicationService.updateDocumentScope(eq(userId), eq(conversationId), eq(List.of(documentId))))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/conversations/{id}/scope", conversationId)
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIds\":[\"" + documentId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentScope[0]").value(documentId.toString()));
    }

    @Test
    void deleteRemovesOwnedConversation() throws Exception {
        UUID conversationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId).with(asUser()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void listMessagesReturnsPagedMessages() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content("Hello")
                .citations(List.of())
                .createdAt(Instant.now())
                .build();

        when(conversationApplicationService.listMessages(eq(userId), eq(conversationId), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(message), 1, 0, 20));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(messageId.toString()))
                .andExpect(jsonPath("$.items[0].content").value("Hello"));
    }

    @Test
    void createMessageRunsRagPipelineAndReturnsAssistantReplyWithCitations() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        Citation citation = Citation.builder()
                .documentId(documentId)
                .documentChunkId(chunkId)
                .sourceTitle("intro.md")
                .snippet("MemOS is a RAG assistant.")
                .score(0.92)
                .createdAt(Instant.now())
                .build();

        Message assistantMessage = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content("MemOS is a RAG-based knowledge assistant.")
                .citations(List.of(citation))
                .createdAt(Instant.now())
                .build();

        when(ragQueryService.askQuestion(userId, conversationId, "What is MemOS?")).thenReturn(assistantMessage);

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"What is MemOS?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content").value("MemOS is a RAG-based knowledge assistant."))
                .andExpect(jsonPath("$.citations[0].sourceTitle").value("intro.md"))
                .andExpect(jsonPath("$.citations[0].score").value(0.92));
    }

    @Test
    void createMessageRejectsBlankContent() throws Exception {
        UUID conversationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
