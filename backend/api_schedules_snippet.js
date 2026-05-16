// ============================================================
// SCHEDULES — full mirror sync (client is source of truth)
// Body: { user_id, schedules: [{client_id, name, start_minute, end_minute,
//                               days_mask, is_enabled, allow_numbers,
//                               allow_names, quick_until_ms,
//                               last_toggled_at}, ...] }
// We DELETE all rows for this user and re-insert. Simpler than diff sync
// and the count per user is small (typically <20 schedules).
// ============================================================
router.post('/schedules/sync', async (req, res, next) => {
  try {
    const { user_id, schedules } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (!Array.isArray(schedules)) return res.status(400).json({ error: 'schedules array required' });

    await query('DELETE FROM schedules WHERE user_id = $1', [user_id]);

    for (const s of schedules) {
      if (!s.client_id || !s.name) continue;
      await query(
        `INSERT INTO schedules(user_id, client_id, name, start_minute, end_minute,
            days_mask, is_enabled, allow_numbers, allow_names, quick_until_ms,
            last_toggled_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9::jsonb, $10,
                 COALESCE(to_timestamp($11 / 1000.0), NOW()))`,
        [
          user_id, s.client_id, s.name,
          Math.max(0, Math.min(1439, parseInt(s.start_minute, 10) || 0)),
          Math.max(0, Math.min(1439, parseInt(s.end_minute, 10) || 0)),
          parseInt(s.days_mask, 10) || 127,
          s.is_enabled !== false,
          JSON.stringify(s.allow_numbers || []),
          JSON.stringify(s.allow_names || []),
          s.quick_until_ms || null,
          s.last_toggled_at || null
        ]
      );
    }

    await audit('android', 'schedules_sync', `user_id=${user_id}, count=${schedules.length}`);
    res.json({ ok: true, count: schedules.length });
  } catch (e) { next(e); }
});

// GET /api/schedules/list?user_id=N — pull down (used after reinstall)
router.get('/schedules/list', async (req, res, next) => {
  try {
    const userId = parseInt(req.query.user_id, 10);
    if (!userId) return res.status(400).json({ error: 'user_id required' });
    const rows = await many(
      `SELECT client_id, name, start_minute, end_minute, days_mask,
              is_enabled, allow_numbers, allow_names, quick_until_ms,
              EXTRACT(EPOCH FROM last_toggled_at) * 1000 AS last_toggled_ms
         FROM schedules
        WHERE user_id = $1
        ORDER BY id`,
      [userId]
    );
    res.json({ ok: true, schedules: rows });
  } catch (e) { next(e); }
});
