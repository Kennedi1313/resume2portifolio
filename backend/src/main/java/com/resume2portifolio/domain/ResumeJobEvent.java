package com.resume2portifolio.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResumeJobEvent(UUID id, UUID jobId, String type, OffsetDateTime createdAt, String metadata) {
}
