package com.duriancare.farm.service;

import com.duriancare.farm.dto.AgronomistSummaryResponse;
import java.util.List;

public interface AgronomistDirectoryClient {

    List<AgronomistSummaryResponse> search(String query);

    AgronomistSummaryResponse getEligible(String agronomistId);
}
