package com.duriancare.auth.community;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/community")
public class CommunityPostController {

    private final CommunityPostService postService;

    public CommunityPostController(CommunityPostService postService) {
        this.postService = postService;
    }

    @GetMapping("/posts")
    CommunityPageResponse<CommunityPostResponse> feed(
            Principal principal,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.feed(topic, query, page, size, actor(principal));
    }

    @GetMapping("/posts/mine")
    CommunityPageResponse<CommunityPostResponse> mine(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.myPosts(page, size, actor(principal));
    }

    @GetMapping("/posts/{postId}")
    CommunityPostResponse detail(Principal principal, @PathVariable UUID postId) {
        return postService.detail(postId, actor(principal));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CommunityPostResponse create(
            Principal principal,
            @RequestParam String content,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) CommunityPostVisibility visibility,
            @RequestPart(value = "media", required = false) List<MultipartFile> media) {
        return postService.create(content, topic, visibility, media, actor(principal));
    }

    @PostMapping("/posts/{postId}/reaction")
    CommunityPostResponse react(
            Principal principal,
            @PathVariable UUID postId,
            @RequestParam(required = false) CommunityReactionType type) {
        return postService.react(postId, type, actor(principal));
    }

    @PostMapping("/posts/{postId}/comments")
    CommunityPostResponse comment(
            Principal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody CommunityCommentRequest request) {
        return postService.addComment(postId, request, actor(principal));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    CommunityPostResponse deleteComment(
            Principal principal,
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        return postService.deleteComment(postId, commentId, actor(principal));
    }

    @PostMapping("/posts/{postId}/report")
    CommunityPostResponse report(Principal principal, @PathVariable UUID postId) {
        return postService.report(postId, actor(principal));
    }

    @DeleteMapping("/posts/{postId}")
    ResponseEntity<Void> delete(Principal principal, @PathVariable UUID postId) {
        postService.delete(postId, actor(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/posts")
    CommunityPageResponse<CommunityPostResponse> adminPosts(
            Principal principal,
            @RequestParam(required = false) CommunityPostStatus status,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.adminPosts(status, topic, query, page, size, actor(principal));
    }

    @GetMapping("/media/{mediaId}")
    ResponseEntity<Resource> media(@PathVariable UUID mediaId) {
        CommunityMediaResource media = postService.loadMedia(mediaId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(media.contentType()))
                .body(media.resource());
    }

    private User actor(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return postService.loadUser(authenticatedUser.userId());
        }
        throw new InvalidTokenException("Authenticated user is required");
    }
}
