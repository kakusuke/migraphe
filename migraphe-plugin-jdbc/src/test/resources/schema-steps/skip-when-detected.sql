-- The apply statement deliberately lacks IF NOT EXISTS: it can only succeed while the table is
-- absent, so the test fails loudly if the detection query is ignored.
--@check probe table
SELECT 1 FROM information_schema.tables
 WHERE table_schema = SCHEMA() AND UPPER(table_name) = 'STEP_PROBE';
--@apply
CREATE TABLE step_probe (id INT);
