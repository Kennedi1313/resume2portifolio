CREATE TABLE IF NOT EXISTS resume_job (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    original_filename VARCHAR(255),
    template_name VARCHAR(40) NOT NULL DEFAULT 'editorial',
    status VARCHAR(50) NOT NULL,
    resume_document JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE resume_job ADD COLUMN IF NOT EXISTS template_name VARCHAR(40) NOT NULL DEFAULT 'editorial';

CREATE TABLE IF NOT EXISTS resume_job_event (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES resume_job(id),
    type VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    metadata JSONB
);
