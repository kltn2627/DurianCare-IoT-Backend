ALTER TABLE community_reactions
    DROP CONSTRAINT IF EXISTS chk_community_reactions_type;

ALTER TABLE community_reactions
    ADD CONSTRAINT chk_community_reactions_type
        CHECK (reaction_type IN ('LIKE', 'LOVE', 'WOW', 'SAD', 'HAHA'));
