const pg = require("pg");
const { config } = require("./config.js");

const { Pool } = pg;

const pool = new Pool({
  connectionString: config.DATABASE_URL,
  ssl: config.DATABASE_SSL ? { rejectUnauthorized: false } : false,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000
});

pool.on("error", (error) => {
  console.error("Unexpected PostgreSQL pool error", error);
});

async function closeDatabase() {
  await pool.end();
}

module.exports = { pool, closeDatabase };
