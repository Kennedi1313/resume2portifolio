package com.resume2portifolio.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResumeProcessMessage(
        UUID eventId,
        UUID jobId,
        UUID fileId,
        int attempt,
        String schemaVersion,
        OffsetDateTime occurredAt
) {}
