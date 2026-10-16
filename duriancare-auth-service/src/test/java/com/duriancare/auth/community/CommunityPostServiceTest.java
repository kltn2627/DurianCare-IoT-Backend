package com.duriancare.auth.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duriancare.auth.domain.UserConnectionSource;
import com.duriancare.auth.domain.UserConnectionStatus;
import com.duriancare.auth.domain.UserRole;
import com.duriancare.auth.domain.UserStatus;
import com.duriancare.auth.entity.User;
import com.duriancare.auth.entity.UserConnection;
import com.duriancare.auth.exception.ResourceNotFoundException;
import com.duriancare.auth.repository.UserConnectionRepository;
import com.duriancare.auth.repository.UserRepository;
import java.util.Collection;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @Mock
    private CommunityPostRepository postRepository;
    @Mock
    private CommunityMediaRepository mediaRepository;
    @Mock
    private CommunityCommentRepository commentRepository;
    @Mock
    private CommunityReactionRepository reactionRepository;
    @Mock
    private CommunityMediaStorageService mediaStorageService;
    @Mock
    private UserConnectionRepository connectionRepository;
    @Mock
    private UserRepository userRepository;

    private CommunityPostService service;

    @BeforeEach
    void setUp() {
        CommunityPostAccessService accessService = new CommunityPostAccessService(connectionRepository);
        service = new CommunityPostService(
                postRepository,
                mediaRepository,
                commentRepository,
                reactionRepository,
                mediaStorageService,
                accessService,
                userRepository);
        lenient().when(mediaRepository.findByPostIdOrderBySortOrderAsc(any())).thenReturn(List.of());
        lenient().when(commentRepository.findTop20ByPostIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        lenient().when(reactionRepository.findByPostIdAndUserId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void publicPostCanBeViewedByUnrelatedUser() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.PUBLIC, "public content");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        CommunityPostResponse response = service.detail(post.getId(), viewer);

        assertThat(response.id()).isEqualTo(post.getId());
        assertThat(response.visibility()).isEqualTo(CommunityPostVisibility.PUBLIC);
    }

    @Test
    void acceptedConnectionCanViewConnectionsPost() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "connected content");
        UserConnection connection = acceptedConnection(author, viewer);
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.of(connection));

        CommunityPostResponse response = service.detail(post.getId(), viewer);

        assertThat(response.id()).isEqualTo(post.getId());
        assertThat(response.visibility()).isEqualTo(CommunityPostVisibility.CONNECTIONS);
    }

    @Test
    void unrelatedUserCannotViewConnectionsPostByDirectId() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "private content");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(post.getId(), viewer))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Community post was not found");
    }

    @Test
    void pendingConnectionCannotViewConnectionsPost() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "pending blocked content");
        UserConnection connection = pendingConnection(author, viewer);
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.detail(post.getId(), viewer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void disconnectedFormerConnectionCannotViewConnectionsPost() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "former connection content");
        UserConnection connection = acceptedConnection(author, viewer);
        connection.disconnect();
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.detail(post.getId(), viewer))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void authorCanViewOwnConnectionsPostWithoutAcceptedConnections() {
        User author = user(UserRole.FARMER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "own content");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        CommunityPostResponse response = service.detail(post.getId(), author);

        assertThat(response.id()).isEqualTo(post.getId());
        verify(connectionRepository, never()).findPair(any(), any());
    }

    @Test
    void feedUsesAcceptedConnectionsInRepositoryQueryBeforePagination() {
        User actor = user(UserRole.ENGINEER);
        User connectedAuthor = user(UserRole.FARMER);
        UserConnection connection = acceptedConnection(actor, connectedAuthor);
        when(connectionRepository.findAcceptedForUser(actor.getId())).thenReturn(List.of(connection));
        when(postRepository.feed(eq(actor.getId()), any(), eq("Sâu bệnh"), eq("secret"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);

        service.feed(" Sâu bệnh ", " secret ", 0, 10, actor);

        verify(postRepository).feed(eq(actor.getId()), idsCaptor.capture(), eq("Sâu bệnh"), eq("secret"), any(Pageable.class));
        assertThat(idsCaptor.getValue()).containsExactly(connectedAuthor.getId());
    }

    @Test
    void reactionToInaccessiblePostIsDenied() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "no react");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.react(post.getId(), CommunityReactionType.LIKE, viewer))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reactionRepository, never()).save(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    void commentToInaccessiblePostIsDenied() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "no comment");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComment(post.getId(), new CommunityCommentRequest("hello", null), viewer))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(commentRepository, never()).save(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    void reportToInaccessiblePostIsDenied() {
        User author = user(UserRole.FARMER);
        User viewer = user(UserRole.ENGINEER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "no report");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(connectionRepository.findPair(low(author.getId(), viewer.getId()), high(author.getId(), viewer.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(post.getId(), viewer))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void adminModerationListKeepsModerationOverride() {
        User admin = user(UserRole.ADMIN);
        User author = user(UserRole.FARMER);
        CommunityPost post = post(author, CommunityPostVisibility.CONNECTIONS, "reported content");
        post.setStatus(CommunityPostStatus.REPORTED);
        when(postRepository.adminList(eq(CommunityPostStatus.REPORTED), eq(""), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));

        CommunityPageResponse<CommunityPostResponse> response =
                service.adminPosts(CommunityPostStatus.REPORTED, null, null, 0, 10, admin);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo(CommunityPostStatus.REPORTED);
        verify(connectionRepository, never()).findAcceptedForUser(any());
        verify(connectionRepository, never()).findPair(any(), any());
    }

    private User user(UserRole role) {
        User user = new User(UUID.randomUUID() + "@example.com", "hash", UserStatus.ACTIVE, role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private CommunityPost post(User author, CommunityPostVisibility visibility, String content) {
        CommunityPost post = new CommunityPost(author, "Sâu bệnh", content, visibility);
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        return post;
    }

    private UserConnection acceptedConnection(User first, User second) {
        UserConnection connection = pendingConnection(first, second);
        connection.accept();
        return connection;
    }

    private UserConnection pendingConnection(User first, User second) {
        UserConnection connection = new UserConnection(
                first.getId(),
                second.getId(),
                first.getRole(),
                second.getRole(),
                UserConnectionSource.COMMUNITY);
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        return connection;
    }

    private UUID low(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private UUID high(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? second : first;
    }
}
