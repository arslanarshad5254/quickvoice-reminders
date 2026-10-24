const { app } = require("./src/app.js");
const { config } = require("./src/config.js");
const { closeDatabase } = require("./src/db.js");

// cPanel/Passenger supplies PORT automatically. A local fallback keeps the same
// startup file usable during development.
const server = app.listen(config.PORT, () => {
  console.log(`QuickVoice backend listening on port ${config.PORT}`);
});

async function shutdown(signal) {
  console.log(`Received ${signal}; shutting down`);
  server.close(async () => {
    await closeDatabase();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));

module.exports = app;
