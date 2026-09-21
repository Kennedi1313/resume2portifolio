package com.resume2portifolio.service;

import com.resume2portifolio.ai.ResumeAIExtractor;
import com.resume2portifolio.domain.JobStatus;
import com.resume2portifolio.extraction.ResumeTextExtractor;
import com.resume2portifolio.messaging.ResumeProcessMessage;
import com.resume2portifolio.messaging.ResumeProcessPublisher;
import com.resume2portifolio.repository.ResumeJobRepository;
import com.resume2portifolio.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
public class ResumeProcessingService {
    private final ResumeJobRepository repository;
    private final ResumeProcessPublisher publisher;
    private final ResumeAIExtractor aiExtractor;
    private final ResumeTextExtractor textExtractor;
    private final FileStorage fileStorage;
    private final int minTextLength;
    private final int maxTextLength;
    private final long mockStepDelayMs;

    public ResumeProcessingService(ResumeJobRepository repository,
                                   ResumeProcessPublisher publisher,
                                   ResumeAIExtractor aiExtractor,
                                   ResumeTextExtractor textExtractor,
                                   FileStorage fileStorage,
                                   @Value("${resume2portifolio.resume.min-text-length:80}") int minTextLength,
                                   @Value("${resume2portifolio.resume.max-text-length:100000}") int maxTextLength,
                                   @Value("${resume2portifolio.resume.mock-step-delay-ms:1000}") long mockStepDelayMs) {
        this.repository = repository;
        this.publisher = publisher;
        this.aiExtractor = aiExtractor;
        this.textExtractor = textExtractor;
        this.fileStorage = fileStorage;
        this.minTextLength = minTextLength;
        this.maxTextLength = maxTextLength;
        this.mockStepDelayMs = mockStepDelayMs;
    }

    public UUID queue(byte[] content, String originalFilename) throws IOException {
        return queue(content, originalFilename, "editorial");
    }

    public UUID queue(byte[] content, String originalFilename, String templateName) throws IOException {
        UUID fileId = fileStorage.save(new ByteArrayInputStream(content));
        UUID jobId = UUID.randomUUID();
        repository.create(jobId, fileId, originalFilename, templateName, JobStatus.QUEUED);
        publisher.publish(jobId, fileId);
        return jobId;
    }

    public void process(ResumeProcessMessage message) throws IOException {
        if (!jobExists(message.jobId())) return;
        repository.updateStatus(message.jobId(), JobStatus.EXTRACTING_TEXT);
        delayForDemo();
        if (!jobExists(message.jobId())) return;
        String text;
        try (var pdf = fileStorage.read(message.fileId())) {
            text = textExtractor.extract(pdf);
        }
        if (text.length() < minTextLength) {
            throw new InvalidResumeTextException("Texto extraído insuficiente");
        }
        if (text.length() > maxTextLength) {
            throw new InvalidResumeTextException("Texto extraído acima do limite permitido");
        }
        if (!jobExists(message.jobId())) return;
        repository.updateStatus(message.jobId(), JobStatus.AI_PROCESSING);
        delayForDemo();
        if (!jobExists(message.jobId())) return;
        var document = aiExtractor.extract(text);
        repository.saveDocument(message.jobId(), document);
        if (!jobExists(message.jobId())) return;
        repository.updateStatus(message.jobId(), JobStatus.AI_COMPLETED);
        delayForDemo();
        if (!jobExists(message.jobId())) return;
        repository.updateStatus(message.jobId(), JobStatus.READY);
    }

    public void delayForDemo() {
        if (mockStepDelayMs <= 0) return;
        try {
            Thread.sleep(mockStepDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processamento interrompido", exception);
        }
    }

    public void fail(UUID jobId) {
        if (!jobExists(jobId)) return;
        repository.updateStatus(jobId, JobStatus.FAILED);
    }

    public boolean jobExists(UUID jobId) {
        return repository.findById(jobId).isPresent();
    }

    public boolean delete(UUID jobId) throws IOException {
        var job = repository.findById(jobId);
        if (job.isEmpty()) return false;
        fileStorage.delete(job.get().fileId());
        repository.delete(jobId);
        return true;
    }
}
