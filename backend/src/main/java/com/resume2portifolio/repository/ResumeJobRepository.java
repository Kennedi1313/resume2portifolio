package com.resume2portifolio.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume2portifolio.domain.JobStatus;
import com.resume2portifolio.domain.ResumeDocument;
import com.resume2portifolio.domain.ResumeJob;
import com.resume2portifolio.domain.ResumeJobEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ResumeJobRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ResumeJobRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void saveDocument(UUID jobId, ResumeDocument document) {
        try {
                jdbc.update(
                    "UPDATE resume_job SET resume_document = CAST(? AS jsonb) WHERE id = ?",
                    objectMapper.writeValueAsString(document), jobId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o ResumeDocument", exception);
        }
    }

    public void updateStatus(UUID jobId, JobStatus status) {
        jdbc.update("UPDATE resume_job SET status = ? WHERE id = ?", status.name(), jobId);
        addEvent(jobId, status.name());
    }

    public void addEvent(UUID jobId, String type) {
        jdbc.update("""
                INSERT INTO resume_job_event (id, job_id, type, created_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), jobId, type, OffsetDateTime.now());
    }

    public List<ResumeJobEvent> findEvents(UUID jobId) {
        return jdbc.query("""
                SELECT id, job_id, type, created_at, metadata
                FROM resume_job_event
                WHERE job_id = ?
                ORDER BY created_at ASC
                """, (resultSet, rowNum) -> new ResumeJobEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("job_id", UUID.class),
                resultSet.getString("type"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getString("metadata")
        ), jobId);
    }

    public Optional<ResumeDocument> findDocument(UUID jobId) {
        return jdbc.query(
            "SELECT resume_document FROM resume_job WHERE id = ?",
                (resultSet, rowNum) -> {
                    try {
                        return objectMapper.readValue(resultSet.getString("resume_document"), ResumeDocument.class);
                    } catch (JsonProcessingException exception) {
                        throw new IllegalStateException("Não foi possível ler o ResumeDocument", exception);
                    }
                }, jobId).stream().findFirst();
    }

    public void create(UUID id, UUID fileId, String originalFilename, JobStatus status) {
        create(id, fileId, originalFilename, "editorial", status);
    }

    public void create(UUID id, UUID fileId, String originalFilename, String templateName, JobStatus status) {
        jdbc.update("""
                INSERT INTO resume_job (id, file_id, original_filename, template_name, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, fileId, originalFilename, templateName, status.name(), OffsetDateTime.now());
        addEvent(id, "RECEIVED");
        addEvent(id, status.name());
    }

    public Optional<ResumeJob> findById(UUID id) {
        return jdbc.query("""
                SELECT id, file_id, original_filename, template_name, status, created_at
                FROM resume_job
                WHERE id = ?
                """, (resultSet, rowNum) -> new ResumeJob(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("file_id", UUID.class),
                resultSet.getString("original_filename"),
                resultSet.getString("template_name"),
                JobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class)
        ), id).stream().findFirst();
    }

    public List<ResumeJob> findAll() {
        return jdbc.query("""
                SELECT id, file_id, original_filename, template_name, status, created_at
                FROM resume_job
                ORDER BY created_at DESC
                """, (resultSet, rowNum) -> new ResumeJob(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("file_id", UUID.class),
                resultSet.getString("original_filename"),
                resultSet.getString("template_name"),
                JobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", OffsetDateTime.class)
        ));
    }

    public void delete(UUID jobId) {
        jdbc.update("DELETE FROM resume_job_event WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM resume_job WHERE id = ?", jobId);
    }
}
