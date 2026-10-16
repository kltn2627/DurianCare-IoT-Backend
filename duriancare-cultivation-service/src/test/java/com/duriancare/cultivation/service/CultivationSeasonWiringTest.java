package com.duriancare.cultivation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.duriancare.cultivation.repository.CultivationSeasonRepository;
import com.duriancare.cultivation.security.CultivationAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class CultivationSeasonWiringTest {
    @Test void springCanConstructSeasonCatalogWithProductionDependencies() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CultivationSeasonRepository.class, () -> mock(CultivationSeasonRepository.class));
            context.registerBean(CultivationAccessGuard.class, () -> mock(CultivationAccessGuard.class));
            context.register(CultivationSeasonCatalogService.class);
            context.refresh();
            assertThat(context.getBean(CultivationSeasonCatalogService.class)).isNotNull();
        }
    }
}
