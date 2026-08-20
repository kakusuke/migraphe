-- The detection query names the current schema through the bound parameter rather than a
-- dialect-specific function, so a same-named table in another schema cannot satisfy it.
-- The apply statement deliberately lacks IF NOT EXISTS: it can only succeed while the table is
-- absent, so the test fails loudly if detection is ignored.
--@check probe table
SELECT 1 FROM information_schema.tables
 WHERE table_schema = ? AND UPPER(table_name) = 'STEP_PROBE';
--@apply
CREATE TABLE step_probe (id INT);
