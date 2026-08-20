package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Parses a history-schema initialization resource into {@link SchemaStep}s.
 *
 * <p>Scripts describe their steps with two directive lines. {@code --@apply} introduces the
 * statements applying a step; the optional {@code --@check} preceding it declares a detection query
 * that lets the step be skipped once it is in place. Steps that create objects normally omit {@code
 * --@check} and rely on {@code IF NOT EXISTS} for idempotency; a detection query is what makes
 * steps expressible that have no portable conditional form, such as {@code ALTER TABLE ... ADD
 * COLUMN}.
 *
 * <p>Parsing runs in three stages, each with its own responsibility:
 *
 * <ol>
 *   <li>{@link #sliceByDirective} cuts the text into {@link Chunk}s — one per directive, holding
 *       the lines up to the next directive. It knows nothing about steps; its only rule is that
 *       lines before the first directive are a file header and must be blank or {@code --}
 *       comments, so SQL is never silently discarded.
 *   <li>{@link #groupIntoSteps} pairs those chunks into {@link Group}s using nothing but their
 *       kinds: {@code --@check} always opens a new step, and {@code --@apply} takes the pending
 *       detection query, if any, as its own.
 *   <li>{@link #buildStep} turns one group into a {@link SchemaStep}, resolving the label and
 *       splitting the sections into statements. This is the only stage that needs a {@link
 *       StatementSplitter}.
 * </ol>
 *
 * <p><strong>Invariants</strong>, each verified where the value it constrains comes into being, so
 * that no malformed step can exist in the first place:
 *
 * <ol>
 *   <li>the step has an {@code --@apply} section — {@link Group};
 *   <li>the step declares no two <em>different</em> labels — {@link Group};
 *   <li>a declared detection query is exactly one statement — {@link #buildStep};
 *   <li>the apply section holds at least one statement — {@link SchemaStep}.
 * </ol>
 *
 * <p>Label uniqueness across steps is deliberately <em>not</em> required: two steps may carry the
 * same label. A label is only ever interpolated into an exception message (see {@link
 * SchemaStep#label()}); it is never persisted, matched or compared, so uniqueness would buy nothing
 * and would reject resources that are otherwise perfectly well formed.
 *
 * <p><strong>Labels.</strong> Either directive may carry trailing text naming the step. Whichever
 * one declares it wins; if both do, invariant 2 requires them to be equal after trimming, compared
 * case-sensitively. A step that declares no label gets the positional default {@code step N},
 * counting from 1.
 *
 * <p>A resource containing no directive at all is parsed as a single unconditional step labelled
 * {@code schema}, which keeps plain-SQL resources supplied through {@link
 * JdbcHistoryRepository#JdbcHistoryRepository(JdbcEnvironment, String)} working unchanged.
 */
final class SchemaStepParser {

    private static final String CHECK_DIRECTIVE = "--@check";
    private static final String APPLY_DIRECTIVE = "--@apply";

    /** Label given to the implicit step of a resource that carries no directive at all. */
    private static final String PLAIN_SQL_LABEL = "schema";

    private SchemaStepParser() {}

    /** Which directive a line declares. */
    private enum Kind {
        CHECK,
        APPLY
    }

    /**
     * A directive line, decoded once per line.
     *
     * @param kind which directive the line declares
     * @param label the trailing label, or {@code null} when the directive is bare
     */
    private record Directive(Kind kind, @Nullable String label) {}

    /**
     * A directive together with the lines it introduces, as cut by {@link #sliceByDirective}.
     *
     * @param kind which directive opened this chunk
     * @param label the label the directive declared, or {@code null} when it was bare
     * @param body the text between this directive and the next one
     */
    private record Chunk(Kind kind, @Nullable String label, String body) {

        static Chunk of(Directive directive, String body) {
            return new Chunk(directive.kind(), directive.label(), body);
        }
    }

    /**
     * The chunks of one step, as paired by {@link #groupIntoSteps}.
     *
     * <p>A group cannot exist in a malformed state: its constructor enforces invariants 1 and 2, so
     * {@link #groupIntoSteps} reports a detection query left without its apply section simply by
     * trying to pair it. {@code apply} is declared nullable only so that call sites can express
     * that absence; a constructed group always has one.
     *
     * @param check the chunk opened by {@code --@check}, or {@code null} if the step declared none
     * @param apply the chunk opened by {@code --@apply}
     */
    private record Group(@Nullable Chunk check, @Nullable Chunk apply) {

        Group {
            if (apply == null) {
                throw new IllegalArgumentException(checkWithoutApplyMessage(check)); // invariant 1
            }
            requireAtMostOneLabel(check, apply); // invariant 2
        }

        @Nullable String declaredLabel() {
            @Nullable String fromCheck = check == null ? null : check.label();
            @Nullable String fromApply = apply == null ? null : apply.label();
            return fromCheck != null ? fromCheck : fromApply;
        }
    }

    /**
     * Parses the given resource text into ordered steps.
     *
     * @param resourceText the full text of the schema resource
     * @return the parsed steps, in file order
     * @throws IllegalArgumentException if the resource is malformed: SQL precedes the first
     *     directive, or any of the four step invariants documented on this class is violated
     */
    static List<SchemaStep> parse(String resourceText) {
        StatementSplitter splitter = StatementSplitter.standard();
        List<Chunk> chunks = sliceByDirective(resourceText);
        List<Group> groups = groupIntoSteps(chunks);
        return IntStream.range(0, groups.size())
                .mapToObj(index -> buildStep(splitter, groups.get(index), index + 1))
                .toList();
    }

    /**
     * Stage 1: cuts the resource into one chunk per directive.
     *
     * <p>Purely mechanical — a directive line starts a chunk, and every following line belongs to
     * it until the next directive. The only rule enforced here concerns the text before the first
     * directive, which must carry no SQL.
     *
     * @param resourceText the full text of the schema resource
     * @return the chunks, in file order; never empty
     * @throws IllegalArgumentException if SQL precedes the first directive
     */
    private static List<Chunk> sliceByDirective(String resourceText) {
        List<Chunk> chunks = new ArrayList<>();
        @Nullable Directive open = null;
        StringBuilder body = new StringBuilder();
        @Nullable String sqlBeforeFirstDirective = null;

        for (String line : resourceText.split("\n", -1)) {
            @Nullable Directive directive = parseDirective(line);
            if (directive == null) {
                if (open == null && sqlBeforeFirstDirective == null && !isTrivia(line)) {
                    sqlBeforeFirstDirective = line.trim();
                }
                body.append(line).append('\n');
                continue;
            }
            if (open == null) {
                requireNoStatementBeforeFirstDirective(sqlBeforeFirstDirective);
            } else {
                chunks.add(Chunk.of(open, body.toString()));
            }
            open = directive;
            body = new StringBuilder();
        }

        if (open == null) {
            // No directive anywhere. The whole resource becomes one unconditional apply chunk;
            // this path deliberately skips the header requirement, because a plain-SQL resource
            // is nothing but the statements that requirement would otherwise reject. Returning a
            // chunk rather than an empty list keeps the later stages free of a special case.
            return List.of(new Chunk(Kind.APPLY, PLAIN_SQL_LABEL, resourceText));
        }
        chunks.add(Chunk.of(open, body.toString()));
        return List.copyOf(chunks);
    }

    /**
     * Stage 2: pairs chunks into steps, looking only at their kinds.
     *
     * <p>Only the previous chunk matters, so the whole stage is one pending detection query. {@code
     * --@check} always opens a new step, because a detection query can only precede its apply
     * section; meeting a second one therefore leaves the first without an apply section, and
     * pairing it reports that (invariant 1). {@code --@apply} takes the pending detection query, if
     * any, as its own.
     *
     * @param chunks the chunks cut by {@link #sliceByDirective}
     * @return the groups, in file order
     * @throws IllegalArgumentException if a detection query has no apply section (invariant 1), or
     *     a step declares two different labels (invariant 2)
     */
    private static List<Group> groupIntoSteps(List<Chunk> chunks) {
        List<Group> groups = new ArrayList<>();
        @Nullable Chunk pendingCheck = null;

        for (Chunk chunk : chunks) {
            if (chunk.kind() == Kind.CHECK) {
                if (pendingCheck != null) {
                    groups.add(new Group(pendingCheck, null)); // throws: invariant 1
                }
                pendingCheck = chunk;
            } else {
                groups.add(new Group(pendingCheck, chunk));
                pendingCheck = null;
            }
        }
        if (pendingCheck != null) {
            groups.add(new Group(pendingCheck, null)); // throws: invariant 1
        }
        return List.copyOf(groups);
    }

    /**
     * Stage 3: builds one group's step.
     *
     * <p>Invariants 1 and 2 already hold, having been enforced when the group was constructed, and
     * invariant 4 is enforced by {@link SchemaStep} itself. What is left here is resolving the
     * label and splitting the sections, which is where invariant 3 belongs: it constrains a count
     * that only the splitter can produce.
     *
     * @param splitter the splitter separating the sections into statements
     * @param group the group to build
     * @param position the group's 1-based position, used for the default label
     * @return the parsed step
     * @throws IllegalArgumentException if a declared detection query is not exactly one statement
     *     (invariant 3), or the apply section holds no statement (invariant 4)
     */
    private static SchemaStep buildStep(StatementSplitter splitter, Group group, int position) {
        @Nullable String declared = group.declaredLabel();
        String label = declared == null ? "step " + position : declared;

        @Nullable Chunk check = group.check();
        @Nullable String checkSql = check == null ? null : singleStatement(splitter, label, check);

        Chunk apply =
                Objects.requireNonNull(group.apply(), "invariant 1 guarantees an apply chunk");
        return new SchemaStep(label, checkSql, splitter.split(apply.body()));
    }

    /**
     * Returns a detection chunk's sole statement.
     *
     * @param splitter the splitter separating the chunk into statements
     * @param label the step's label, used in diagnostics
     * @param check the detection chunk
     * @return the single detection statement
     * @throws IllegalArgumentException if the chunk does not hold exactly one statement (invariant
     *     3)
     */
    private static String singleStatement(StatementSplitter splitter, String label, Chunk check) {
        List<String> statements = splitter.split(check.body());
        if (statements.size() != 1) {
            throw new IllegalArgumentException(
                    "Step '"
                            + label
                            + "' must declare a single detection statement, found "
                            + statements.size());
        }
        return statements.get(0);
    }

    /**
     * Builds the message rejecting a detection query that never reached its apply section.
     *
     * <p>The position that names an otherwise unlabelled step is not known here — steps are
     * numbered once they are built — so an unlabelled section is described rather than named.
     *
     * @param check the chunk left without an apply section, or {@code null}
     * @return the exception message
     */
    private static String checkWithoutApplyMessage(@Nullable Chunk check) {
        @Nullable String label = check == null ? null : check.label();
        String subject =
                label == null
                        ? "A " + CHECK_DIRECTIVE + " section"
                        : "A " + CHECK_DIRECTIVE + " section labelled '" + label + "'";
        return subject + " has no " + APPLY_DIRECTIVE + " section";
    }

    /**
     * Rejects a step whose two directives declare different labels.
     *
     * @param check the chunk opened by {@code --@check}, or {@code null}
     * @param apply the chunk opened by {@code --@apply}
     * @throws IllegalArgumentException if the two directives declare different labels (invariant 2)
     */
    private static void requireAtMostOneLabel(@Nullable Chunk check, Chunk apply) {
        @Nullable String fromCheck = check == null ? null : check.label();
        @Nullable String fromApply = apply.label();
        if (fromCheck != null && fromApply != null && !fromCheck.equals(fromApply)) {
            throw new IllegalArgumentException(
                    "A step must declare at most one label, but "
                            + CHECK_DIRECTIVE
                            + " declares '"
                            + fromCheck
                            + "' and "
                            + APPLY_DIRECTIVE
                            + " declares '"
                            + fromApply
                            + "'");
        }
    }

    /**
     * Decodes a line as a directive.
     *
     * @param line the line to inspect
     * @return the directive the line declares, or {@code null} if it declares none
     */
    private static @Nullable Directive parseDirective(String line) {
        String trimmed = line.trim();
        @Nullable String checkLabel = directiveArgument(trimmed, CHECK_DIRECTIVE);
        if (checkLabel != null) {
            return new Directive(Kind.CHECK, emptyToNull(checkLabel));
        }
        @Nullable String applyLabel = directiveArgument(trimmed, APPLY_DIRECTIVE);
        if (applyLabel != null) {
            return new Directive(Kind.APPLY, emptyToNull(applyLabel));
        }
        return null;
    }

    /**
     * Returns the text following the directive on a directive line.
     *
     * @param trimmedLine the line to inspect, already trimmed
     * @param directive the directive to match at the start of the line
     * @return the trimmed remainder of the line (possibly empty) when the line declares {@code
     *     directive}, otherwise {@code null}
     */
    private static @Nullable String directiveArgument(String trimmedLine, String directive) {
        if (!trimmedLine.startsWith(directive)) {
            return null;
        }
        String rest = trimmedLine.substring(directive.length());
        if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0))) {
            return null;
        }
        return rest.trim();
    }

    /**
     * Normalizes a directive's trailing text: a bare directive declares no label.
     *
     * @param directiveArgument the trimmed text following a directive
     * @return the declared label, or {@code null} if the directive was bare
     */
    private static @Nullable String emptyToNull(String directiveArgument) {
        return directiveArgument.isEmpty() ? null : directiveArgument;
    }

    /**
     * Returns whether a line carries no SQL.
     *
     * @param line the line to inspect
     * @return {@code true} if the line is blank or a {@code --} comment
     */
    private static boolean isTrivia(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("--");
    }

    /**
     * Rejects a resource whose header carries SQL.
     *
     * @param statement the first non-trivia line seen before the first directive, or {@code null}
     * @throws IllegalArgumentException if {@code statement} is non-{@code null}
     */
    private static void requireNoStatementBeforeFirstDirective(@Nullable String statement) {
        if (statement != null) {
            throw new IllegalArgumentException(
                    "Statements must follow a "
                            + CHECK_DIRECTIVE
                            + " or "
                            + APPLY_DIRECTIVE
                            + " directive; found SQL before the first one: "
                            + statement);
        }
    }
}
