package com.duriancare.farm.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.duriancare.farm.domain.FarmPermissionType;
import com.duriancare.farm.dto.FarmAccessCheckRequest;
import com.duriancare.farm.service.FarmAccessCheckService;
import com.duriancare.farm.service.FarmAuthenticationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalFarmAccessControllerTest {

    @Mock
    private FarmAccessCheckService service;

    @Test
    void validInternalTokenAllowsAccessCheck() {
        InternalFarmAccessController controller = new InternalFarmAccessController(service, "secret");
        FarmAccessCheckRequest request = request();
        when(service.canAccess(request)).thenReturn(true);

        assertThat(controller.check("secret", request).allowed()).isTrue();
    }

    @Test
    void missingInternalTokenIsDenied() {
        InternalFarmAccessController controller = new InternalFarmAccessController(service, "secret");

        assertThatThrownBy(() -> controller.check(null, request()))
                .isInstanceOf(FarmAuthenticationException.class);
    }

    @Test
    void invalidInternalTokenIsDenied() {
        InternalFarmAccessController controller = new InternalFarmAccessController(service, "secret");

        assertThatThrownBy(() -> controller.check("wrong", request()))
                .isInstanceOf(FarmAuthenticationException.class);
    }

    private FarmAccessCheckRequest request() {
        return new FarmAccessCheckRequest(
                "engineer-1",
                "ENGINEER",
                "farm-1",
                "zone-1",
                FarmPermissionType.VIEW_CARE_SCHEDULE);
    }
}
