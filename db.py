"""Tiny database layer: SQLite locally, Postgres when DATABASE_URL (or POSTGRES_URL) is set.

Why: on serverless hosts like Vercel the local disk is temporary, so a SQLite file is wiped
whenever the app restarts. A hosted Postgres database (e.g. Neon, free) keeps accounts,
posts, chat, edits and uploaded PDFs permanently.

The rest of the app writes SQL with `?` placeholders (SQLite style); this module converts
them for Postgres and makes rows readable both as row["column"] and row[0].
"""
import os
import re
import sqlite3


def database_url():
    url = (os.getenv("DATABASE_URL") or os.getenv("POSTGRES_URL") or "").strip()
    return url if url.startswith(("postgres://", "postgresql://")) else ""


def using_postgres():
    return bool(database_url())


SQLITE_SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'user',
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS posts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL DEFAULT '',
  code TEXT,
  due TEXT,
  marks INTEGER,
  years TEXT NOT NULL,
  sections TEXT NOT NULL,
  pdf TEXT,
  author_id INTEGER NOT NULL REFERENCES users(id),
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_by TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS otps (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL,
  purpose TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  payload TEXT NOT NULL,
  expires_at REAL NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  sent_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id),
  body TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS files (
  name TEXT PRIMARY KEY,
  data BLOB NOT NULL,
  created_at TEXT NOT NULL
)
"""

POSTGRES_SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'user',
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS posts (
  id SERIAL PRIMARY KEY,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL DEFAULT '',
  code TEXT,
  due TEXT,
  marks INTEGER,
  years TEXT NOT NULL,
  sections TEXT NOT NULL,
  pdf TEXT,
  author_id INTEGER NOT NULL REFERENCES users(id),
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_by TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS otps (
  id SERIAL PRIMARY KEY,
  email TEXT NOT NULL,
  purpose TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  payload TEXT NOT NULL,
  expires_at DOUBLE PRECISION NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  sent_at DOUBLE PRECISION NOT NULL
);
CREATE TABLE IF NOT EXISTS messages (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  body TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS files (
  name TEXT PRIMARY KEY,
  data BYTEA NOT NULL,
  created_at TEXT NOT NULL
)
"""

# columns added after the first release (added automatically if missing)
EXTRA_USER_COLUMNS = [
    ("chat_ban_until", "REAL NOT NULL DEFAULT 0", "DOUBLE PRECISION NOT NULL DEFAULT 0"),
    ("chat_ban_by", "TEXT", "TEXT"),
    ("last_login_at", "TEXT", "TEXT"),
]

TABLES_WITH_ID = ("users", "posts", "otps", "messages")


class Row(dict):
    """A dict that also allows row[0], like sqlite3.Row."""

    def __getitem__(self, key):
        if isinstance(key, int):
            return list(self.values())[key]
        return super().__getitem__(key)


class _Result:
    def __init__(self, rows, lastrowid=None):
        self._rows = rows
        self.lastrowid = lastrowid

    def fetchone(self):
        return self._rows[0] if self._rows else None

    def fetchall(self):
        return self._rows


class PostgresConnection:
    def __init__(self, url):
        import psycopg
        self._psycopg = psycopg
        self._conn = psycopg.connect(url, autocommit=False, connect_timeout=10)

    def execute(self, sql, params=()):
        sql = sql.replace("?", "%s")
        returning = False
        m = re.match(r"\s*INSERT\s+INTO\s+(\w+)", sql, re.I)
        if m and m.group(1).lower() in TABLES_WITH_ID and "RETURNING" not in sql.upper():
            sql += " RETURNING id"
            returning = True
        cur = self._conn.cursor()
        cur.execute(sql, tuple(bytes(p) if isinstance(p, memoryview) else p for p in params))
        rows = []
        if cur.description:
            names = [d.name if hasattr(d, "name") else d[0] for d in cur.description]
            rows = [Row(zip(names, r)) for r in cur.fetchall()]
        lastrowid = rows[0]["id"] if returning and rows else None
        return _Result(rows, lastrowid)

    def executescript(self, script):
        for stmt in [s.strip() for s in script.split(";") if s.strip()]:
            self.execute(stmt)

    def commit(self):
        self._conn.commit()

    def rollback(self):
        self._conn.rollback()

    def close(self):
        self._conn.close()


def connect(sqlite_path):
    """Open a connection: Postgres if configured, else the SQLite file."""
    if using_postgres():
        return PostgresConnection(database_url())
    conn = sqlite3.connect(sqlite_path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def integrity_errors():
    errors = [sqlite3.IntegrityError]
    try:
        import psycopg
        errors.append(psycopg.IntegrityError)
    except ImportError:
        pass
    return tuple(errors)


def create_schema(conn):
    pg = isinstance(conn, PostgresConnection)
    conn.executescript(POSTGRES_SCHEMA if pg else SQLITE_SCHEMA)
    for name, sqlite_type, pg_type in EXTRA_USER_COLUMNS:
        if pg:
            conn.execute(f"ALTER TABLE users ADD COLUMN IF NOT EXISTS {name} {pg_type}")
        else:
            try:
                conn.execute(f"ALTER TABLE users ADD COLUMN {name} {sqlite_type}")
            except sqlite3.OperationalError:
                pass  # already exists
    conn.commit()
