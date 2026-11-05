-- AI tool usage per (hashed) install and day, for rate limiting. No request content is stored.
CREATE TABLE ai_usage (
    install_hash TEXT NOT NULL,
    day TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (install_hash, day)
);

-- Consented, aggregated "trending near you" signals. One row per distinct contributor per item per day, so counts are
-- numbers of people. install_hash is salted per day: rows cannot be linked across days.
CREATE TABLE signals (
    day TEXT NOT NULL,
    country TEXT NOT NULL,
    region TEXT NOT NULL,
    kind TEXT NOT NULL,
    key TEXT NOT NULL,
    install_hash TEXT NOT NULL,
    PRIMARY KEY (day, country, region, kind, key, install_hash)
);

CREATE INDEX signals_area ON signals (country, region, day);
