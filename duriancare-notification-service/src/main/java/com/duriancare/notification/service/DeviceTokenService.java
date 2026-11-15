package com.duriancare.notification.service;

import com.duriancare.notification.domain.DeviceToken;
import com.duriancare.notification.dto.DeviceTokenResponse;
import com.duriancare.notification.dto.RegisterDeviceTokenRequest;
import com.duriancare.notification.repository.DeviceTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeviceTokenService {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REPLACED = "REPLACED";
    public static final String REASON_INVALID = "INVALID";

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceTokenService.class);

    private final MongoTemplate mongoTemplate;
    private final DeviceTokenRepository repository;
    private final Clock clock;

    public DeviceTokenService(MongoTemplate mongoTemplate, DeviceTokenRepository repository, Clock pushClock) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
        this.clock = pushClock;
    }

    /**
     * Binds the token to the caller, taking it over from any previous owner, and retires whatever else was
     * active on this installation: its old token after a refresh, or the previous account's row after a
     * logout that never reached the server. That is what keeps one phone receiving for one user.
     */
    public DeviceTokenResponse register(String userId, RegisterDeviceTokenRequest request) {
        String owner = requireUser(userId);
        DeviceToken saved = upsert(owner, request);
        mongoTemplate.updateMulti(
                new Query(Criteria.where("deviceId").is(saved.getDeviceId())
                        .and("active").is(true)
                        .and("_id").ne(saved.getId())),
                deactivation(REASON_REPLACED),
                DeviceToken.class);
        return DeviceTokenResponse.from(saved);
    }

    /** Idempotent, and scoped to the caller: another user's device id simply matches nothing. */
    public void unregister(String userId, String deviceId) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("userId").is(requireUser(userId))
                        .and("deviceId").is(deviceId)
                        .and("active").is(true)),
                deactivation(REASON_LOGOUT),
                DeviceToken.class);
    }

    public List<DeviceToken> activeTokens(String userId) {
        return repository.findByUserIdAndActiveTrue(userId);
    }

    public List<DeviceToken> activeTokens(String userId, Collection<String> ids) {
        return repository.findByUserIdAndActiveTrueAndIdIn(userId, ids);
    }

    public void deactivateInvalid(Collection<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        mongoTemplate.updateMulti(
                new Query(Criteria.where("_id").in(ids).and("active").is(true)),
                deactivation(REASON_INVALID),
                DeviceToken.class);
        LOGGER.info("Deactivated {} invalid FCM token(s)", ids.size());
    }

    private DeviceToken upsert(String owner, RegisterDeviceTokenRequest request) {
        Instant now = clock.instant();
        Query byToken = new Query(Criteria.where("token").is(request.token()));
        Update update = new Update()
                .set("userId", owner)
                .set("deviceId", request.deviceId())
                .set("platform", request.platform())
                .set("appVersion", request.appVersion())
                .set("active", true)
                .set("updatedAt", now)
                .unset("deactivatedAt")
                .unset("deactivationReason")
                .setOnInsert("createdAt", now);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        try {
            return mongoTemplate.findAndModify(byToken, update, options, DeviceToken.class);
        } catch (DuplicateKeyException race) {
            // Two concurrent first registrations of one token: the row now exists, so this one updates it.
            return mongoTemplate.findAndModify(byToken, update, options, DeviceToken.class);
        }
    }

    private Update deactivation(String reason) {
        Instant now = clock.instant();
        return new Update()
                .set("active", false)
                .set("deactivationReason", reason)
                .set("deactivatedAt", now)
                .set("updatedAt", now);
    }

    private String requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new NotificationAccessDeniedException("Current user identity is required");
        }
        return userId.trim();
    }
}
