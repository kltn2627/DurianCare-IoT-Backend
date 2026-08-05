package com.duriancare.auth.knowledge;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.UserRepository;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeArticleService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeImageStorageService imageStorageService;
    private final UserRepository userRepository;

    public KnowledgeArticleService(
            KnowledgeArticleRepository articleRepository,
            KnowledgeImageStorageService imageStorageService,
            UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.imageStorageService = imageStorageService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse list(KnowledgeArticleStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<KnowledgeArticle> result = status == null
                ? articleRepository.findAll(pageable)
                : articleRepository.findByStatus(status, pageable);
        return new KnowledgeArticlePageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize());
    }

    @Transactional(readOnly = true)
    public KnowledgeArticlePageResponse listMine(User actor, KnowledgeArticleStatus status, int page, int size) {
        validateWriter(actor);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<KnowledgeArticle> result = status == null
                ? articleRepository.findByAuthorId(actor.getId(), pageable)
                : articleRepository.findByAuthorIdAndStatus(actor.getId(), status, pageable);
        return new KnowledgeArticlePageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize());
    }

    @Transactional(readOnly = true)
    public KnowledgeArticleResponse getPublishedBySlug(String slug) {
        return articleRepository.findBySlugAndStatus(slug, KnowledgeArticleStatus.PUBLISHED)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Published knowledge article was not found"));
    }

    @Transactional
    public KnowledgeArticleResponse save(KnowledgeArticleRequest request, User actor, UUID id) {
        validateWriter(actor);
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
        applyStatus(article, normalizeRequestedStatus(request.status(), actor), actor);
        return toResponse(articleRepository.save(article));
    }

    @Transactional
    public KnowledgeArticleResponse approve(UUID id, User admin) {
        validateAdmin(admin);
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article was not found"));
        applyStatus(article, KnowledgeArticleStatus.PUBLISHED, admin);
        return toResponse(articleRepository.save(article));
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
            article.setSubmittedAt(article.getSubmittedAt() == null ? now : article.getSubmittedAt());
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
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.ENGINEER && actor.getRole() != UserRole.FARMER) {
            throw new InvalidRequestException("Only farmers, engineers, or admins can submit knowledge articles");
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
        throw new InvalidRequestException("You can only edit your own unpublished knowledge articles");
    }

    private void validateAdmin(User actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new InvalidRequestException("Administrator permission is required");
        }
    }

    public User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
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
