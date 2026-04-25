-- Cloudflare Access is now the single source of truth for admin identity.
-- The Access policy (Google Workspace group) decides who is an admin; the
-- backend no longer maintains its own allowlist or password hashes.
DROP TABLE admin_users;
