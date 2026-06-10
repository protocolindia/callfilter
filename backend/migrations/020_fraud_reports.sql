-- ============================================================
-- Fraud-call reporting
-- ============================================================

CREATE TABLE IF NOT EXISTS fraud_reports (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    number      TEXT NOT NULL,
    category    TEXT,                       -- e.g. 'fraud' | 'scam' | 'spam' | 'phishing'
    note        TEXT,                       -- optional free-text from the user
    reporter    TEXT,                       -- reporter's own phone/name if available
    emailed     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fraud_reports_created ON fraud_reports (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fraud_reports_number  ON fraud_reports (number);

-- Default (empty) settings rows so the admin UI shows the fields.
INSERT INTO settings (key, value) VALUES
    ('fraud_report_email', ''),
    ('smtp_host', ''),
    ('smtp_port', '587'),
    ('smtp_user', ''),
    ('smtp_pass', ''),
    ('smtp_from', ''),
    ('smtp_secure', 'false')
ON CONFLICT (key) DO NOTHING;
