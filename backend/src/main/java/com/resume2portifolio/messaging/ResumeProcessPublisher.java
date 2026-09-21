package com.resume2portifolio.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ResumeProcessPublisher {
    public static final String QUEUE = "resume.process";

    private final RabbitTemplate rabbitTemplate;

    public ResumeProcessPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(UUID jobId, UUID fileId) {
        rabbitTemplate.convertAndSend(QUEUE, new ResumeProcessMessage(
                UUID.randomUUID(), jobId, fileId, 1, "1.0", OffsetDateTime.now()));
    }
}
