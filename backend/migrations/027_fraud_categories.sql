-- ============================================================
-- Fraud report categories (multi-recipient, per-category HTML template)
-- ============================================================

CREATE TABLE IF NOT EXISTS fraud_categories (
    id            SERIAL PRIMARY KEY,
    name          TEXT NOT NULL,                 -- internal/display name
    template_html TEXT NOT NULL DEFAULT '',      -- rich-text email body (HTML)
    subject       TEXT NOT NULL DEFAULT 'Fraud report: {{number}}',
    position      INT  NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fraud_category_emails (
    id          SERIAL PRIMARY KEY,
    category_id INT NOT NULL REFERENCES fraud_categories(id) ON DELETE CASCADE,
    email       TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fraud_cat_emails_cat ON fraud_category_emails (category_id);

-- Link reports to a category and capture extra context.
ALTER TABLE fraud_reports ADD COLUMN IF NOT EXISTS category_id  INT;
ALTER TABLE fraud_reports ADD COLUMN IF NOT EXISTS caller_name  TEXT;
ALTER TABLE fraud_reports ADD COLUMN IF NOT EXISTS block_reason TEXT;

-- Seed a default "General fraud" category, and migrate any existing single
-- recipient (settings.fraud_report_email) into it so nothing is lost.
INSERT INTO fraud_categories (name, subject, template_html, position)
SELECT 'General fraud',
       'Fraud report: {{number}}',
       '<p>A fraud call has been reported in CyberGuard AI.</p>'
       || '<ul>'
       || '<li><b>Reported number:</b> {{number}}</li>'
       || '<li><b>Category:</b> {{category}}</li>'
       || '<li><b>Reported by:</b> {{reporter}}</li>'
       || '<li><b>Caller name:</b> {{caller_name}}</li>'
       || '<li><b>Block reason:</b> {{block_reason}}</li>'
       || '<li><b>Time:</b> {{date}}</li>'
       || '</ul>',
       0
WHERE NOT EXISTS (SELECT 1 FROM fraud_categories);

INSERT INTO fraud_category_emails (category_id, email)
SELECT (SELECT id FROM fraud_categories ORDER BY id ASC LIMIT 1),
       value
  FROM settings
 WHERE key = 'fraud_report_email'
   AND value IS NOT NULL AND value <> ''
   AND NOT EXISTS (SELECT 1 FROM fraud_category_emails);
