package com.memos.web.collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.memos.application.collection.CollectionApplicationService;
import com.memos.domain.collection.Collection;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.infrastructure.security.JwtService;
import com.memos.infrastructure.security.UserDetailsServiceImpl;

@WebMvcTest(value = CollectionController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
class CollectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollectionApplicationService collectionApplicationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private final UUID userId = UUID.randomUUID();

    @Test
    void listReturnsCollectionsWithDocumentIds() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(collectionApplicationService.list(userId)).thenReturn(List.of(collection("HR policies", documentId)));

        mockMvc.perform(get("/api/v1/collections").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("HR policies"))
                .andExpect(jsonPath("$[0].documentIds[0]").value(documentId.toString()));
    }

    @Test
    void createReturnsCreated() throws Exception {
        when(collectionApplicationService.create(eq(userId), eq("Project X"), any()))
                .thenReturn(collection("Project X", UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/collections")
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Project X\",\"documentIds\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Project X"));
    }

    @Test
    void createRejectsBlankNameAndReportsDuplicates() throws Exception {
        mockMvc.perform(post("/api/v1/collections")
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());

        when(collectionApplicationService.create(eq(userId), eq("HR"), any()))
                .thenThrow(new DuplicateResourceException("You already have a collection named \"HR\"."));
        mockMvc.perform(post("/api/v1/collections")
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HR\"}"))
                .andExpect(status().isConflict());
    }

    private Collection collection(String name, UUID documentId) {
        return Collection.builder()
                .id(UUID.randomUUID())
                .ownerId(userId)
                .name(name)
                .documentIds(Set.of(documentId))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private RequestPostProcessor asUser() {
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
}
