CREATE SCHEMA IF NOT EXISTS paperless_rag;

CREATE EXTENSION IF NOT EXISTS vector;

-- Documents we know about + ingestion status
CREATE TABLE IF NOT EXISTS paperless_rag.document_source
(
    id                    BIGSERIAL PRIMARY KEY,
    paperless_doc_id      INTEGER     NOT NULL UNIQUE,            -- FK into public.documents_document.id (logically)
    title                 TEXT        NOT NULL,
    correspondent         TEXT,
    doc_date DATE,

    paperless_modified_at TIMESTAMPTZ NOT NULL,                   -- snapshot of documents_document.modified
    last_ingested_at      TIMESTAMPTZ,
    status                TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING/RUNNING/DONE/ERROR
    error_message         TEXT,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_document_source_status
    ON paperless_rag.document_source (status);

CREATE INDEX IF NOT EXISTS idx_document_source_modified
    ON paperless_rag.document_source (paperless_modified_at);

-- Embedding chunks per document
CREATE TABLE IF NOT EXISTS paperless_rag.document_chunk
(
    id                 BIGSERIAL PRIMARY KEY,
    document_source_id BIGINT      NOT NULL REFERENCES paperless_rag.document_source (id) ON DELETE CASCADE,
    chunk_index        INTEGER     NOT NULL,
    content            TEXT        NOT NULL,
    embedding          VECTOR(1536),
    metadata           JSONB,

    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE paperless_rag.document_chunk
    ADD CONSTRAINT uq_document_chunk_doc_idx UNIQUE (document_source_id, chunk_index);

-- Vector index for fast similarity queries
CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding
    ON paperless_rag.document_chunk
        USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);