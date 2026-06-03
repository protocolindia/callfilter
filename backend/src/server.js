require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { migrate } = require('./migrate');

async function main() {
  // Run migrations on every boot — Postgres handles IF NOT EXISTS gracefully
  try {
    await migrate();
    console.log('✅ Migrations OK');
  } catch (err) {
    // If migrations fail, the schema is in an unknown state — running
    // the server anyway can silently mask data-loss bugs (e.g. rules
    // sync mirror-deleting cloud rows). Fail loud so Railway surfaces
    // the error in deploy logs and the user knows to fix it.
    console.error('═══════════════════════════════════════════════════════');
    console.error('❌ MIGRATIONS FAILED — refusing to start server');
    console.error(err.stack || err.message || err);
    console.error('═══════════════════════════════════════════════════════');
    process.exit(1);
  }

  const app = express();

  // CORS — allow the React frontend (configurable via env)
  const allowed = (process.env.CORS_ORIGINS || '*')
    .split(',').map(s => s.trim()).filter(Boolean);
  app.use(cors({
    origin: allowed.includes('*') ? true : allowed,
    credentials: true
  }));

  app.use(express.json({ limit: '25mb' }));
  app.use(express.urlencoded({ extended: true, limit: '25mb' }));

  // Routes
  app.use('/api', require('./api'));
  app.use('/admin', require('./admin'));

  // Root probe
  app.get('/', (req, res) => res.json({
    service: 'callfilter-backend',
    ok: true,
    docs: '/api/health'
  }));

  // 404
  app.use((req, res) => res.status(404).json({ error: 'Not found' }));

  // Error handler
  app.use((err, req, res, next) => {
    console.error(err);
    res.status(500).json({ error: err.message || 'Server error' });
  });

  const PORT = process.env.PORT || 3000;
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Backend listening on http://0.0.0.0:${PORT}`);
  });
}

main().catch(err => {
  console.error('Fatal startup error:', err);
  process.exit(1);
});
