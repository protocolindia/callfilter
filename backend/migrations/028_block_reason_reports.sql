-- ============================================================
-- Block reasons become report categories; user email; shared templates
-- ============================================================

-- Structured block reasons (replaces the newline string in settings).
CREATE TABLE IF NOT EXISTS block_reasons (
    id             SERIAL PRIMARY KEY,
    label          TEXT UNIQUE NOT NULL,
    position       INT  NOT NULL DEFAULT 0,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    report_enabled BOOLEAN NOT NULL DEFAULT FALSE,   -- show as a fraud-report category
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Recipient emails per reason (used when report_enabled).
CREATE TABLE IF NOT EXISTS block_reason_emails (
    id        SERIAL PRIMARY KEY,
    reason_id INT NOT NULL REFERENCES block_reasons(id) ON DELETE CASCADE,
    email     TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_block_reason_emails ON block_reason_emails(reason_id);

-- User email (editable in app profile; required to report).
ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT;

-- Migrate the existing newline block_reasons setting into rows.
INSERT INTO block_reasons (label, position)
SELECT trim(line), ord
  FROM (
    SELECT line, row_number() OVER () AS ord
      FROM regexp_split_to_table(
             COALESCE((SELECT value FROM settings WHERE key='block_reasons'), ''),
             E'\\r?\\n') AS line
  ) t
 WHERE trim(line) <> ''
ON CONFLICT (label) DO NOTHING;

-- Shared templates (one recipient template, one user template) + subjects.
INSERT INTO settings (key, value) VALUES
  ('fraud_recipient_subject', 'Fraud report: {{number}}'),
  ('fraud_recipient_template',
   '<p>A fraud call has been reported in CyberGuard AI.</p>'
   || '<ul>'
   || '<li><b>Reported number:</b> {{number}}</li>'
   || '<li><b>Category:</b> {{category}}</li>'
   || '<li><b>Reported by:</b> {{reporter}} ({{reporter_email}})</li>'
   || '<li><b>Caller name:</b> {{caller_name}}</li>'
   || '<li><b>Block reason:</b> {{block_reason}}</li>'
   || '<li><b>Time:</b> {{date}}</li>'
   || '</ul>'),
  ('fraud_user_subject', 'We received your fraud report'),
  ('fraud_user_template',
   '<p>Hi {{reporter}},</p>'
   || '<p>Thank you for reporting <b>{{number}}</b> as <b>{{category}}</b>. '
   || 'Our team will review it. This helps protect other users.</p>'
   || '<p>- CyberGuard AI</p>')
ON CONFLICT (key) DO NOTHING;
