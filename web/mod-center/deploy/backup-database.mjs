// Consistent SQLite snapshot. Copying catalog.sqlite while WAL mode is live can
// drop committed pages, so this uses the database backup API and then checks it.
import { chmodSync, mkdirSync, readdirSync, renameSync, rmSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { DatabaseSync, backup } from 'node:sqlite';

const stamp = () => new Date().toISOString().replace(/[-:]/g, '').replace('.', '');
const snapshotName = /^catalog-\d{8}T\d{9}Z\.sqlite$/;

export async function backupDatabase({ databasePath, directory, keep = 28 } = {}) {
  if (!databasePath || databasePath === ':memory:') throw new Error('DATABASE_PATH must point at the catalog file');
  const retention = Number(keep);
  if (!Number.isInteger(retention) || retention < 1 || retention > 365) throw new Error('BACKUP_KEEP must be between 1 and 365');
  mkdirSync(directory, { recursive: true });
  chmodSync(directory, 0o700);
  const filename = `catalog-${stamp()}.sqlite`;
  const temporary = path.join(directory, `.${filename}.partial`);
  const source = new DatabaseSync(databasePath);
  try { await backup(source, temporary); }
  finally { source.close(); }
  const copy = new DatabaseSync(temporary, { readOnly: true });
  try {
    const result = copy.prepare('PRAGMA integrity_check').get();
    if (result?.integrity_check !== 'ok') throw new Error('Backup failed the integrity check');
  } finally { copy.close(); }
  const destination = path.join(directory, filename);
  renameSync(temporary, destination);
  chmodSync(destination, 0o600);
  const older = readdirSync(directory).filter(name => snapshotName.test(name)).sort();
  while (older.length > retention) rmSync(path.join(directory, older.shift()));
  return destination;
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const databasePath = process.env.DATABASE_PATH || '/var/lib/endfield-resource-center/catalog.sqlite';
  const directory = process.env.BACKUP_DIRECTORY || path.join(path.dirname(databasePath), 'backups');
  const keep = process.env.BACKUP_KEEP || 28;
  backupDatabase({ databasePath, directory, keep }).then(file => {
    console.info(`Catalog backup written to ${file}`);
  }, error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
