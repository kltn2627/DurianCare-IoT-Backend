package com.duriancare.auth.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.event.KnowledgeNotificationEvent;
import com.duriancare.auth.event.publisher.KnowledgeNotificationPublisher;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KnowledgeArticleServiceTest {

    @Mock
    private KnowledgeArticleRepository articleRepository;
    @Mock
    private KnowledgeImageStorageService imageStorageService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KnowledgeNotificationPublisher notificationPublisher;

    private KnowledgeArticleService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeArticleService(
                articleRepository,
                imageStorageService,
                userRepository,
                notificationPublisher);
    }

    @Test
    void farmerCannotCreateKnowledgeArticle() {
        User farmer = user(UserRole.FARMER);

        assertThatThrownBy(() -> service.save(request(KnowledgeArticleStatus.REVIEW), farmer, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only engineers or admins");
        verify(articleRepository, never()).save(any());
    }

    @Test
    void publicListAlwaysQueriesPublishedArticles() {
        KnowledgeArticle article = article(KnowledgeArticleStatus.PUBLISHED);
        when(articleRepository.searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                eq("%phytophthora%"),
                eq("sâu bệnh"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(article)));

        KnowledgeArticlePageResponse response =
                service.listPublished(" phytophthora ", " Sâu bệnh ", 0, 10, "publishedAt,desc");

        assertThat(response.articles()).hasSize(1);
        assertThat(response.articles().get(0).status()).isEqualTo(KnowledgeArticleStatus.PUBLISHED);
    }

    @Test
    void publicListPassesNullFiltersWhenSearchAndCategoryAreNull() {
        when(articleRepository.searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(emptyPage());

        KnowledgeArticlePageResponse response =
                service.listPublished(null, null, 0, 10, "publishedAt,desc");

        assertThat(response.totalElements()).isZero();
        verify(articleRepository).searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                isNull(),
                isNull(),
                any(Pageable.class));
    }

    @Test
    void publicListPassesNullSearchAndNormalizedCategory() {
        when(articleRepository.searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                isNull(),
                eq("dinh dưỡng"),
                any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listPublished(null, " Dinh dưỡng ", 0, 10, "publishedAt,desc");

        verify(articleRepository).searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                isNull(),
                eq("dinh dưỡng"),
                any(Pageable.class));
    }

    @Test
    void publicListPassesNormalizedSearchAndNullCategory() {
        when(articleRepository.searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                eq("%phytophthora%"),
                isNull(),
                any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listPublished(" Phytophthora ", null, 0, 10, "publishedAt,desc");

        verify(articleRepository).searchByStatus(
                eq(KnowledgeArticleStatus.PUBLISHED),
                eq("%phytophthora%"),
                isNull(),
                any(Pageable.class));
    }

    @Test
    void adminListPassesNullFiltersWhenBothAreNull() {
        User admin = user(UserRole.ADMIN);
        when(articleRepository.searchForAdmin(
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listForAdmin(admin, null, null, null, 0, 10, "updatedAt,desc");

        verify(articleRepository).searchForAdmin(
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class));
    }

    @Test
    void myListPassesNormalizedSearchAndCategory() {
        User engineer = user(UserRole.ENGINEER);
        when(articleRepository.searchMine(
                eq(engineer.getId()),
                eq(KnowledgeArticleStatus.REVIEW),
                eq("%nấm%"),
                eq("sâu bệnh"),
                any(Pageable.class)))
                .thenReturn(emptyPage());

        service.listMine(engineer, KnowledgeArticleStatus.REVIEW, " Nấm ", " Sâu bệnh ", 0, 10, "updatedAt,desc");

        verify(articleRepository).searchMine(
                eq(engineer.getId()),
                eq(KnowledgeArticleStatus.REVIEW),
                eq("%nấm%"),
                eq("sâu bệnh"),
                any(Pageable.class));
    }

    @Test
    void categoryOptionsDoNotDependOnPublishedArticles() {
        List<KnowledgeCategoryOptionResponse> options = service.listCategoryOptions();

        assertThat(options)
                .extracting(KnowledgeCategoryOptionResponse::label)
                .contains("Sâu bệnh", "Dinh dưỡng", "Kỹ thuật canh tác");
        verify(articleRepository, never()).countByStatusGroupByCategory(any());
    }

    @Test
    void engineerRequestingPublishedIsSubmittedForReview() {
        User engineer = user(UserRole.ENGINEER);
        when(articleRepository.existsBySlug(any())).thenReturn(false);
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByRoleInAndStatus(List.of(UserRole.ADMIN), UserStatus.ACTIVE))
                .thenReturn(List.of());

        KnowledgeArticleResponse response =
                service.save(request(KnowledgeArticleStatus.PUBLISHED), engineer, null);

        assertThat(response.status()).isEqualTo(KnowledgeArticleStatus.REVIEW);
        assertThat(response.authorRole()).isEqualTo(UserRole.ENGINEER);
        assertThat(response.submittedAt()).isNotNull();
    }

    @Test
    void engineerSubmitReviewPublishesNotificationForActiveAdmins() {
        User engineer = user(UserRole.ENGINEER);
        User admin = user(UserRole.ADMIN);
        when(articleRepository.existsBySlug(any())).thenReturn(false);
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> {
            KnowledgeArticle article = invocation.getArgument(0, KnowledgeArticle.class);
            ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
            return article;
        });
        when(userRepository.findByRoleInAndStatus(List.of(UserRole.ADMIN), UserStatus.ACTIVE))
                .thenReturn(List.of(admin));

        service.save(request(KnowledgeArticleStatus.REVIEW), engineer, null);

        ArgumentCaptor<KnowledgeNotificationEvent> captor =
                ArgumentCaptor.forClass(KnowledgeNotificationEvent.class);
        verify(notificationPublisher).publish(captor.capture());
        KnowledgeNotificationEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("KNOWLEDGE_SUBMITTED");
        assertThat(event.receiverId()).isEqualTo(admin.getId().toString());
        assertThat(event.metadata()).containsEntry("targetUrl", "/dashboard/admin/knowledge");
    }

    @Test
    void expertCannotSubmitArticleForReview() {
        User expert = user(UserRole.EXPERT);

        assertThatThrownBy(() -> service.save(request(KnowledgeArticleStatus.REVIEW), expert, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only engineers or admins");
        verify(articleRepository, never()).save(any());
    }

    @Test
    void adminCanPublishDirectly() {
        User admin = user(UserRole.ADMIN);
        when(articleRepository.existsBySlug(any())).thenReturn(false);
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeArticleResponse response =
                service.save(request(KnowledgeArticleStatus.PUBLISHED), admin, null);

        assertThat(response.status()).isEqualTo(KnowledgeArticleStatus.PUBLISHED);
        assertThat(response.reviewedBy()).isEqualTo(admin.getId());
        assertThat(response.publishedAt()).isNotBlank();
    }

    @Test
    void adminApproveReviewPublishesWithReviewMetadata() {
        User admin = user(UserRole.ADMIN);
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = article(KnowledgeArticleStatus.REVIEW);
        ReflectionTestUtils.setField(article, "id", articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        KnowledgeArticleResponse response = service.approve(articleId, admin);

        assertThat(response.status()).isEqualTo(KnowledgeArticleStatus.PUBLISHED);
        assertThat(response.reviewedBy()).isEqualTo(admin.getId());
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.publishedAt()).isNotBlank();
    }

    @Test
    void adminApproveReviewPublishesNotificationForAuthor() {
        User admin = user(UserRole.ADMIN);
        User engineer = user(UserRole.ENGINEER);
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = article(KnowledgeArticleStatus.REVIEW);
        ReflectionTestUtils.setField(article, "id", articleId);
        article.setAuthor(engineer);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        service.approve(articleId, admin);

        ArgumentCaptor<KnowledgeNotificationEvent> captor =
                ArgumentCaptor.forClass(KnowledgeNotificationEvent.class);
        verify(notificationPublisher).publish(captor.capture());
        KnowledgeNotificationEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("KNOWLEDGE_APPROVED");
        assertThat(event.receiverId()).isEqualTo(engineer.getId().toString());
        assertThat(event.metadata()).containsEntry("targetUrl", "/dashboard/client/knowledge/benh-chay-la");
    }

    @Test
    void adminRejectReviewStoresReasonAndReviewMetadata() {
        User admin = user(UserRole.ADMIN);
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = article(KnowledgeArticleStatus.REVIEW);
        ReflectionTestUtils.setField(article, "id", articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        KnowledgeArticleResponse response = service.reject(articleId, "Cần bổ sung nguồn kỹ thuật.", admin);

        assertThat(response.status()).isEqualTo(KnowledgeArticleStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Cần bổ sung nguồn kỹ thuật.");
        assertThat(response.reviewedBy()).isEqualTo(admin.getId());
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(response.publishedAt()).isBlank();
    }

    @Test
    void adminRejectReviewPublishesNotificationForAuthor() {
        User admin = user(UserRole.ADMIN);
        User engineer = user(UserRole.ENGINEER);
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = article(KnowledgeArticleStatus.REVIEW);
        ReflectionTestUtils.setField(article, "id", articleId);
        article.setAuthor(engineer);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        service.reject(articleId, "Cần bổ sung nguồn kỹ thuật.", admin);

        ArgumentCaptor<KnowledgeNotificationEvent> captor =
                ArgumentCaptor.forClass(KnowledgeNotificationEvent.class);
        verify(notificationPublisher).publish(captor.capture());
        KnowledgeNotificationEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("KNOWLEDGE_REJECTED");
        assertThat(event.receiverId()).isEqualTo(engineer.getId().toString());
        assertThat(event.metadata()).containsEntry("targetUrl", "/dashboard/engineer/knowledge/my-articles");
    }

    @Test
    void rejectRequiresReason() {
        User admin = user(UserRole.ADMIN);
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = article(KnowledgeArticleStatus.REVIEW);
        ReflectionTestUtils.setField(article, "id", articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        assertThatThrownBy(() -> service.reject(articleId, " ", admin))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    @Test
    void nonAdminCannotApprove() {
        User engineer = user(UserRole.ENGINEER);

        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), engineer))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator permission");
    }

    @Test
    void farmerCannotApprove() {
        User farmer = user(UserRole.FARMER);

        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), farmer))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator permission");
    }

    @Test
    void engineerCannotReject() {
        User engineer = user(UserRole.ENGINEER);

        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "Không đạt", engineer))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Administrator permission");
    }

    @Test
    void publishedArticleCannotBeApprovedAgain() {
        User admin = user(UserRole.ADMIN);
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = article(KnowledgeArticleStatus.PUBLISHED);
        ReflectionTestUtils.setField(article, "id", articleId);
        when(articleRepository.findById(articleId)).thenReturn(Optional.of(article));

        assertThatThrownBy(() -> service.approve(articleId, admin))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only articles waiting for review");
    }

    @Test
    void getPublishedArticleIncrementsViewCount() {
        KnowledgeArticle article = article(KnowledgeArticleStatus.PUBLISHED);
        article.setViews(4);
        when(articleRepository.findBySlugAndStatus("benh-chay-la", KnowledgeArticleStatus.PUBLISHED))
                .thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        KnowledgeArticleResponse response = service.getPublishedBySlug("benh-chay-la");

        assertThat(response.views()).isEqualTo(5);
        verify(articleRepository).save(article);
    }

    private KnowledgeArticleRequest request(KnowledgeArticleStatus status) {
        return new KnowledgeArticleRequest(
                "Nhận biết bệnh cháy lá",
                "Sâu bệnh",
                null,
                "Tóm tắt cách nhận biết và phòng trị bệnh cháy lá.",
                "Nội dung hướng dẫn kỹ thuật phòng trị bệnh cháy lá cho cây sầu riêng.",
                status,
                false,
                List.of("Cháy lá"));
    }

    private KnowledgeArticle article(KnowledgeArticleStatus status) {
        KnowledgeArticle article = new KnowledgeArticle(
                "Nhận biết bệnh cháy lá",
                "benh-chay-la",
                "Sâu bệnh",
                "Kỹ sư DurianCare",
                "Tóm tắt cách nhận biết và phòng trị bệnh cháy lá.",
                "Nội dung hướng dẫn kỹ thuật phòng trị bệnh cháy lá cho cây sầu riêng.");
        ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
        article.setStatus(status);
        article.setReadingTime("1 phút đọc");
        return article;
    }

    private PageImpl<KnowledgeArticle> emptyPage() {
        return new PageImpl<>(List.of());
    }

    private User user(UserRole role) {
        User user = new User(role.name().toLowerCase() + "@example.com", "hash", UserStatus.ACTIVE, role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
