package com.duriancare.notification.service;

import com.duriancare.notification.config.OtpProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RedisOtpService implements OtpService {

    private static final String KEY_PREFIX = "duriancare:notification:otp:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final OtpProperties properties;

    public RedisOtpService(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            EmailService emailService,
            OtpProperties properties) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.properties = properties;
    }

    @Override
    public void generateOtp(String email) {
        String normalizedEmail = normalize(email);
        String key = KEY_PREFIX + normalizedEmail;
        String otp = createNumericOtp();
        redisTemplate.opsForValue().set(
                key,
                serialize(new OtpCacheValue(passwordEncoder.encode(otp), 0)),
                properties.ttl());
        try {
            emailService.sendOtpEmail(normalizedEmail, otp, properties.ttl().toMinutes());
        } catch (RuntimeException exception) {
            redisTemplate.delete(key);
            throw exception;
        }
    }

    @Override
    public boolean validateOtp(String email, String otp) {
        String key = KEY_PREFIX + normalize(email);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return false;
        }
        OtpCacheValue value = deserialize(cached);
        if (passwordEncoder.matches(otp, value.hashedOtp())) {
            redisTemplate.delete(key);
            return true;
        }
        int attempts = value.attempts() + 1;
        if (attempts >= properties.maxAttempts()) {
            redisTemplate.delete(key);
            return false;
        }
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            redisTemplate.delete(key);
            return false;
        }
        redisTemplate.opsForValue().set(
                key,
                serialize(new OtpCacheValue(value.hashedOtp(), attempts)),
                Duration.ofSeconds(seconds));
        return false;
    }

    private String createNumericOtp() {
        StringBuilder otp = new StringBuilder(properties.length());
        for (int index = 0; index < properties.length(); index++) {
            otp.append(SECURE_RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    private String serialize(OtpCacheValue value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize OTP state", exception);
        }
    }

    private OtpCacheValue deserialize(String value) {
        try {
            return objectMapper.readValue(value, OtpCacheValue.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize OTP state", exception);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record OtpCacheValue(String hashedOtp, int attempts) {
    }
}
