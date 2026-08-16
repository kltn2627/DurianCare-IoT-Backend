package com.duriancare.auth.community;

import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserProfile;
import com.duriancare.auth.exception.InvalidRequestException;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityMediaRepository mediaRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityReactionRepository reactionRepository;
    private final CommunityMediaStorageService mediaStorageService;
    private final UserRepository userRepository;

    public CommunityPostService(
            CommunityPostRepository postRepository,
            CommunityMediaRepository mediaRepository,
            CommunityCommentRepository commentRepository,
            CommunityReactionRepository reactionRepository,
            CommunityMediaStorageService mediaStorageService,
            UserRepository userRepository) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.mediaStorageService = mediaStorageService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public CommunityPageResponse<CommunityPostResponse> feed(String topic, String query, int page, int size, User actor) {
        Page<CommunityPost> result = postRepository.feed(cleanFilter(topic), cleanFilter(query), pageRequest(page, size));
        return pageResponse(result, actor, false);
    }

    @Transactional(readOnly = true)
    public CommunityPageResponse<CommunityPostResponse> myPosts(int page, int size, User actor) {
        Page<CommunityPost> result = postRepository.findVisibleByAuthorId(actor.getId(), pageRequest(page, size));
        return pageResponse(result, actor, false);
    }

    @Transactional(readOnly = true)
    public CommunityPageResponse<CommunityPostResponse> adminPosts(
            CommunityPostStatus status,
            String topic,
            String query,
            int page,
            int size,
            User actor) {
        validateAdmin(actor);
        Page<CommunityPost> result = postRepository.adminList(status, cleanFilter(topic), cleanFilter(query), pageRequest(page, size));
        return pageResponse(result, actor, false);
    }

    @Transactional(readOnly = true)
    public CommunityPostResponse detail(UUID postId, User actor) {
        CommunityPost post = post(postId);
        return toResponse(post, actor, true);
    }

    @Transactional
    public CommunityPostResponse create(
            String content,
            String topic,
            CommunityPostVisibility visibility,
            List<MultipartFile> files,
            User actor) {
        validateWriter(actor);
        String cleanedContent = cleanContent(content);
        CommunityPost post = new CommunityPost(
                actor,
                StringUtils.hasText(topic) ? topic.trim() : "Kỹ thuật trồng",
                cleanedContent,
                visibility == null ? CommunityPostVisibility.PUBLIC : visibility);
        int index = 0;
        for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (index >= 6) {
                throw new InvalidRequestException("A community post can contain up to 6 media files");
            }
            UUID mediaId = UUID.randomUUID();
            StoredCommunityMedia stored = mediaStorageService.upload(mediaId, file);
            post.addMedia(new CommunityMedia(
                    mediaId,
                    stored.mediaType(),
                    stored.url(),
                    stored.objectKey(),
                    stored.contentType(),
                    index++));
        }
        return toResponse(postRepository.save(post), actor, true);
    }

    @Transactional
    public CommunityPostResponse react(UUID postId, CommunityReactionType reactionType, User actor) {
        CommunityPost post = post(postId);
        if (reactionType == null) {
            reactionRepository.findByPostIdAndUserId(postId, actor.getId()).ifPresent(reactionRepository::delete);
        } else {
            CommunityReaction reaction = reactionRepository.findByPostIdAndUserId(postId, actor.getId())
                    .orElseGet(() -> new CommunityReaction(post, actor, reactionType));
            reaction.setReactionType(reactionType);
            reactionRepository.save(reaction);
        }
        post.setReactionCount(reactionRepository.countByPostId(postId));
        return toResponse(postRepository.save(post), actor, false);
    }

    @Transactional
    public CommunityPostResponse addComment(UUID postId, CommunityCommentRequest request, User actor) {
        CommunityPost post = post(postId);
        CommunityComment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment was not found"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new InvalidRequestException("Parent comment does not belong to this post");
            }
            if (parent.getParent() != null) {
                parent = parent.getParent();
            }
        }
        CommunityComment comment = new CommunityComment(post, actor, parent, cleanComment(request.content()));
        commentRepository.save(comment);
        post.setCommentCount(post.getCommentCount() + 1);
        return toResponse(postRepository.save(post), actor, true);
    }

    @Transactional
    public CommunityPostResponse deleteComment(UUID postId, UUID commentId, User actor) {
        CommunityPost post = post(postId);
        CommunityComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment was not found"));
        if (!comment.getPost().getId().equals(postId)) {
            throw new ResourceNotFoundException("Comment was not found");
        }
        boolean canDelete = actor.getRole() == UserRole.ADMIN
                || post.getAuthor().getId().equals(actor.getId())
                || comment.getAuthor().getId().equals(actor.getId());
        if (!canDelete) {
            throw new InvalidRequestException("You do not have permission to delete this comment");
        }
        long removed = 1 + commentRepository.countByParentId(commentId);
        commentRepository.delete(comment);
        post.setCommentCount((int) Math.max(0, post.getCommentCount() - removed));
        return toResponse(postRepository.save(post), actor, true);
    }

    @Transactional
    public CommunityPostResponse report(UUID postId, User actor) {
        CommunityPost post = post(postId);
        if (!post.getAuthor().getId().equals(actor.getId())) {
            post.setStatus(CommunityPostStatus.REPORTED);
        }
        return toResponse(postRepository.save(post), actor, false);
    }

    @Transactional
    public void delete(UUID postId, User actor) {
        CommunityPost post = post(postId);
        if (actor.getRole() != UserRole.ADMIN && !post.getAuthor().getId().equals(actor.getId())) {
            throw new InvalidRequestException("You can only delete your own post");
        }
        post.setStatus(CommunityPostStatus.HIDDEN);
        postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public CommunityMediaResource loadMedia(UUID mediaId) {
        CommunityMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Community media was not found"));
        return mediaStorageService.load(media);
    }

    public User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user was not found"));
    }

    private CommunityPost post(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Community post was not found"));
    }

    private CommunityPageResponse<CommunityPostResponse> pageResponse(Page<CommunityPost> page, User actor, boolean includeComments) {
        return new CommunityPageResponse<>(
                page.getContent().stream().map(post -> toResponse(post, actor, includeComments)).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private CommunityPostResponse toResponse(CommunityPost post, User actor, boolean includeComments) {
        List<CommunityMediaResponse> media = mediaRepository.findByPostIdOrderBySortOrderAsc(post.getId())
                .stream()
                .map(item -> new CommunityMediaResponse(item.getId(), item.getMediaType(), item.getUrl(), item.getContentType()))
                .toList();
        List<CommunityCommentResponse> comments = includeComments
                ? commentTree(commentRepository.findTop20ByPostIdOrderByCreatedAtAsc(post.getId()))
                : List.of();
        CommunityReactionType myReaction = reactionRepository.findByPostIdAndUserId(post.getId(), actor.getId())
                .map(CommunityReaction::getReactionType)
                .orElse(null);
        return new CommunityPostResponse(
                post.getId(),
                author(post.getAuthor()),
                post.getTopic(),
                post.getContent(),
                post.getVisibility(),
                post.getStatus(),
                media,
                tags(post.getContent()),
                post.getReactionCount(),
                post.getCommentCount(),
                post.getShareCount(),
                myReaction,
                comments,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }

    private List<CommunityCommentResponse> commentTree(List<CommunityComment> comments) {
        Map<UUID, List<CommunityComment>> repliesByParent = comments.stream()
                .filter(comment -> comment.getParent() != null)
                .collect(Collectors.groupingBy(comment -> comment.getParent().getId()));
        return comments.stream()
                .filter(comment -> comment.getParent() == null)
                .map(comment -> toCommentResponse(comment, repliesByParent))
                .toList();
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment, Map<UUID, List<CommunityComment>> repliesByParent) {
        List<CommunityCommentResponse> replies = repliesByParent.getOrDefault(comment.getId(), List.of())
                .stream()
                .map(reply -> toCommentResponse(reply, Map.of()))
                .toList();
        UUID parentId = comment.getParent() == null ? null : comment.getParent().getId();
        return new CommunityCommentResponse(
                comment.getId(),
                author(comment.getAuthor()),
                comment.getContent(),
                parentId,
                replies,
                comment.getCreatedAt());
    }

    private CommunityAuthorResponse author(User user) {
        UserProfile profile = user.getProfile();
        return new CommunityAuthorResponse(
                user.getId(),
                profile == null || !StringUtils.hasText(profile.getFullName()) ? user.getEmail() : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl(),
                user.getRole(),
                profile == null ? null : profile.getProvinceCity());
    }

    private List<String> tags(String content) {
        return Arrays.stream(content.split("\\s+"))
                .filter(value -> value.startsWith("#") && value.length() > 1)
                .map(value -> value.replaceAll("[^#\\p{L}\\p{N}_-]", ""))
                .distinct()
                .limit(8)
                .toList();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String cleanFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String cleanContent(String value) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException("Community post content is required");
        }
        String cleaned = value.trim();
        if (cleaned.length() > 5000) {
            throw new InvalidRequestException("Community post content is too long");
        }
        return cleaned;
    }

    private String cleanComment(String value) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException("Comment content is required");
        }
        return value.trim();
    }

    private void validateWriter(User actor) {
        if (actor.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRequestException("Only active accounts can post in the community");
        }
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.ENGINEER && actor.getRole() != UserRole.FARMER) {
            throw new InvalidRequestException("Only farmers, engineers, or admins can post in the community");
        }
    }

    private void validateAdmin(User actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new InvalidRequestException("Administrator permission is required");
        }
    }
}
