# MigrationNode.fingerprint() — the contract

The prose here has been wrong three times in three cycles, always the same way: an **absolute
justification** written into the contract that the only implementation satisfies with a **carve-out**.
Each one was caught by an audit, never by a test, because javadoc has no assertions.

The three:

| Written | Why it was false |
|---|---|
| "the token must change only when re-applying would produce a different result" | adding a comment changes the token and nothing about the result |
| "a re-indented text gets a different token, implementations must not normalize that away" | at the time, `crlf` vs `lf` differed only in whitespace and a green test pinned them **equal** (line-ending normalization has since been removed, so that test now pins them unequal) |
| "an autocommit flag only switches transaction semantics" | autocommit exists *for* statements that cannot run in a transaction (`docs/USER_GUIDE.ja.md:401`), so flipping it turns `CREATE INDEX CONCURRENTLY` from succeeding into failing — and the token does not move |

## The rule this file exists to enforce

State only what a test pins. Phrase every reason as a **trade that names its own cost**, never as a
universal truth about SQL, databases, or migrations. "X cannot affect what is applied" is almost always
false for some dialect; "X is normalized so that one migration keeps one token across platforms, at the
cost of hiding a line ending inside a string literal" is checkable and honest.

Before editing this javadoc, read `JdbcMigrationNodeTest`'s fingerprint tests and check the sentence you
are about to write against each one. They currently pin: two distinct hash literals; that surrounding
whitespace is stripped from **both** SQL texts; that CRLF and LF inputs differ; a comment-only edit
changing the token; an interior re-indent changing the token; that the transitive dependencies handed in
are covered, one dependency `ab` differing from two dependencies `a`, `b`; that `downSql` and
`autocommit` are covered; and that an **absent** `downSql` differs from an **empty** one.

## The dependency list comes from the caller, and the framing is unambiguous

`MigrationNode.fingerprint(List<NodeId>)` receives the closure already computed and already sorted by
`MigrationGraph.canonicalTransitiveDependencies`. A plugin that re-sorts it moves the normalization out
of the one place that owns it. Each part is written as `<length>:<text>` precisely so that no two
different inputs build the same pre-image — plain concatenation would let `[ab]` and `[a, b]` hash alike,
and a separator character fails as soon as an id contains it. Changing the framing changes every token
already recorded, and nothing in a stored token says which derivation produced it.

`UpCommandTest.shouldRecordUpFingerprintInHistory` pins the whole chain against a real PostgreSQL, with
the expected values computed from the framing rather than copied from the implementation — `002_add_index`
declares a dependency, so passing an empty list instead of the closure fails it.

## The token covers the whole recorded definition, and that is a trade

`downSql` and `autocommit` are in the pre-image. The token therefore answers "does the definition still
match what was recorded", not "would re-applying produce the same object" — editing only `down:` moves it
although no database object differs. That was chosen deliberately: the caller cannot read the database, so
it cannot be the one to decide which side is right, and a token that tried to mean "the object is stale"
would be making that decision. State it that way. Do **not** justify including `autocommit` by claiming it
changes what is applied, and do not justify excluding anything by claiming it cannot.

The framing has one non-length element: an absent `downSql` is written `-`, a present one `<len>:<text>`.
`-` is not a digit, so it cannot begin a length prefix and the two stay distinguishable. Dropping that and
treating absent as empty would make "the rollback was deleted" and "the rollback was blanked" one state.

## Do not add normalization

The only normalization is `strip()`. Anything else invalidates every fingerprint already recorded, and
every candidate is a semantic bet the token has no business making:

- **Comments** — MySQL executes `/*!` version-gated comments, so removing them changes behavior.
- **Interior whitespace** — a line ending or an indent inside a string literal is content.
- **Line endings** — these *were* normalized, to compensate for `upSqlFromFile`/`upSqlFromResource`
  reading files without normalizing. Those four builders are `@Deprecated(forRemoval = true)` and the
  normalization is gone with them. Measured, not assumed: SnakeYAML 2.4 hands a CRLF block scalar back
  as 0 CR / n LF, so the YAML path — the only one the providers use — never needed it.

Two mutations were each shown to slip past the *entire* pre-existing suite before their guard test
existed — appending `downSql` only when non-null, and `replaceAll("(?m)^[ \t]+", "")`. If you find
yourself deleting one of the fingerprint tests to make a normalization change compile, that is the
guard doing its job.
