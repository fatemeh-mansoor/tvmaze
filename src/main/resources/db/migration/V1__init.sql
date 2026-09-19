CREATE TABLE show (
    id         BIGINT PRIMARY KEY,
    name       TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE person (
    id       BIGINT PRIMARY KEY,
    name     TEXT NOT NULL,
    birthday DATE
);

CREATE TABLE show_cast (
    show_id   BIGINT NOT NULL REFERENCES show (id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    PRIMARY KEY (show_id, person_id)
);
CREATE INDEX idx_person_birthday ON person (birthday);

CREATE TABLE scrape_state (
    id             SMALLINT PRIMARY KEY DEFAULT 1,
    mode           TEXT NOT NULL DEFAULT 'full',
    next_page      INT NOT NULL DEFAULT 0,
    last_synced_at TIMESTAMPTZ,
    status         TEXT NOT NULL DEFAULT 'idle',
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (id = 1),
    CHECK (mode IN ('full', 'incremental')),
    CHECK (status IN ('idle', 'running'))
);
INSERT INTO scrape_state (id) VALUES (1);
