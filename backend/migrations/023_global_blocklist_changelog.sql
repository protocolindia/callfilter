-- ============================================================
-- Global blocklist change-log for scalable incremental (delta) sync
-- ============================================================
-- Every mutation to global_blocklist is recorded here by a trigger, so devices
-- can pull only what changed since their last cursor (monotonic id). This works
-- for inserts, updates, soft-deletes AND super_admin hard-deletes.

CREATE TABLE IF NOT EXISTS global_blocklist_changes (
    id          BIGSERIAL PRIMARY KEY,
    number      TEXT NOT NULL,
    reason      TEXT,
    admin_id    BIGINT,
    op          TEXT NOT NULL,            -- 'upsert' | 'remove'
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_gbl_changes_id ON global_blocklist_changes (id);

CREATE OR REPLACE FUNCTION gbl_log_change() RETURNS trigger AS $$
BEGIN
  IF (TG_OP = 'DELETE') THEN
    INSERT INTO global_blocklist_changes(number, reason, admin_id, op)
      VALUES (OLD.number, OLD.reason, OLD.added_by_admin_id, 'remove');
    RETURN OLD;
  ELSE
    IF (NEW.active = TRUE AND NEW.deleted_at IS NULL) THEN
      INSERT INTO global_blocklist_changes(number, reason, admin_id, op)
        VALUES (NEW.number, NEW.reason, NEW.added_by_admin_id, 'upsert');
    ELSE
      INSERT INTO global_blocklist_changes(number, reason, admin_id, op)
        VALUES (NEW.number, NEW.reason, NEW.added_by_admin_id, 'remove');
    END IF;
    RETURN NEW;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_gbl_log ON global_blocklist;
CREATE TRIGGER trg_gbl_log
  AFTER INSERT OR UPDATE OR DELETE ON global_blocklist
  FOR EACH ROW EXECUTE FUNCTION gbl_log_change();

-- Backfill: one 'upsert' change per currently-active entry so a device syncing
-- from cursor 0 receives the whole list through the same delta path.
INSERT INTO global_blocklist_changes(number, reason, admin_id, op, changed_at)
  SELECT number, reason, added_by_admin_id, 'upsert', NOW()
    FROM global_blocklist
   WHERE active = TRUE AND deleted_at IS NULL;
