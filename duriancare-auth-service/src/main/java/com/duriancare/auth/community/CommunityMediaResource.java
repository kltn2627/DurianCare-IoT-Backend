package com.duriancare.auth.community;

import org.springframework.core.io.Resource;

public record CommunityMediaResource(Resource resource, String contentType) {
}
