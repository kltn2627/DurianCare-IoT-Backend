package com.duriancare.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

class AuthDependencyHealthTest {
    @Test void sqlAuthServiceDoesNotAutoConfigureAnUnusedLocalMongoClient() {
        // A Mongo starter with no Mongo repositories caused actuator health to time out on localhost:27017.
        assertThat(ClassUtils.isPresent("com.mongodb.client.MongoClient", getClass().getClassLoader())).isFalse();
    }
}
