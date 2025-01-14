/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.UUID;
import javax.xml.namespace.QName;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.schema.util.ExceptionUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

public class SqaleUtils {

    /**
     * Returns version from midPoint object as a number.
     *
     * @throws IllegalArgumentException if the version is null or non-number
     */
    public static int objectVersionAsInt(ObjectType schemaObject) {
        String version = schemaObject.getVersion();
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version must be a number: " + version);
        }
    }

    /**
     * Returns version from prism object as a number.
     *
     * @throws IllegalArgumentException if the version is null or non-number
     */
    public static int objectVersionAsInt(PrismObject<?> prismObject) {
        String version = prismObject.getVersion();
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Version must be a number: " + version);
        }
    }

    @Nullable
    public static UUID oidToUuid(@Nullable String oid) {
        if (oid == null) {
            return null;
        }
        return oidToUuidMandatory(oid);
    }

    @NotNull
    public static UUID oidToUuidMandatory(@NotNull String oid) {
        Objects.requireNonNull(oid, "OID must not be null");
        try {
            return UUID.fromString(oid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot convert OID '" + oid + "' to UUID", e);
        }
    }

    /** Parametrized type friendly version of {@link Object#getClass()}. */
    public static <S> Class<S> getClass(S object) {
        //noinspection unchecked
        return (Class<S>) object.getClass();
    }

    /**
     * Fixes reference type if `null` and tries to use default from definition.
     * Use returned value.
     */
    public static ObjectReferenceType referenceWithTypeFixed(ObjectReferenceType value) {
        if (value.getType() != null) {
            return value;
        }

        PrismReferenceDefinition def = value.asReferenceValue().getDefinition();
        QName defaultType = def.getTargetTypeName();
        if (defaultType == null) {
            throw new IllegalArgumentException("Can't modify reference with no target type"
                    + " specified and no default type in the definition. Value: " + value
                    + " Definition: " + def);
        }
        value = new ObjectReferenceType()
                .oid(value.getOid())
                .type(defaultType)
                .relation(value.getRelation());
        return value;
    }

    /** Throws more specific exception or returns and then original exception should be rethrown. */
    public static void handlePostgresException(Exception exception)
            throws ObjectAlreadyExistsException {
        PSQLException psqlException = ExceptionUtil.findCause(exception, PSQLException.class);

        String state = psqlException.getSQLState();
        String message = psqlException.getMessage();
        if (PSQLState.UNIQUE_VIOLATION.getState().equals(state)) {
            if (message.contains("m_object_oid_pkey")) {
                String oid = StringUtils.substringBetween(message, "(oid)=(", ")");
                throw new ObjectAlreadyExistsException(
                        oid != null ? "Provided OID " + oid + " already exists" : message,
                        exception);
            } else if (message.contains("namenorm_key")) {
                String name = StringUtils.substringBetween(message, "(namenorm)=(", ")");
                throw new ObjectAlreadyExistsException(name != null
                        ? "Object with conflicting normalized name '" + name + "' already exists"
                        : message,
                        exception);
            } else {
                throw new ObjectAlreadyExistsException(
                        "Conflicting object already exists, constraint violation message: "
                                + psqlException.getMessage(), exception);
            }
        }
    }

    public static boolean isUniqueConstraintViolation(Exception exception) {
        PSQLException psqlException = ExceptionUtil.findCause(exception, PSQLException.class);
        return PSQLState.UNIQUE_VIOLATION.getState().equals(psqlException.getSQLState());
    }

    public static String toString(Object object) {
        return new ToStringUtil(object).toString();
    }

    private static class ToStringUtil extends ReflectionToStringBuilder {

        @SuppressWarnings("DoubleBraceInitialization")
        private static final ToStringStyle STYLE = new ToStringStyle() {{
            setFieldSeparator(", ");
            setUseShortClassName(true);
            setUseIdentityHashCode(false);
        }};

        private ToStringUtil(Object object) {
            super(object, STYLE);
        }

        @Override
        protected boolean accept(Field field) {
            try {
                return super.accept(field) && field.get(getObject()) != null;
            } catch (IllegalAccessException e) {
                return super.accept(field);
            }
        }
    }
}
