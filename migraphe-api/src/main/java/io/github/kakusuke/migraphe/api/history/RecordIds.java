package io.github.kakusuke.migraphe.api.history;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Generates the identifiers carried by {@link ExecutionRecord#id()}.
 *
 * <p>Identifiers are UUIDv7 values (RFC 9562): the leading 48 bits hold the generation time in
 * milliseconds, so the canonical lowercase-hex form sorts lexicographically in generation order.
 * That property makes the identifier usable as a tie-breaker when several records share an {@code
 * executed_at} value, which happens whenever the timestamp is stored at second granularity — as it
 * is on MariaDB, where the MySQL driver drops fractional seconds because the server reports itself
 * as version 5.5.5.
 *
 * <p>Ordering is preserved within a millisecond as well: the 12-bit {@code rand_a} field is used as
 * a dedicated counter (RFC 9562 §6.2 method 1) that increments while the clock stands still, and
 * the timestamp is borrowed from the next millisecond if the counter overflows. A clock that moves
 * backwards never rewinds the sequence; the previous timestamp is kept and the counter advances
 * instead, so identifiers stay strictly increasing for the lifetime of the process. The remaining
 * 62 random bits keep identifiers unique across processes, which the counter alone cannot do.
 *
 * <p>This type is deliberately not part of the public API: {@link ExecutionRecord}'s canonical
 * constructor still accepts any string, so a caller that supplies its own identifier keeps working,
 * and the identifier format stays an implementation detail rather than a published contract.
 */
final class RecordIds {

    /** The process-wide generator backing {@link #newId()}. */
    private static final Generator INSTANCE = new Generator(System::currentTimeMillis);

    private RecordIds() {}

    /**
     * Returns a new time-ordered identifier.
     *
     * @return a UUIDv7 in canonical lowercase form, greater than every identifier previously
     *     returned by this process
     */
    static String newId() {
        return INSTANCE.next();
    }

    /**
     * A UUIDv7 generator over an injectable clock.
     *
     * <p>Package-private so tests can drive the clock, including backwards.
     */
    static final class Generator {

        /** Widest value of the 12-bit {@code rand_a} counter. */
        private static final int MAX_SEQUENCE = 0xFFF;

        /** Version nibble (7) positioned at bits 12-15 of the most significant long. */
        private static final long VERSION_BITS = 0x7000L;

        /** Variant bits (0b10) positioned at the top of the least significant long. */
        private static final long VARIANT_BITS = 0x8000000000000000L;

        /** Mask clearing the two variant bits from the random {@code rand_b} field. */
        private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL;

        private final LongSupplier clock;
        private final SecureRandom random = new SecureRandom();
        private final AtomicReference<Tick> tick =
                new AtomicReference<>(new Tick(Long.MIN_VALUE, 0));

        /**
         * Creates a generator reading the current time from the given clock.
         *
         * @param clock supplies the current time in milliseconds since the epoch
         */
        Generator(LongSupplier clock) {
            this.clock = clock;
        }

        /**
         * Returns the next identifier, strictly greater than the previous one.
         *
         * @return a UUIDv7 in canonical lowercase form
         */
        String next() {
            long now = clock.getAsLong();
            Tick current = tick.updateAndGet(previous -> previous.advanceTo(now));
            long mostSignificant =
                    (current.timestamp() << 16) | VERSION_BITS | (long) current.sequence();
            long leastSignificant = (random.nextLong() & RAND_B_MASK) | VARIANT_BITS;
            return new UUID(mostSignificant, leastSignificant).toString();
        }

        /**
         * The timestamp and counter shared by every identifier issued in one millisecond.
         *
         * @param timestamp milliseconds since the epoch, embedded in the identifier
         * @param sequence the {@code rand_a} counter distinguishing identifiers within {@code
         *     timestamp}
         */
        private record Tick(long timestamp, int sequence) {

            /**
             * Returns the tick to use for the next identifier.
             *
             * <p>A clock that has advanced restarts the counter at the new millisecond. A clock
             * that stands still or has moved backwards keeps this tick's timestamp and advances the
             * counter, which borrows from the following millisecond once the counter is exhausted.
             *
             * @param now the current clock reading
             * @return the next tick, always greater than this one
             */
            Tick advanceTo(long now) {
                if (now > timestamp) {
                    return new Tick(now, 0);
                }
                if (sequence < MAX_SEQUENCE) {
                    return new Tick(timestamp, sequence + 1);
                }
                return new Tick(timestamp + 1, 0);
            }
        }
    }
}
