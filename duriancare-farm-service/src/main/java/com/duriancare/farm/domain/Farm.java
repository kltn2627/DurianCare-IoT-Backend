package com.duriancare.farm.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "farms")
public record Farm(
        @Id String id,
        @Indexed String ownerUserId,
        String name,
        String address,
        String province,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal areaHectares,
        FarmStatus status,
        List<FarmZone> zones,
        Instant createdAt,
        Instant updatedAt) {
}
