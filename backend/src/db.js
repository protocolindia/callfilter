const { Pool } = require('pg');

// Railway provides DATABASE_URL automatically when you attach a Postgres service
// to your backend. Locally, set it in backend/.env
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL && process.env.DATABASE_URL.includes('localhost')
    ? false
    : { rejectUnauthorized: false }
});

pool.on('error', err => console.error('Postgres pool error:', err));

async function query(text, params) {
  return pool.query(text, params);
}

async function one(text, params) {
  const r = await pool.query(text, params);
  return r.rows[0] || null;
}

async function many(text, params) {
  const r = await pool.query(text, params);
  return r.rows;
}

module.exports = { pool, query, one, many };
