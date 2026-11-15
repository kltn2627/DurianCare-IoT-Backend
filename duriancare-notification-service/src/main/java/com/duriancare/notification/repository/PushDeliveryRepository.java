package com.duriancare.notification.repository;

import com.duriancare.notification.domain.PushDelivery;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PushDeliveryRepository extends MongoRepository<PushDelivery, String> {

    /** Deliveries that are due, plus claims left behind by a sender that died mid-attempt. */
    @Query(value = "{ '$or': ["
            + "{ 'status': { '$in': ['PENDING', 'RETRY'] }, 'nextAttemptAt': { '$lte': ?0 } },"
            + "{ 'status': 'SENDING', 'claimedAt': { '$lte': ?1 } }"
            + "] }", fields = "{ '_id': 1 }")
    List<PushDelivery> findDue(Instant now, Instant staleClaimBefore, Pageable pageable);
}
