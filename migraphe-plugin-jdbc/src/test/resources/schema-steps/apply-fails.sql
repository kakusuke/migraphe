-- The apply statement always fails and the detection query never matches afterwards, so the
-- failure must propagate rather than be mistaken for a lost race.
--@check never applied
SELECT 1 FROM information_schema.tables
 WHERE table_schema = SCHEMA() AND UPPER(table_name) = 'NEVER_CREATED';
--@apply
CREATE TABLE never_created (id NOT_A_TYPE);
