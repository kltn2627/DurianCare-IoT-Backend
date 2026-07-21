package com.duriancare.farm.config;

import com.duriancare.farm.domain.AgronomistInvitation;
import com.duriancare.farm.domain.FarmAuthorization;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
public class FarmAuthorizationMongoIndexInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmAuthorizationMongoIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public FarmAuthorizationMongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    void ensureIndexes() {
        mongoTemplate.indexOps(AgronomistInvitation.class).ensureIndex(new CompoundIndexDefinition(
                        new org.bson.Document()
                                .append("farmId", 1)
                                .append("agronomistId", 1))
                        .named("uk_pending_agronomist_invitation")
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("blocksNewInvitation").is(true))));
        mongoTemplate.indexOps(FarmAuthorization.class).ensureIndex(new CompoundIndexDefinition(
                        new org.bson.Document()
                                .append("farmId", 1)
                                .append("agronomistId", 1))
                        .named("uk_active_farm_authorization")
                        .unique()
                        .partial(PartialIndexFilter.of(Criteria.where("blocksNewAuthorization").is(true))));
        LOGGER.info("Ensured farm authorization MongoDB partial unique indexes");
    }
}
