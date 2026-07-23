CREATE OR REPLACE FUNCTION cf_touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sys_user_username_unique UNIQUE (username),
    CONSTRAINT sys_user_username_not_blank CHECK (length(btrim(username)) > 0),
    CONSTRAINT sys_user_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE IF NOT EXISTS cf_project (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    platform VARCHAR(50),
    domain VARCHAR(100),
    positioning TEXT,
    target_audience TEXT,
    content_style VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_project_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT cf_project_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS cf_document (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES cf_project(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    file_url VARCHAR(500),
    file_ext VARCHAR(16),
    mime_type VARCHAR(128),
    file_size BIGINT NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'INDEXING',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_document_filename_not_blank CHECK (length(btrim(filename)) > 0),
    CONSTRAINT cf_document_status_check CHECK (status IN ('INDEXING', 'READY', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS cf_document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES cf_document(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES cf_project(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    chroma_id VARCHAR(100) UNIQUE,
    token_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_document_chunk_index_check CHECK (chunk_index >= 0)
);

CREATE TABLE IF NOT EXISTS cf_content (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES cf_project(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    content TEXT,
    markdown TEXT NOT NULL,
    content_type VARCHAR(50) NOT NULL DEFAULT 'ARTICLE',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    conversation_id BIGINT,
    references_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_content_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT cf_content_markdown_not_blank CHECK (length(btrim(markdown)) > 0),
    CONSTRAINT cf_content_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT cf_content_type_check CHECK (content_type IN ('ARTICLE', 'SHORT_POST', 'SCRIPT', 'OTHER'))
);

CREATE TABLE IF NOT EXISTS cf_content_tag (
    id BIGSERIAL PRIMARY KEY,
    content_id BIGINT NOT NULL REFERENCES cf_content(id) ON DELETE CASCADE,
    tag_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_content_tag_name_not_blank CHECK (length(btrim(tag_name)) > 0)
);

CREATE TABLE IF NOT EXISTS cf_agent_conversation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES cf_project(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL UNIQUE,
    title VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_agent_conversation_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE IF NOT EXISTS cf_agent_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES cf_agent_conversation(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cf_agent_message_role_check CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL'))
);

CREATE INDEX IF NOT EXISTS idx_sys_user_username
    ON sys_user (username);

CREATE INDEX IF NOT EXISTS idx_cf_project_owner_created
    ON cf_project (owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cf_project_status
    ON cf_project (status);

CREATE INDEX IF NOT EXISTS idx_cf_document_project_created
    ON cf_document (project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cf_document_owner_created
    ON cf_document (owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cf_document_status
    ON cf_document (status);

CREATE INDEX IF NOT EXISTS idx_cf_document_chunk_project
    ON cf_document_chunk (project_id);

CREATE INDEX IF NOT EXISTS idx_cf_document_chunk_document
    ON cf_document_chunk (document_id);

CREATE INDEX IF NOT EXISTS idx_cf_content_project_created
    ON cf_content (project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cf_content_owner_created
    ON cf_content (owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_cf_content_status
    ON cf_content (status);

CREATE INDEX IF NOT EXISTS idx_cf_content_tag_content
    ON cf_content_tag (content_id);

CREATE INDEX IF NOT EXISTS idx_cf_conversation_project
    ON cf_agent_conversation (project_id);

CREATE INDEX IF NOT EXISTS idx_cf_conversation_user
    ON cf_agent_conversation (user_id);

CREATE INDEX IF NOT EXISTS idx_cf_message_conversation
    ON cf_agent_message (conversation_id);

DROP TRIGGER IF EXISTS trg_sys_user_touch_updated_at ON sys_user;
CREATE TRIGGER trg_sys_user_touch_updated_at
BEFORE UPDATE ON sys_user
FOR EACH ROW
EXECUTE FUNCTION cf_touch_updated_at();

DROP TRIGGER IF EXISTS trg_cf_project_touch_updated_at ON cf_project;
CREATE TRIGGER trg_cf_project_touch_updated_at
BEFORE UPDATE ON cf_project
FOR EACH ROW
EXECUTE FUNCTION cf_touch_updated_at();

DROP TRIGGER IF EXISTS trg_cf_document_touch_updated_at ON cf_document;
CREATE TRIGGER trg_cf_document_touch_updated_at
BEFORE UPDATE ON cf_document
FOR EACH ROW
EXECUTE FUNCTION cf_touch_updated_at();

DROP TRIGGER IF EXISTS trg_cf_content_touch_updated_at ON cf_content;
CREATE TRIGGER trg_cf_content_touch_updated_at
BEFORE UPDATE ON cf_content
FOR EACH ROW
EXECUTE FUNCTION cf_touch_updated_at();

DROP TRIGGER IF EXISTS trg_cf_agent_conversation_touch_updated_at ON cf_agent_conversation;
CREATE TRIGGER trg_cf_agent_conversation_touch_updated_at
BEFORE UPDATE ON cf_agent_conversation
FOR EACH ROW
EXECUTE FUNCTION cf_touch_updated_at();
