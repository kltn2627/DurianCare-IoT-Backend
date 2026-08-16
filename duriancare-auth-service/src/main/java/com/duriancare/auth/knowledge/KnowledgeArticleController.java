package com.duriancare.auth.knowledge;

import com.duriancare.auth.entity.User;
import com.duriancare.auth.exception.InvalidTokenException;
import com.duriancare.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeArticleController {

    private final KnowledgeArticleService articleService;

    public KnowledgeArticleController(KnowledgeArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping("/articles")
    KnowledgeArticlePageResponse listArticles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "publishedAt,desc") String sort) {
        return articleService.listPublished(search, category, page, size, sort);
    }

    @GetMapping("/admin/articles")
    KnowledgeArticlePageResponse listAdminArticles(
            Principal principal,
            @RequestParam(required = false) KnowledgeArticleStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return articleService.listForAdmin(actor(principal), status, search, category, page, size, sort);
    }

    @GetMapping("/articles/mine")
    KnowledgeArticlePageResponse listMyArticles(
            Principal principal,
            @RequestParam(required = false) KnowledgeArticleStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return articleService.listMine(actor(principal), status, search, category, page, size, sort);
    }

    @GetMapping("/articles/recent")
    KnowledgeArticlePageResponse listRecentArticles(@RequestParam(defaultValue = "5") int size) {
        return articleService.listRecent(size);
    }

    @GetMapping("/articles/popular")
    KnowledgeArticlePageResponse listPopularArticles(@RequestParam(defaultValue = "5") int size) {
        return articleService.listPopular(size);
    }

    @GetMapping("/articles/featured")
    KnowledgeArticlePageResponse listFeaturedArticles(@RequestParam(defaultValue = "5") int size) {
        return articleService.listFeatured(size);
    }

    @GetMapping("/categories")
    List<KnowledgeCategoryCountResponse> listCategories() {
        return articleService.listPublishedCategoryCounts();
    }

    @GetMapping("/category-options")
    List<KnowledgeCategoryOptionResponse> listCategoryOptions() {
        return articleService.listCategoryOptions();
    }

    @GetMapping("/articles/{slug}")
    KnowledgeArticleResponse getPublishedArticle(@PathVariable String slug) {
        return articleService.getPublishedBySlug(slug);
    }

    @GetMapping("/articles/{slug}/related")
    KnowledgeArticlePageResponse listRelatedArticles(
            @PathVariable String slug,
            @RequestParam(defaultValue = "4") int size) {
        return articleService.listRelated(slug, size);
    }

    @PostMapping("/articles")
    KnowledgeArticleResponse createArticle(
            Principal principal,
            @Valid @RequestBody KnowledgeArticleRequest request) {
        return articleService.save(request, actor(principal), null);
    }

    @PutMapping("/articles/{id}")
    KnowledgeArticleResponse updateArticle(
            Principal principal,
            @PathVariable UUID id,
            @Valid @RequestBody KnowledgeArticleRequest request) {
        return articleService.save(request, actor(principal), id);
    }

    @PostMapping(value = "/articles/form", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    KnowledgeArticleResponse createArticleForm(
            Principal principal,
            @Valid @ModelAttribute KnowledgeArticleRequest request,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage) {
        KnowledgeArticleResponse article = articleService.save(request, actor(principal), null);
        if (coverImage == null || coverImage.isEmpty()) {
            return article;
        }
        return articleService.uploadCover(article.id(), coverImage, actor(principal));
    }

    @PostMapping(value = "/articles/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    KnowledgeArticleResponse uploadCover(
            Principal principal,
            @PathVariable UUID id,
            @RequestPart("coverImage") MultipartFile coverImage) {
        return articleService.uploadCover(id, coverImage, actor(principal));
    }

    @PostMapping("/articles/{id}/approve")
    KnowledgeArticleResponse approveArticle(Principal principal, @PathVariable UUID id) {
        return articleService.approve(id, actor(principal));
    }

    @PostMapping("/articles/{id}/reject")
    KnowledgeArticleResponse rejectArticle(
            Principal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) RejectKnowledgeArticleRequest request) {
        return articleService.reject(id, request == null ? null : request.reason(), actor(principal));
    }

    @DeleteMapping("/articles/{id}")
    ResponseEntity<Void> deleteArticle(Principal principal, @PathVariable UUID id) {
        articleService.delete(id, actor(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/images/{id}")
    ResponseEntity<Resource> loadCover(@PathVariable UUID id) {
        KnowledgeImageResource image = articleService.loadCover(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.resource());
    }

    private User actor(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return articleService.loadUser(authenticatedUser.userId());
        }
        throw new InvalidTokenException("Authenticated user is required");
    }

    record RejectKnowledgeArticleRequest(String reason) {
    }
}
