package io.github.kakusuke.migraphe.mysql.schema;

/**
 * A single parameter of a MySQL stored routine, as reported by {@code
 * information_schema.PARAMETERS}.
 *
 * <p>Parameters are collected in ordinal order into {@link MySQLRoutineInfo#parameters()} so
 * generators can document a routine's signature. A function's return value is reported by the
 * source table at ordinal position {@code 0} with no mode and no name; it is not a parameter and is
 * therefore never represented by this record (the return type is available as {@link
 * MySQLRoutineInfo#dataType()}).
 *
 * @param position the 1-based ordinal position of the parameter (the {@code ORDINAL_POSITION}
 *     column)
 * @param mode the parameter direction (the {@code PARAMETER_MODE} column, {@code "IN"}, {@code
 *     "OUT"} or {@code "INOUT"}); empty string when the source value was {@code null}
 * @param name the parameter name (the {@code PARAMETER_NAME} column); empty string when the source
 *     value was {@code null}
 * @param dataType the declared data type (the {@code DTD_IDENTIFIER} column, for example {@code
 *     "varchar(10)"}); empty string when the source value was {@code null}. The exact spelling is
 *     server-dependent — MariaDB reports {@code int(11)} where MySQL 8.0 reports {@code int}.
 */
public record MySQLParameterInfo(int position, String mode, String name, String dataType) {}
