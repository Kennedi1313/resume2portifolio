package com.resume2portifolio.worker;

import com.resume2portifolio.messaging.ResumeProcessMessage;
import com.resume2portifolio.service.ResumeProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.resume2portifolio.service.InvalidResumeTextException;
import java.io.IOException;

@Component
@ConditionalOnProperty(name = "resume2portifolio.worker", havingValue = "resume")
public class ResumeProcessingWorker {
    private final ResumeProcessingService processingService;

    public ResumeProcessingWorker(ResumeProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(queues = "resume.process")
    public void process(ResumeProcessMessage message) throws Exception {
        try {
            processingService.process(message);
        } catch (InvalidResumeTextException | IllegalArgumentException | IOException exception) {
            processingService.fail(message.jobId());
        }
    }
}
