package com.duriancare.auth.community;

import com.duriancare.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "community_reactions",
        uniqueConstraints = @UniqueConstraint(name = "uq_community_reactions_post_user", columnNames = {"post_id", "user_id"}))
public class CommunityReaction {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 20)
    private CommunityReactionType reactionType;

    protected CommunityReaction() {
    }

    public CommunityReaction(CommunityPost post, User user, CommunityReactionType reactionType) {
        this.post = post;
        this.user = user;
        this.reactionType = reactionType;
    }

    public CommunityReactionType getReactionType() {
        return reactionType;
    }

    public void setReactionType(CommunityReactionType reactionType) {
        this.reactionType = reactionType;
    }
}
