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
are about to write against each one. They currently pin: the framed signature handed over for each
direction, literally; that surrounding whitespace is stripped from **both** SQL texts; that CRLF and LF
inputs differ; a comment-only edit changing the signature; an interior re-indent changing it; that each
direction's `autocommit` rides in that direction's signature; and that a **blank** `downSql` is refused
rather than signed.

What the *node* tests no longer cover, because it moved to core: the closure, the three attributes core
reads off the node, and the digest itself. Those are `MigrationGraphTest`'s `fingerprinterFor_*` tests —
check both files, not one.

## Two levels of framing, and neither may be concatenation

`MigrationNode.fingerprint(Fingerprinter)` hands core one signature per direction; core owns
everything else — the three attributes it reads off the node, the closure and the digest — and the
plugin never sees any of it.

| level | who | shape |
|---|---|---|
| task signature | `JdbcUpTask` / `JdbcDownTask` via `SignatureFraming` | `<len>:<stripped sql><len>:<t\|f>` |
| pre-image | `MigrationGraph.fold` | `<len>:<name>` `<noWayBack>` `<len>:<target id>` `<len>:<signature>`… `/` `<len>:<closure id>`… |

The three core-read parts come **first**, at a fixed count, so they are consumed before anything of
variable length; put last they would have to be counted back from the end of the closure. `fold` takes
the `MigrationNode` and `fingerprinterFor` looks it up from the id it was already given — nothing is
handed to a caller, which is the same property the closure has and for the same reason.

**The target is its id, not its name** (`node.target().id().value()`) — the value the history's
`target_id` column holds.

**A fixture meant to isolate one part of the pre-image must pin `name` and `target` explicitly.**
`TestHelpers.node(id)` defaults the name to the id, so two nodes with different ids already differ in
the pre-image before anything else does — which silently disarmed both
`fingerprinterFor_keepsTheSignaturesApartFromTheClosure` and
`fingerprinterFor_foldsInTheSignaturesAndTheNodesOwnClosure` the moment `name` entered the fold. They
kept passing while the separator and the whole closure loop could be deleted. After changing what the
pre-image holds, re-run the mutation: delete `preimage.append('/')`, then the closure loop, and
confirm each still fails a test.

Each part is length-prefixed precisely so that no two different inputs build the same pre-image —
plain concatenation would let `[ab]` and `[a, b]` hash alike, and a separator character fails as soon
as the text contains it. **Both levels need it.** The task level got it late: `sql + (autocommit ?
"\nautocommit" : "")` let a statement ending in `\nautocommit` produce the token of the same
statement with the mode set.

Whether a rollback exists is carried by **arity**, not by a marker: one signature means none, two
means there is one. The one `-` in the pre-image is elsewhere — see below.

Changing either framing invalidates every expected value in the repo at once — currently the two
digests in `UpCommandTest.shouldRecordUpFingerprintInHistory`, the digest in
`MigrationGraphTest.fingerprinterFor_foldsTheNameTheNoWayBackReasonAndTheTargetAheadOfTheSignatures`,
and the three framed signature literals in `JdbcMigrationNodeTest`. Recompute each from the framing in a scratch script *before* editing the
production line, never from what the new implementation prints: copying the output makes the test
agree with whatever was written, including a mistake. (`JdbcHistoryRepositoryTest`'s hex literal is an
opaque value round-tripped through the column, not a derived one — leave it alone.)

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

**Absent and blank are separated by two different mechanisms, and neither is a `-` marker.** A node with
no rollback hands over one signature; a node with one hands over two, so arity carries absence
(`JdbcMigrationNode.fingerprint`, `SimpleMigrationNode.fingerprint`). A *blank* `downSql` never reaches
the fold at all — `JdbcDownTask.create` rejects it, which
`fingerprintRefusesToSignARollbackThatCannotBeRun` pins. Do not reintroduce a sentinel here: the only
live `-` is the one `MigrationGraph.fold` writes at the **fixed** position for an absent
`no_way_back:`, where arity cannot speak because the part is not optional. Different sentinel,
different level, different reason.

## Each direction's autocommit rides in that direction's own signature

A fixture that varies autocommit **must** use `autocommitUp(boolean)` / `autocommitDown(boolean)`:
the builder's `autocommit(boolean)` sets both directions, so a test using it moves both flags and
cannot fail when only one of them stops being covered.
`fingerprintCarriesEachDirectionsAutocommitInThatDirectionsSignature` is the one that can, and it
asserts the two framed signatures literally (`…1:t` for UP, `…1:f` for DOWN) rather than mere
inequality, so a flag dropped from one direction fails it with a value rather than silently comparing
two identical tokens.

One mutation it does **not** catch: a DOWN flag hardcoded to `false`. Every down fixture in
`JdbcMigrationNodeTest` sets it false, so the literals agree either way. The guard for that lives in a
different class — `JdbcDownTaskTest.signatureCoversTheRollbackSqlAndItsOwnMode`. Don't delete it
believing the node tests cover it.

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
