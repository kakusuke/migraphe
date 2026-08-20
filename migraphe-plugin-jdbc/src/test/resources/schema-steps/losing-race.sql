-- Simulates losing a race: the first statement creates the table, the second fails exactly the way
-- a competing process's ALTER would. Re-running the detection query then reports the step as
-- applied, so initialize() must swallow the failure instead of propagating it.
--@check race target
SELECT 1 FROM information_schema.tables
 WHERE table_schema = SCHEMA() AND UPPER(table_name) = 'RACE_TARGET';
--@apply
CREATE TABLE race_target (id INT);
CREATE TABLE race_target (id INT);
