-- The detection query itself fails. Such a failure must propagate: mistaking it for "not applied"
-- would turn permission errors and broken connections into blind DDL attempts.
--@check broken detection
SELECT 1 FROM information_schema.no_such_view;
--@apply
CREATE TABLE unreachable (id INT);
