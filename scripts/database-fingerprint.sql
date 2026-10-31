-- Read-only per-table fingerprint. No row values, credentials or tokens are emitted.
-- Fixed timezone and DateStyle make JSON timestamp rendering comparable across runs.
SET TIME ZONE 'UTC';
SET DateStyle TO ISO;
SELECT format(
  'SELECT json_build_object(''schema'', %L, ''table'', %L, ''count'', count(*), ''md5'', md5(coalesce(string_agg(md5(to_jsonb(t)::text), '''' ORDER BY md5(to_jsonb(t)::text)), ''''))) FROM %I.%I t;',
  schemaname, tablename, schemaname, tablename)
FROM pg_tables
WHERE schemaname IN ('public', 'duriancare_auth', 'duriancare_iot')
ORDER BY schemaname, tablename
\gexec
