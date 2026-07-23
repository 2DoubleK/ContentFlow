CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cf_project (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cf_document (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES cf_project(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id),
    filename VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cf_content (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES cf_project(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id),
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    markdown TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
