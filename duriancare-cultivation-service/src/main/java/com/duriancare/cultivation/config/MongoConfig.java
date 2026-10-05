package com.duriancare.cultivation.config;

import com.duriancare.cultivation.domain.CultivationTaskStatus;
import com.duriancare.cultivation.domain.CultivationTaskType;
import com.duriancare.cultivation.domain.HarvestBatchStatus;
import java.time.LocalTime;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
            new StringToCultivationTaskTypeConverter(),
            new StringToCultivationTaskStatusConverter(),
            new StringToHarvestBatchStatusConverter(),
            new StringToLocalTimeConverter()
        ));
    }

    @ReadingConverter
    static class StringToCultivationTaskTypeConverter implements Converter<String, CultivationTaskType> {
        @Override
        public CultivationTaskType convert(String source) {
            return CultivationTaskType.fromValue(source);
        }
    }

    @ReadingConverter
    static class StringToCultivationTaskStatusConverter implements Converter<String, CultivationTaskStatus> {
        @Override
        public CultivationTaskStatus convert(String source) {
            return CultivationTaskStatus.fromValue(source);
        }
    }

    @ReadingConverter
    static class StringToHarvestBatchStatusConverter implements Converter<String, HarvestBatchStatus> {
        @Override
        public HarvestBatchStatus convert(String source) {
            try {
                return HarvestBatchStatus.valueOf(source.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Legacy values (e.g. PENDING_INSPECTION from old seed data) map to APPROVED
                return HarvestBatchStatus.APPROVED;
            }
        }
    }

    @ReadingConverter
    static class StringToLocalTimeConverter implements Converter<String, LocalTime> {
        @Override
        public LocalTime convert(String source) {
            return LocalTime.parse(source);
        }
    }
}
