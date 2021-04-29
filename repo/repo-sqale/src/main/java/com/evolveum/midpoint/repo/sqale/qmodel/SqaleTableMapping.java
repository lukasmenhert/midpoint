/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.qmodel;

import java.util.function.Function;

import com.querydsl.core.types.dsl.*;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqale.delta.item.*;
import com.evolveum.midpoint.repo.sqale.filtering.RefItemFilterProcessor;
import com.evolveum.midpoint.repo.sqale.filtering.UriItemFilterProcessor;
import com.evolveum.midpoint.repo.sqale.mapping.SqaleItemSqlMapper;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.EnumItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.PolyStringItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.SimpleItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.TimestampItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sqlbase.querydsl.UuidPath;

/**
 * Mapping superclass with common functions for {@link QObject} and non-objects (e.g. containers).
 * See javadoc in {@link QueryTableMapping} for more.
 *
 * @param <S> schema type
 * @param <Q> type of entity path
 * @param <R> row type related to the {@link Q}
 * @see QueryTableMapping
 */
public abstract class SqaleTableMapping<S, Q extends FlexibleRelationalPathBase<R>, R>
        extends QueryTableMapping<S, Q, R>
        implements SqaleMappingMixin<S, Q, R> {

    protected SqaleTableMapping(
            @NotNull String tableName,
            @NotNull String defaultAliasName,
            @NotNull Class<S> schemaType,
            @NotNull Class<Q> queryType) {
        super(tableName, defaultAliasName, schemaType, queryType);
    }

    /**
     * Returns the mapper creating the string filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    @Override
    protected <MS> SqaleItemSqlMapper<MS, Q, R> stringMapper(
            Function<Q, StringPath> rootToQueryItem) {
        return new SqaleItemSqlMapper<>(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /**
     * Returns the mapper creating the integer filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    @Override
    public <MS> SqaleItemSqlMapper<MS, Q, R> integerMapper(
            Function<Q, NumberPath<Integer>> rootToQueryItem) {
        return new SqaleItemSqlMapper<>(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /**
     * Returns the mapper creating the boolean filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    @Override
    protected <MS> SqaleItemSqlMapper<MS, Q, R> booleanMapper(Function<Q, BooleanPath> rootToQueryItem) {
        return new SqaleItemSqlMapper<>(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /**
     * Returns the mapper creating the UUID filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    @Override
    protected <MS> SqaleItemSqlMapper<MS, Q, R> uuidMapper(Function<Q, UuidPath> rootToQueryItem) {
        return new SqaleItemSqlMapper<>(
                ctx -> new SimpleItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new SimpleItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /**
     * Returns the mapper creating the timestamp filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    @Override
    protected <MS, T extends Comparable<T>> SqaleItemSqlMapper<MS, Q, R> timestampMapper(
            Function<Q, DateTimePath<T>> rootToQueryItem) {
        return new SqaleItemSqlMapper<>(
                ctx -> new TimestampItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new TimestampItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }

    /**
     * Returns the mapper creating the polystring filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    @Override
    protected <MS> SqaleItemSqlMapper<MS, Q, R> polyStringMapper(
            @NotNull Function<Q, StringPath> origMapping,
            @NotNull Function<Q, StringPath> normMapping) {
        return new SqaleItemSqlMapper<>(
                ctx -> new PolyStringItemFilterProcessor(ctx, origMapping, normMapping),
                ctx -> new PolyStringItemDeltaProcessor(ctx, origMapping, normMapping),
                origMapping);
    }

    /**
     * Returns the mapper creating the reference filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    protected <MS> SqaleItemSqlMapper<MS, Q, R> refMapper(
            Function<Q, UuidPath> rootToOidPath,
            Function<Q, EnumPath<MObjectType>> rootToTypePath,
            Function<Q, NumberPath<Integer>> rootToRelationIdPath) {
        return new SqaleItemSqlMapper<>(
                ctx -> new RefItemFilterProcessor(ctx,
                        rootToOidPath, rootToTypePath, rootToRelationIdPath),
                ctx -> new RefItemDeltaProcessor(ctx,
                        rootToOidPath, rootToTypePath, rootToRelationIdPath));
    }

    /**
     * Returns the mapper creating the cached URI filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    protected <MS> SqaleItemSqlMapper<MS, Q, R> uriMapper(
            Function<Q, NumberPath<Integer>> rootToPath) {
        return new SqaleItemSqlMapper<>(
                ctx -> new UriItemFilterProcessor(ctx, rootToPath),
                ctx -> new UriItemDeltaProcessor(ctx, rootToPath));
    }

    /**
     * Returns the mapper creating the enum filter/delta processors from context.
     *
     * @param <MS> mapped schema type, see javadoc in {@link QueryTableMapping}
     */
    public <MS, E extends Enum<E>> SqaleItemSqlMapper<MS, Q, R> enumMapper(
            @NotNull Function<Q, EnumPath<E>> rootToQueryItem) {
        return new SqaleItemSqlMapper<>(
                ctx -> new EnumItemFilterProcessor<>(ctx, rootToQueryItem),
                ctx -> new EnumItemDeltaProcessor<>(ctx, rootToQueryItem),
                rootToQueryItem);
    }
}
