-- Enable Row Level Security on all tables and deny PostgREST / Supabase
-- client direct access.  This app uses Ktor (server-side) with a dedicated
-- DB role that bypasses RLS, so a blanket-deny policy is correct.
--
-- flyway_schema_history is owned by the migration role and is not exposed
-- via PostgREST, but we enable RLS on it too to silence the Supabase linter.

-- Supabase's connection pooler enforces a short statement_timeout.
-- ALTER TABLE … ENABLE RLS acquires ACCESS EXCLUSIVE and may wait for
-- concurrent locks, so give it more headroom.
SET LOCAL statement_timeout = '60s';

-- ── application tables ──────────────────────────────────────────────
ALTER TABLE users             ENABLE ROW LEVEL SECURITY;
ALTER TABLE themes            ENABLE ROW LEVEL SECURITY;
ALTER TABLE bests             ENABLE ROW LEVEL SECURITY;
ALTER TABLE best_items        ENABLE ROW LEVEL SECURITY;
ALTER TABLE collections       ENABLE ROW LEVEL SECURITY;
ALTER TABLE collection_cards  ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens    ENABLE ROW LEVEL SECURITY;
ALTER TABLE jwt_blocklist     ENABLE ROW LEVEL SECURITY;

-- ── flyway metadata ─────────────────────────────────────────────────
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;

-- No permissive policies are created intentionally: the Ktor backend
-- connects as the table owner (or a superuser/role with BYPASSRLS),
-- so RLS does not affect it.  Any connection through PostgREST's
-- anon / authenticated roles will be denied by default.
