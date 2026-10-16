package com.duriancare.auth.knowledge;

import org.springframework.core.io.Resource;

public record KnowledgeImageResource(Resource resource, String contentType) {
}

