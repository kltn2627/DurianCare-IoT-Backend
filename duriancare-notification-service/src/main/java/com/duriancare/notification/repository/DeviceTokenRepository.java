package com.duriancare.notification.repository;

import com.duriancare.notification.domain.DeviceToken;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeviceTokenRepository extends MongoRepository<DeviceToken, String> {

    List<DeviceToken> findByUserIdAndActiveTrue(String userId);

    List<DeviceToken> findByUserIdAndActiveTrueAndIdIn(String userId, Collection<String> ids);
}
