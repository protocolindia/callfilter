require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { migrate } = require('./migrate');

async function main() {
  // Run migrations on every boot. A failing migration is logged but does NOT
  // stop the server — otherwise one bad migration would freeze the backend on
  // an old build forever (deploys would keep crashing). Failures are surfaced
  // via /admin/me so they're visible without server log access.
  try {
    await migrate();
    console.log('✅ Migrations step finished');
  } catch (err) {
    console.error('⚠️ Migration step threw (continuing to start server):');
    console.error(err.stack || err.message || err);
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
