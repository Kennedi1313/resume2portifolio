package com.resume2portifolio.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResumeJob(
        UUID id,
        UUID fileId,
        String originalFilename,
        String templateName,
        JobStatus status,
        OffsetDateTime createdAt
) {
}
