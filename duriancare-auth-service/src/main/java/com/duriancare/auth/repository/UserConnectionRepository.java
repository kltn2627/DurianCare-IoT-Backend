package com.duriancare.auth.repository;

import com.duriancare.auth.domain.ConnectionStatus;
import com.duriancare.auth.domain.UserConnection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserConnectionRepository
        extends MongoRepository<UserConnection, String> {

    List<UserConnection> findByRecipientUserIdAndStatus(
            String recipientUserId,
            ConnectionStatus status);
}
