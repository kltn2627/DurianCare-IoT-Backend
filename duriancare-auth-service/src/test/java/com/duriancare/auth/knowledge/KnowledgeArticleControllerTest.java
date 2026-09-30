package com.duriancare.auth.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.security.JwtService;
import com.duriancare.auth.security.RevokedTokenService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KnowledgeArticleController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.duriancare.auth.exception.GlobalExceptionHandler.class)
class KnowledgeArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeArticleService articleService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private RevokedTokenService revokedTokenService;

    @Test
    void farmerCreateKnowledgeReturnsForbidden() throws Exception {
        UUID farmerId = UUID.randomUUID();
        User farmer = user(farmerId, UserRole.FARMER);
        when(articleService.loadUser(farmerId)).thenReturn(farmer);
        when(articleService.save(any(), eq(farmer), eq(null)))
                .thenThrow(new AccessDeniedException("Only engineers or admins can submit knowledge articles"));

        mockMvc.perform(post("/api/knowledge/articles")
                        .principal(authentication(farmerId, UserRole.FARMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Nhận biết bệnh cháy lá",
                                  "category": "Sâu bệnh",
                                  "excerpt": "Tóm tắt cách nhận biết bệnh.",
                                  "content": "Nội dung hướng dẫn kỹ thuật.",
                                  "status": "REVIEW",
                                  "featured": false,
                                  "tags": ["Cháy lá"]
                                }
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only engineers or admins can submit knowledge articles"));
    }

    @Test
    void publicListIgnoresStatusAndUsesPublishedServicePath() throws Exception {
        when(articleService.listPublished("phytophthora", "Sâu bệnh", 0, 10, "views,desc"))
                .thenReturn(new KnowledgeArticlePageResponse(List.of(), 0, 0, 0, 10));

        mockMvc.perform(get("/api/knowledge/articles")
                        .param("status", "REVIEW")
                        .param("search", "phytophthora")
                        .param("category", "Sâu bệnh")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "views,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(articleService).listPublished("phytophthora", "Sâu bệnh", 0, 10, "views,desc");
    }

    @Test
    void categoryOptionsAreCanonicalAndIndependentFromPublishedCounts() throws Exception {
        when(articleService.listCategoryOptions())
                .thenReturn(List.of(
                        new KnowledgeCategoryOptionResponse("Sâu bệnh", "Sâu bệnh"),
                        new KnowledgeCategoryOptionResponse("Dinh dưỡng", "Dinh dưỡng")));

        mockMvc.perform(get("/api/knowledge/category-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("Sâu bệnh"))
                .andExpect(jsonPath("$[0].label").value("Sâu bệnh"));

        verify(articleService).listCategoryOptions();
    }

    private User user(UUID id, UserRole role) {
        User user = new User(role.name().toLowerCase() + "@example.com", "hash", UserStatus.ACTIVE, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UsernamePasswordAuthenticationToken authentication(UUID userId, UserRole role) {
        return new UsernamePasswordAuthenticationToken(
                new com.duriancare.auth.security.AuthenticatedUser(
                        userId,
                        role.name().toLowerCase() + "@example.com",
                        role,
                        "token-jti"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
