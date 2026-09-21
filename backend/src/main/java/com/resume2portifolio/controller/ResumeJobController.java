package com.resume2portifolio.controller;

import com.resume2portifolio.repository.ResumeJobRepository;
import com.resume2portifolio.service.ResumeProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@RequestMapping("/api/resumes")
public class ResumeJobController {
    private final ResumeJobRepository repository;
    private final long maxFileSizeBytes;
    private final ResumeProcessingService processingService;

    public ResumeJobController(
            ResumeJobRepository repository,
            @Value("${resume2portifolio.resume.max-file-size-bytes:10485760}") long maxFileSizeBytes,
            ResumeProcessingService processingService
    ) {
        this.repository = repository;
        this.processingService = processingService;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @PostMapping(consumes = "multipart/form-data")
        public ResponseEntity<Map<String, UUID>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "editorial") String template
        ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > maxFileSizeBytes) {
            return ResponseEntity.status(413).build();
        }
        try {
            if (!template.equals("editorial") && !template.equals("minimal")) {
                return ResponseEntity.badRequest().build();
            }
            UUID jobId = processingService.queue(file.getBytes(), file.getOriginalFilename(), template);
            return ResponseEntity.accepted().body(Map.of("jobId", jobId));
        } catch (IOException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID jobId) {
        var job = repository.findById(jobId);
        if (job.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", job.get().id());
        response.put("status", job.get().status());
        response.put("filename", job.get().originalFilename());
        response.put("template", job.get().templateName());
        response.put("createdAt", job.get().createdAt());
        response.put("events", repository.findEvents(jobId));
        if (job.get().status() == com.resume2portifolio.domain.JobStatus.READY) {
            response.put("portfolioUrl", "/portfolio/" + jobId);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<Map<String, Object>>> list() {
        var response = repository.findAll().stream().map(job -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", job.id());
            item.put("status", job.status());
            item.put("filename", job.originalFilename());
            item.put("template", job.templateName());
            item.put("createdAt", job.createdAt());
            item.put("events", repository.findEvents(job.id()));
            if (job.status() == com.resume2portifolio.domain.JobStatus.READY) {
                item.put("portfolioUrl", "/portfolio/" + job.id());
            }
            return item;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> delete(@PathVariable UUID jobId) throws IOException {
        return processingService.delete(jobId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
