package com.resume2portifolio.controller;

import com.resume2portifolio.domain.JobStatus;
import com.resume2portifolio.repository.ResumeJobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class PortfolioController {
    private final ResumeJobRepository repository;

    public PortfolioController(ResumeJobRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/resumes/{jobId}/portfolio")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID jobId) {
        var job = repository.findById(jobId);
        if (job.isEmpty() || job.get().status() != JobStatus.READY) {
            return ResponseEntity.notFound().build();
        }
        var document = repository.findDocument(jobId);
        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "portfolioName", document.get().basics().name(),
            "template", job.get().templateName(),
            "data", document.get()
        ));
    }
}
