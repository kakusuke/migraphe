package io.github.kakusuke.migraphe.jdbc;

import io.github.kakusuke.migraphe.jdbc.statement.StatementSplitter;
import java.util.ArrayList;
import java.util.List;
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
 * <p><strong>Grouping.</strong> {@code --@check} always opens a new step. {@code --@apply} fills
 * the empty apply slot of the step being read, or opens a new step when that slot is already taken.
 * Lines before the first directive are a file header and must be blank or {@code --} comments, so
 * SQL is never silently discarded.
 *
 * <p><strong>Invariants</strong>, verified per step:
 *
 * <ol>
 *   <li>the step has an {@code --@apply} section;
 *   <li>the step declares no two <em>different</em> labels;
 *   <li>a declared detection query is exactly one statement;
 *   <li>the apply section holds at least one statement.
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

    private SchemaStepParser() {}

    /** Which section of a step the parser is currently reading. */
    private enum Section {
        /** No step is open: either the file header, or the gap right after a step was closed. */
        NONE,
        /** The detection query of the step being read. */
        CHECK,
        /** The apply statements of the step being read. */
        APPLY
    }

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
     * Parses the given resource text into ordered steps.
     *
     * @param resourceText the full text of the schema resource
     * @return the parsed steps, in file order
     * @throws IllegalArgumentException if the resource is malformed: SQL precedes the first
     *     directive, or any of the four step invariants documented on this class is violated
     */
    static List<SchemaStep> parse(String resourceText) {
        StatementSplitter splitter = StatementSplitter.standard();
        List<SchemaStep> steps = new ArrayList<>();

        Section section = Section.NONE;
        StringBuilder body = new StringBuilder();
        @Nullable StringBuilder check = null;
        @Nullable String declaredLabel = null;
        @Nullable String statementBeforeFirstDirective = null;

        for (String line : resourceText.split("\n", -1)) {
            @Nullable Directive directive = parseDirective(line);

            if (directive == null) {
                if (section == Section.NONE
                        && statementBeforeFirstDirective == null
                        && !isTrivia(line)) {
                    statementBeforeFirstDirective = line.trim();
                }
                body.append(line).append('\n');
                continue;
            }

            // Close whatever precedes this directive.
            if (section == Section.NONE) {
                requireNoStatementBeforeFirstDirective(statementBeforeFirstDirective);
                body.setLength(0); // drop the header comments
            } else if (directive.kind() == Kind.CHECK || section == Section.APPLY) {
                steps.add(
                        closeStep(
                                splitter,
                                labelOrPosition(declaredLabel, steps.size()),
                                section,
                                check,
                                body));
                section = Section.NONE;
                check = null;
                declaredLabel = null;
                body = new StringBuilder();
            }

            // Open a step, or fill the current one's apply slot.
            if (directive.kind() == Kind.CHECK) {
                declaredLabel = directive.label();
                section = Section.CHECK;
            } else if (section == Section.CHECK) {
                declaredLabel = mergeLabels(declaredLabel, directive.label());
                check = body;
                body = new StringBuilder();
                section = Section.APPLY;
            } else {
                declaredLabel = directive.label();
                section = Section.APPLY;
            }
        }

        if (section == Section.NONE) {
            // No directive anywhere. The whole resource is one unconditional step; this path
            // deliberately skips the header requirement, because a plain-SQL resource is nothing
            // but the statements it would otherwise reject.
            return List.of(new SchemaStep("schema", null, splitter.split(body.toString())));
        }
        steps.add(
                closeStep(
                        splitter,
                        labelOrPosition(declaredLabel, steps.size()),
                        section,
                        check,
                        body));
        return List.copyOf(steps);
    }

    /**
     * Closes the step being read.
     *
     * @param splitter the splitter separating the sections into statements
     * @param label the step's label, used in diagnostics
     * @param section the section the parser stopped in
     * @param check the accumulated detection query, or {@code null} if the step declared none
     * @param apply the accumulated apply statements
     * @return the parsed step
     * @throws IllegalArgumentException if the step never reached its {@code --@apply} section
     *     (invariant 1), or {@link #buildStep} rejects its contents
     */
    private static SchemaStep closeStep(
            StatementSplitter splitter,
            String label,
            Section section,
            @Nullable StringBuilder check,
            StringBuilder apply) {
        if (section != Section.APPLY) {
            throw new IllegalArgumentException(
                    "Step '" + label + "' has no " + APPLY_DIRECTIVE + " section");
        }
        return buildStep(
                splitter, label, check == null ? null : check.toString(), apply.toString());
    }

    /**
     * Splits a step's sections into statements and enforces the invariants on their counts.
     *
     * @param splitter the splitter separating the sections into statements
     * @param label the step's label, used in diagnostics
     * @param checkBody the detection query text, or {@code null} if the step declared none
     * @param applyBody the apply section's text
     * @return the parsed step
     * @throws IllegalArgumentException if a declared detection query is not exactly one statement
     *     (invariant 3), or the apply section holds no statement (invariant 4)
     */
    private static SchemaStep buildStep(
            StatementSplitter splitter,
            String label,
            @Nullable String checkBody,
            String applyBody) {
        @Nullable String checkSql = null;
        if (checkBody != null) {
            List<String> checkStatements = splitter.split(checkBody);
            if (checkStatements.size() != 1) {
                throw new IllegalArgumentException(
                        "Step '"
                                + label
                                + "' must declare a single detection statement, found "
                                + checkStatements.size());
            }
            checkSql = checkStatements.get(0);
        }
        List<String> applyStatements = splitter.split(applyBody);
        if (applyStatements.isEmpty()) {
            throw new IllegalArgumentException(
                    "Step '" + label + "' has an empty " + APPLY_DIRECTIVE + " section");
        }
        return new SchemaStep(label, checkSql, applyStatements);
    }

    /**
     * Combines the labels declared by a step's two directives.
     *
     * @param fromCheck the label declared by {@code --@check}, or {@code null}
     * @param fromApply the label declared by {@code --@apply}, or {@code null}
     * @return the single declared label, or {@code null} if neither directive declared one
     * @throws IllegalArgumentException if the two directives declare different labels (invariant 2)
     */
    private static @Nullable String mergeLabels(
            @Nullable String fromCheck, @Nullable String fromApply) {
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
        return fromCheck != null ? fromCheck : fromApply;
    }

    /**
     * Returns the declared label, or the positional default when the step declared none.
     *
     * @param declared the label declared by the step's directives, or {@code null}
     * @param completedSteps the number of steps parsed so far
     * @return the label to use in diagnostics
     */
    private static String labelOrPosition(@Nullable String declared, int completedSteps) {
        return declared == null ? "step " + (completedSteps + 1) : declared;
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
