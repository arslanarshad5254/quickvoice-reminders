const { readdir, readFile } = require("node:fs/promises");
const { join } = require("node:path");
const { pool, closeDatabase } = require("./db.js");

const migrationsDir = join(__dirname, "migrations");

async function migrate() {
  const files = (await readdir(migrationsDir)).filter((name) => name.endsWith(".sql")).sort();
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        name TEXT PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
    `);
    for (const name of files) {
      const exists = await client.query("SELECT 1 FROM schema_migrations WHERE name = $1", [name]);
      if (exists.rowCount) continue;
      await client.query(await readFile(join(migrationsDir, name), "utf8"));
      await client.query("INSERT INTO schema_migrations(name) VALUES ($1)", [name]);
      console.log(`Applied migration ${name}`);
    }
    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

migrate()
  .then(() => closeDatabase())
  .catch(async (error) => {
    console.error(error);
    await closeDatabase();
    process.exitCode = 1;
  });
