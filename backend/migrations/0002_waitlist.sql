-- Early-access sign-ups from the website. Only what is needed to send one invite email.
CREATE TABLE waitlist (
    email TEXT PRIMARY KEY,
    platform TEXT NOT NULL,
    country TEXT,
    created_at TEXT NOT NULL
);
