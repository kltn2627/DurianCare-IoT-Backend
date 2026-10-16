package com.duriancare.auth.knowledge;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.event.KnowledgeNotificationEvent;
import com.duriancare.auth.event.publisher.KnowledgeNotificationPublisher;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.UserRepository;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeArticleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeArticleService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<KnowledgeCategoryOptionResponse> CATEGORY_OPTIONS = List.of(
            new KnowledgeCategoryOptionResponse("Dinh dưỡng", "Dinh dưỡng"),
            new KnowledgeCategoryOptionResponse("Sâu bệnh", "Sâu bệnh"),
            new KnowledgeCategoryOptionResponse("Kỹ thuật canh tác", "Kỹ thuật canh tác"),
            new KnowledgeCategoryOptionResponse("Kỹ thuật bón phân nghịch vụ", "Kỹ thuật bón phân nghịch vụ"),
            new KnowledgeCategoryOptionResponse("Kỹ thuật cắt tỉa", "Kỹ thuật cắt tỉa"));

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeImageStorageService imageStorageService;
    private final UserRepository userRepository;
    private final KnowledgeNotificationPublisher notificationPublisher;

    public KnowledgeArticleService(
            KnowledgeArticleRepository articleRepository,
            KnowledgeImageStorageService imageStorageService,
            UserRepository userRepository,
            KnowledgeNotificationPublisher notificationPublisher) {
        this.articleRepository = articleRepository;
        this.imageStorageService = imageStorageService;
        this.userRepository = userRepository;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listPublished(String search, String category, int page, int size, String sort) {
        Page<KnowledgeArticle> result = articleRepository.searchByStatus(
                KnowledgeArticleStatus.PUBLISHED,
                searchPattern(search),
                normalizedCategory(category),
                pageable(page, size, sort, Sort.by(Sort.Direction.DESC, "publishedAt")));
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listForAdmin(
            User actor,
            KnowledgeArticleStatus status,
            String search,
            String category,
            int page,
            int size,
            String sort) {
        validateAdmin(actor);
        Page<KnowledgeArticle> result = articleRepository.searchForAdmin(
                status,
                searchPattern(search),
                normalizedCategory(category),
                pageable(page, size, sort, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listRecent(int size) {
        Page<KnowledgeArticle> result = articleRepository.searchByStatus(
                KnowledgeArticleStatus.PUBLISHED,
                null,
                null,
                pageable(0, size, "publishedAt,desc", Sort.by(Sort.Direction.DESC, "publishedAt")));
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listPopular(int size) {
        Page<KnowledgeArticle> result = articleRepository.searchByStatus(
                KnowledgeArticleStatus.PUBLISHED,
                null,
                null,
                pageable(0, size, "views,desc", Sort.by(Sort.Direction.DESC, "views")));
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listFeatured(int size) {
        Page<KnowledgeArticle> result = articleRepository.findFeaturedByStatus(
                KnowledgeArticleStatus.PUBLISHED,
                pageable(0, size, "publishedAt,desc", Sort.by(Sort.Direction.DESC, "publishedAt")));
        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeCategoryCountResponse> listPublishedCategoryCounts() {
        return articleRepository.countByStatusGroupByCategory(KnowledgeArticleStatus.PUBLISHED).stream()
                .map(row -> new KnowledgeCategoryCountResponse((String) row[0], (Long) row[1]))
                .toList();
    }

    public List<KnowledgeCategoryOptionResponse> listCategoryOptions() {
        return CATEGORY_OPTIONS;
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listRelated(String slug, int size) {
        KnowledgeArticle article = articleRepository.findBySlugAndStatus(slug, KnowledgeArticleStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published knowledge article was not found"));
        Page<KnowledgeArticle> result = articleRepository.findRelatedByStatusAndCategory(
                KnowledgeArticleStatus.PUBLISHED,
                normalizedCategory(article.getCategory()),
                article.getId(),
                pageable(0, size, "publishedAt,desc", Sort.by(Sort.Direction.DESC, "publishedAt")));
        return toPageResponse(result);
    }

    private KnowledgeArticlePageResponse toPageResponse(Page<KnowledgeArticle> result) {
        return new KnowledgeArticlePageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize());
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listMine(
            User actor,
            KnowledgeArticleStatus status,
            String search,
            String category,
            int page,
            int size,
            String sort) {
        validateWriter(actor);
        Page<KnowledgeArticle> result = articleRepository.searchMine(
                actor.getId(),
                status,
                searchPattern(search),
                normalizedCategory(category),
                pageable(page, size, sort, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return toPageResponse(result);
    }

    @Transactional
    public KnowledgeArticleResponse getPublishedBySlug(String slug) {
        KnowledgeArticle article = articleRepository.findBySlugAndStatus(slug, KnowledgeArticleStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published knowledge article was not found"));
        article.setViews(article.getViews() + 1);
        return toResponse(articleRepository.save(article));
    }

    @Transactional
    public KnowledgeArticleResponse save(KnowledgeArticleRequest request, User actor, UUID id) {
        validateWriter(actor);
        boolean newArticle = id == null;
        KnowledgeArticle article = id == null
                ? new KnowledgeArticle(
                        clean(request.title()),
                        uniqueSlug(request.title(), null),
                        clean(request.category()),
                        resolveAuthorName(request, actor),
                        clean(request.excerpt()),
                        clean(request.content()))
                : articleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Knowledge article was not found"));
        if (id != null) {
            validateCanEdit(actor, article);
            article.setTitle(clean(request.title()));
            article.setCategory(clean(request.category()));
            article.setAuthorName(resolveAuthorName(request, actor));
            article.setExcerpt(clean(request.excerpt()));
            article.setContent(clean(request.content()));
        }
        article.setFeatured(actor.getRole() == UserRole.ADMIN && request.featured());
        article.setTags(toTagString(request.tags()));
        article.setReadingTime(readingTime(article.getContent()));
        article.setAuthor(article.getAuthor() == null ? actor : article.getAuthor());
        article.setAuthorRole(article.getAuthorRole() == null ? actor.getRole() : article.getAuthorRole());
        KnowledgeArticleStatus previousStatus = newArticle ? null : article.getStatus();
        KnowledgeArticleStatus nextStatus = normalizeRequestedStatus(request.status(), actor);
        applyStatus(article, nextStatus, actor);
        KnowledgeArticle saved = articleRepository.save(article);
        if (actor.getRole() == UserRole.ENGINEER
                && saved.getStatus() == KnowledgeArticleStatus.REVIEW
                && previousStatus != KnowledgeArticleStatus.REVIEW) {
            publishSubmittedNotification(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public KnowledgeArticleResponse approve(UUID id, User admin) {
        validateAdmin(admin);
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article was not found"));
        validateReviewStatus(article);
        applyStatus(article, KnowledgeArticleStatus.PUBLISHED, admin);
        KnowledgeArticle saved = articleRepository.save(article);
        publishReviewedNotification(saved, "KNOWLEDGE_APPROVED", "Bài kiến thức đã được duyệt",
                "Bài \"%s\" đã được duyệt và hiển thị trong thư viện kiến thức.");
        return toResponse(saved);
    }

    @Transactional
    public KnowledgeArticleResponse reject(UUID id, String reason, User admin) {
        validateAdmin(admin);
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article was not found"));
        validateReviewStatus(article);
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Rejection reason is required");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        article.setStatus(KnowledgeArticleStatus.REJECTED);
        article.setReviewedAt(now);
        article.setReviewedBy(admin);
        article.setRejectionReason(reason.trim());
        KnowledgeArticle saved = articleRepository.save(article);
        publishReviewedNotification(saved, "KNOWLEDGE_REJECTED", "Bài kiến thức bị từ chối",
                "Bài \"%s\" cần chỉnh sửa trước khi gửi duyệt lại.");
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id, User admin) {
        validateAdmin(admin);
        if (!articleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Knowledge article was not found");
        }
        articleRepository.deleteById(id);
    }

    @Transactional
    public KnowledgeArticleResponse uploadCover(UUID id, MultipartFile image, User actor) {
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article was not found"));
        validateCanEdit(actor, article);
        StoredKnowledgeImage stored = imageStorageService.upload(article.getId(), image);
        article.setCoverImage(stored.imageUrl(), stored.objectKey(), stored.contentType());
        return toResponse(articleRepository.save(article));
    }

    @Transactional(readOnly = true)
    public KnowledgeImageResource loadCover(UUID id) {
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article was not found"));
        return imageStorageService.load(article.getCoverImageKey(), article.getCoverImageContentType());
    }

    private void applyStatus(KnowledgeArticle article, KnowledgeArticleStatus status, User actor) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (status == KnowledgeArticleStatus.PUBLISHED) {
            validateAdmin(actor);
            article.setStatus(KnowledgeArticleStatus.PUBLISHED);
            article.setPublishedAt(article.getPublishedAt() == null ? now : article.getPublishedAt());
            article.setReviewedAt(now);
            article.setReviewedBy(actor);
            article.setRejectionReason(null);
        } else if (status == KnowledgeArticleStatus.DRAFT) {
            article.setStatus(KnowledgeArticleStatus.DRAFT);
        } else {
            article.setStatus(KnowledgeArticleStatus.REVIEW);
            article.setSubmittedAt(now);
            article.setReviewedAt(null);
            article.setReviewedBy(null);
            article.setRejectionReason(null);
        }
    }

    private KnowledgeArticleStatus normalizeRequestedStatus(KnowledgeArticleStatus status, User actor) {
        if (status == KnowledgeArticleStatus.PUBLISHED) {
            return actor.getRole() == UserRole.ADMIN ? KnowledgeArticleStatus.PUBLISHED : KnowledgeArticleStatus.REVIEW;
        }
        if (status == KnowledgeArticleStatus.DRAFT) {
            return KnowledgeArticleStatus.DRAFT;
        }
        return KnowledgeArticleStatus.REVIEW;
    }

    private void validateWriter(User actor) {
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.ENGINEER) {
            throw new AccessDeniedException("Only engineers or admins can submit knowledge articles");
        }
    }

    private void validateCanEdit(User actor, KnowledgeArticle article) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (article.getAuthor() != null && article.getAuthor().getId().equals(actor.getId())
                && article.getStatus() != KnowledgeArticleStatus.PUBLISHED) {
            return;
        }
        throw new AccessDeniedException("You can only edit your own unpublished knowledge articles");
    }

    private void validateAdmin(User actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator permission is required");
        }
    }

    private void validateReviewStatus(KnowledgeArticle article) {
        if (article.getStatus() != KnowledgeArticleStatus.REVIEW) {
            throw new InvalidRequestException("Only articles waiting for review can be approved or rejected");
        }
    }

    public User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
    }

    private void publishSubmittedNotification(KnowledgeArticle article) {
        List<User> admins = userRepository.findByRoleInAndStatus(List.of(UserRole.ADMIN), UserStatus.ACTIVE);
        if (admins == null) {
            return;
        }
        admins.forEach(admin -> publishNotification(new KnowledgeNotificationEvent(
                UUID.randomUUID(),
                "KNOWLEDGE_SUBMITTED",
                admin.getId().toString(),
                "Có bài kiến thức chờ duyệt",
                "Kỹ sư đã gửi bài \"" + article.getTitle() + "\" để admin xem xét.",
                "GENERAL",
                knowledgeMetadata(article, "/dashboard/admin/knowledge"),
                Instant.now())));
    }

    private void publishReviewedNotification(
            KnowledgeArticle article,
            String eventType,
            String title,
            String messageTemplate) {
        if (article.getAuthor() == null || article.getAuthor().getId() == null) {
            return;
        }
        String targetUrl = "KNOWLEDGE_APPROVED".equals(eventType)
                ? "/dashboard/client/knowledge/" + article.getSlug()
                : "/dashboard/engineer/knowledge/my-articles";
        publishNotification(new KnowledgeNotificationEvent(
                UUID.randomUUID(),
                eventType,
                article.getAuthor().getId().toString(),
                title,
                messageTemplate.formatted(article.getTitle()),
                "GENERAL",
                knowledgeMetadata(article, targetUrl),
                Instant.now()));
    }

    private void publishNotification(KnowledgeNotificationEvent event) {
        try {
            notificationPublisher.publish(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to prepare knowledge notification {}", event.eventId(), exception);
        }
    }

    private Map<String, Object> knowledgeMetadata(KnowledgeArticle article, String targetUrl) {
        return Map.of(
                "articleId", article.getId() == null ? "" : article.getId().toString(),
                "slug", article.getSlug(),
                "status", article.getStatus().name(),
                "targetUrl", targetUrl);
    }

    private KnowledgeArticleResponse toResponse(KnowledgeArticle article) {
        return new KnowledgeArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getCategory(),
                article.getAuthorName(),
                article.getAuthor() == null ? null : article.getAuthor().getId(),
                article.getAuthorRole(),
                article.getExcerpt(),
                article.getContent(),
                article.getStatus(),
                article.isFeatured(),
                article.getCoverImageUrl(),
                article.getReadingTime(),
                parseTags(article.getTags()),
                formatDate(article.getPublishedAt()),
                formatDate(article.getUpdatedAt()),
                article.getSubmittedAt(),
                article.getReviewedAt(),
                article.getReviewedBy() == null ? null : article.getReviewedBy().getId(),
                article.getRejectionReason(),
                article.getViews());
    }

    private String resolveAuthorName(KnowledgeArticleRequest request, User actor) {
        if (request.author() != null && !request.author().isBlank()) {
            return clean(request.author());
        }
        return actor.getProfile() == null ? actor.getEmail() : actor.getProfile().getFullName();
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Knowledge article fields must not be blank");
        }
        return value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() || "Tất cả".equalsIgnoreCase(value.trim())
                ? null
                : value.trim();
    }

    private String normalizedCategory(String value) {
        String cleaned = cleanOptional(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private String searchPattern(String value) {
        String cleaned = cleanOptional(value);
        return cleaned == null ? null : "%" + cleaned.toLowerCase(Locale.ROOT) + "%";
    }

    private PageRequest pageable(int page, int size, String requestedSort, Sort fallbackSort) {
        return PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                resolveSort(requestedSort, fallbackSort));
    }

    private Sort resolveSort(String requestedSort, Sort fallbackSort) {
        if (requestedSort == null || requestedSort.isBlank()) {
            return fallbackSort;
        }
        String[] parts = requestedSort.split(",", 2);
        String property = switch (parts[0].trim()) {
            case "publishedAt", "published_at" -> "publishedAt";
            case "updatedAt", "updated_at" -> "updatedAt";
            case "createdAt", "created_at" -> "createdAt";
            case "views", "viewCount", "view_count" -> "views";
            case "title" -> "title";
            default -> fallbackSort.iterator().next().getProperty();
        };
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private String readingTime(String content) {
        int words = content == null || content.isBlank() ? 0 : content.trim().split("\\s+").length;
        return Math.max(1, (int) Math.ceil(words / 180.0)) + " phút đọc";
    }

    private String uniqueSlug(String title, UUID currentId) {
        String base = slugify(title);
        String slug = base;
        int suffix = 2;
        while (articleRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private String slugify(String title) {
        String normalized = Normalizer.normalize(clean(title), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d")
                .replace("Đ", "d")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "bai-viet-" + System.currentTimeMillis() : normalized;
    }

    private String toTagString(List<String> tags) {
        if (tags == null) {
            return "";
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .limit(12)
                .collect(Collectors.joining(","));
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : DATE_FORMATTER.format(value);
    }
}
