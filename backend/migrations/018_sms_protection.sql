-- ============================================================
-- SMS phishing/spam protection
-- ============================================================

-- Admin-managed keyword/phrase rules. Each row is a phrase that, if found
-- in an SMS body, contributes to the spam/phishing score.
CREATE TABLE IF NOT EXISTS sms_keywords (
    id          SERIAL PRIMARY KEY,
    phrase      TEXT NOT NULL,
    category    TEXT NOT NULL DEFAULT 'spam',   -- 'spam' | 'phishing' | 'scam' | 'promotional'
    weight      INT  NOT NULL DEFAULT 30,       -- contribution to the 0-100 score
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Admin-managed URL / domain blocklist for links found in SMS.
CREATE TABLE IF NOT EXISTS sms_url_blocklist (
    id          SERIAL PRIMARY KEY,
    domain      TEXT NOT NULL,                  -- e.g. "bit.ly" or "secure-paypaI.com"
    category    TEXT NOT NULL DEFAULT 'phishing',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sms_url_domain ON sms_url_blocklist (domain);

-- Per-user flagged-SMS history (synced from the device, like blocked_calls).
CREATE TABLE IF NOT EXISTS flagged_sms (
    id          SERIAL PRIMARY KEY,
    user_id     INT NOT NULL,
    client_id   TEXT NOT NULL,                  -- device-generated dedup id
    sender      TEXT,
    preview     TEXT,                           -- truncated, privacy-safe snippet
    category    TEXT,                           -- 'spam' | 'phishing' | ...
    score       INT,
    reasons     TEXT,                           -- comma-separated matched reasons
    flagged_at_ms BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, client_id)
);
CREATE INDEX IF NOT EXISTS idx_flagged_sms_user ON flagged_sms (user_id, flagged_at_ms DESC);

-- Seed a few sensible default keyword rules (idempotent on fresh installs).
INSERT INTO sms_keywords (phrase, category, weight) VALUES
    ('verify your account', 'phishing', 40),
    ('kyc update', 'phishing', 40),
    ('click the link', 'phishing', 25),
    ('you have won', 'spam', 35),
    ('claim your prize', 'spam', 40),
    ('account will be suspended', 'phishing', 45),
    ('urgent action required', 'phishing', 35),
    ('congratulations you', 'spam', 30),
    ('limited time offer', 'promotional', 20),
    ('update your payment', 'phishing', 45)
ON CONFLICT DO NOTHING;

-- Seed common high-risk URL shorteners / patterns.
INSERT INTO sms_url_blocklist (domain, category) VALUES
    ('bit.ly', 'suspicious'),
    ('tinyurl.com', 'suspicious'),
    ('t.co', 'suspicious'),
    ('is.gd', 'suspicious'),
    ('cutt.ly', 'suspicious')
ON CONFLICT DO NOTHING;
