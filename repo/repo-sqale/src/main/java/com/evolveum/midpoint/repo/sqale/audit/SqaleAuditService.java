/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.audit;

import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import javax.xml.datatype.Duration;

import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.dml.DefaultMapper;
import com.querydsl.sql.dml.SQLInsertClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditReferenceValue;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.prism.path.CanonicalItemPath;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.CloneUtil;
import com.evolveum.midpoint.repo.api.SqlPerformanceMonitorsCollection;
import com.evolveum.midpoint.repo.sqale.SqaleQueryContext;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.SqaleRepositoryConfiguration;
import com.evolveum.midpoint.repo.sqale.SqaleServiceBase;
import com.evolveum.midpoint.repo.sqale.audit.qmodel.*;
import com.evolveum.midpoint.repo.sqlbase.*;
import com.evolveum.midpoint.repo.sqlbase.perfmon.SqlPerformanceMonitorImpl;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.Holder;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CleanupPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationAuditType;
import com.evolveum.prism.xml.ns._public.types_3.ItemDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;

/**
 * Audit service using SQL DB as a store, also allows for searching (see {@link #supportsRetrieval}).
 * TODO: rethink the initialization, will we use factory class again?
 */
public class SqaleAuditService extends SqaleServiceBase implements AuditService {

    private static final Integer CLEANUP_AUDIT_BATCH_SIZE = 500;

    private final SqlQueryExecutor sqlQueryExecutor;

    private volatile SystemConfigurationAuditType auditConfiguration;

    public SqaleAuditService(
            SqaleRepoContext sqlRepoContext,
            SqlPerformanceMonitorsCollection sqlPerformanceMonitorsCollection) {
        super(sqlRepoContext, sqlPerformanceMonitorsCollection);
        this.sqlQueryExecutor = new SqlQueryExecutor(sqlRepoContext);

        SqaleRepositoryConfiguration repoConfig =
                (SqaleRepositoryConfiguration) sqlRepoContext.getJdbcRepositoryConfiguration();

        // monitor initialization and registration
        performanceMonitor = new SqlPerformanceMonitorImpl(
                repoConfig.getPerformanceStatisticsLevel(),
                repoConfig.getPerformanceStatisticsFile());
        sqlPerformanceMonitorsCollection.register(performanceMonitor);
    }

    public SqlRepoContext getSqlRepoContext() {
        return sqlRepoContext;
    }

    @Override
    public void audit(AuditEventRecord record, Task task, OperationResult parentResult) {
        Objects.requireNonNull(record, "Audit event record must not be null.");
        Objects.requireNonNull(task, "Task must not be null.");

        OperationResult operationResult = parentResult.createSubresult(opNamePrefix + OP_AUDIT);

        try {
            auditExecute(record);
        } catch (RuntimeException e) {
            throw handledGeneralException(e, operationResult);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    private void auditExecute(AuditEventRecord record) {
        long opHandle = registerOperationStart(OP_AUDIT);
        try (JdbcSession jdbcSession = sqlRepoContext.newJdbcSession().startTransaction()) {
            long recordId = insertAuditEventRecord(jdbcSession, record);

            /* TODO
            Collection<MAuditDelta> deltas =
                    insertAuditDeltas(jdbcSession, recordId, record.getDeltas());
            insertChangedItemPaths(jdbcSession, recordId, deltas);

            insertProperties(jdbcSession, recordId, record.getProperties());
            insertReferences(jdbcSession, recordId, record.getReferences());
            insertResourceOids(jdbcSession, recordId, record.getResourceOids());
            */
            jdbcSession.commit();
        } finally {
            registerOperationFinish(opHandle, 1);
        }
    }

    /**
     * Inserts audit event record aggregate root without any subentities.
     *
     * @return ID of created audit event record
     */
    private Long insertAuditEventRecord(
            JdbcSession jdbcSession, AuditEventRecord record) {
        QAuditEventRecordMapping aerMapping = QAuditEventRecordMapping.get();
        QAuditEventRecord aer = aerMapping.defaultAlias();
        MAuditEventRecord aerBean = aerMapping.toRowObject(record);
        SQLInsertClause insert = jdbcSession.newInsert(aer).populate(aerBean);

        /* TODO or ext?
        Map<String, ColumnMetadata> customColumns = aerMapping.getExtensionColumns();
        for (Entry<String, String> property : record.getCustomColumnProperty().entrySet()) {
            String propertyName = property.getKey();
            if (!customColumns.containsKey(propertyName)) {
                throw new IllegalArgumentException("Audit event record table doesn't"
                        + " contains column for property " + propertyName);
            }
            // Like insert.set, but that one is too parameter-type-safe for our generic usage here.
            insert.columns(aer.getPath(propertyName)).values(property.getValue());
        }
        */

        return insert.executeWithKey(aer.id);
    }

    private Collection<MAuditDelta> insertAuditDeltas(
            JdbcSession jdbcSession, long recordId, Collection<ObjectDeltaOperation<?>> deltas) {
        // we want to keep only unique deltas, checksum is also part of PK
        Map<String, MAuditDelta> deltasByChecksum = new HashMap<>();
        for (ObjectDeltaOperation<?> deltaOperation : deltas) {
            if (deltaOperation == null) {
                continue;
            }

            MAuditDelta mAuditDelta = convertDelta(deltaOperation, recordId);
            deltasByChecksum.put(mAuditDelta.checksum, mAuditDelta);
        }

        if (!deltasByChecksum.isEmpty()) {
            SQLInsertClause insertBatch = jdbcSession.newInsert(
                    QAuditDeltaMapping.get().defaultAlias());
            for (MAuditDelta value : deltasByChecksum.values()) {
                // NULLs are important to keep the value count consistent during the batch
                insertBatch.populate(value, DefaultMapper.WITH_NULL_BINDINGS).addBatch();
            }
            insertBatch.setBatchToBulk(true);
            insertBatch.execute();
        }
        return deltasByChecksum.values();
    }

    private MAuditDelta convertDelta(ObjectDeltaOperation<?> deltaOperation, long recordId) {
        MAuditDelta mAuditDelta = new MAuditDelta();
        mAuditDelta.recordId = recordId;

        /* TODO
        try {
            ObjectDelta<? extends ObjectType> delta = deltaOperation.getObjectDelta();
            if (delta != null) {
                DeltaConversionOptions options =
                        DeltaConversionOptions.createSerializeReferenceNames();
                options.setEscapeInvalidCharacters(isEscapingInvalidCharacters(auditConfiguration));
                String serializedDelta = DeltaConvertor.toObjectDeltaTypeXml(delta, options);

                // serializedDelta is transient, needed for changed items later
                mAuditDelta.serializedDelta = serializedDelta;
                mAuditDelta.delta = RUtil.getBytesFromSerializedForm(
                        serializedDelta, sqlConfiguration().isUseZipAudit());
                mAuditDelta.deltaOid = delta.getOid();
                mAuditDelta.deltaType = MiscUtil.enumOrdinal(
                        RUtil.getRepoEnumValue(delta.getChangeType(), RChangeType.class));
            }

            OperationResult executionResult = deltaOperation.getExecutionResult();
            if (executionResult != null) {
                OperationResultType jaxb = executionResult.createOperationResultType();
                if (jaxb != null) {
                    mAuditDelta.status = MiscUtil.enumOrdinal(
                            RUtil.getRepoEnumValue(jaxb.getStatus(), ROperationResultStatus.class));
                    // Note that escaping invalid characters and using toString for unsupported types is safe in the
                    // context of operation result serialization.
                    String full = schemaService.createStringSerializer(PrismContext.LANG_XML)
                            .options(SerializationOptions.createEscapeInvalidCharacters()
                                    .serializeUnsupportedTypesAsString(true))
                            .serializeRealValue(jaxb, SchemaConstantsGenerated.C_OPERATION_RESULT);
                    mAuditDelta.fullResult = RUtil.getBytesFromSerializedForm(
                            full, sqlConfiguration().isUseZipAudit());
                }
            }
            mAuditDelta.resourceOid = deltaOperation.getResourceOid();
            if (deltaOperation.getObjectName() != null) {
                mAuditDelta.objectNameOrig = deltaOperation.getObjectName().getOrig();
                mAuditDelta.objectNameNorm = deltaOperation.getObjectName().getNorm();
            }
            if (deltaOperation.getResourceName() != null) {
                mAuditDelta.resourceNameOrig = deltaOperation.getResourceName().getOrig();
                mAuditDelta.resourceNameNorm = deltaOperation.getResourceName().getNorm();
            }
            mAuditDelta.checksum = RUtil.computeChecksum(mAuditDelta.delta, mAuditDelta.fullResult);
        } catch (Exception ex) {
            throw new SystemException("Problem during audit delta conversion", ex);
        }
        */
        return mAuditDelta;
    }

    private void insertChangedItemPaths(
            JdbcSession jdbcSession, long recordId, Collection<MAuditDelta> deltas) {
        Set<String> changedItemPaths = new HashSet<>();
        for (MAuditDelta delta : deltas) {
            try {
                RepositoryObjectParseResult<ObjectDeltaType> parseResult =
                        sqlRepoContext.parsePrismObject(delta.serializedDelta, ObjectDeltaType.class);
                ObjectDeltaType deltaBean = parseResult.prismValue;
                for (ItemDeltaType itemDelta : deltaBean.getItemDelta()) {
                    ItemPath path = itemDelta.getPath().getItemPath();
                    CanonicalItemPath canonical = sqlRepoContext.prismContext()
                            .createCanonicalItemPath(path, deltaBean.getObjectType());
                    for (int i = 0; i < canonical.size(); i++) {
                        changedItemPaths.add(canonical.allUpToIncluding(i).asString());
                    }
                }
            } catch (SchemaException | SystemException e) {
                // See MID-6446 - we want to throw in tests, old ones should be fixed by now
                if (InternalsConfig.isConsistencyChecks()) {
                    throw new SystemException("Problem during audit delta parse", e);
                }
                logger.warn("Serialized audit delta for recordId={} cannot be parsed."
                        + " No changed items were created. This may cause problem later, but is not"
                        + " critical for storing the audit record.", recordId, e);
            }
        }
        /*
        if (!changedItemPaths.isEmpty()) {
            QAuditItem qAuditItem = QAuditItemMapping.get().defaultAlias();
            SQLInsertClause insertBatch = jdbcSession.newInsert(qAuditItem);
            for (String changedItemPath : changedItemPaths) {
                insertBatch.set(qAuditItem.recordId, recordId)
                        .set(qAuditItem.changedItemPath, changedItemPath)
                        .addBatch();
            }
            insertBatch.setBatchToBulk(true);
            insertBatch.execute();
        }
        */
    }

    private void insertProperties(
            JdbcSession jdbcSession, long recordId, Map<String, Set<String>> properties) {
        if (properties.isEmpty()) {
            return;
        }

        /*
        QAuditPropertyValue qAuditPropertyValue = QAuditPropertyValueMapping.get().defaultAlias();
        SQLInsertClause insertBatch = jdbcSession.newInsert(qAuditPropertyValue);
        for (String propertyName : properties.keySet()) {
            for (String propertyValue : properties.get(propertyName)) {
                // id will be generated, but we're not interested in those here
                insertBatch.set(qAuditPropertyValue.recordId, recordId)
                        .set(qAuditPropertyValue.name, propertyName)
                        .set(qAuditPropertyValue.value, propertyValue)
                        .addBatch();
            }
        }
        if (insertBatch.getBatchCount() == 0) {
            return; // strange, no values anywhere?
        }

        insertBatch.setBatchToBulk(true);
        insertBatch.execute();
        */
    }

    private void insertReferences(JdbcSession jdbcSession,
            long recordId, Map<String, Set<AuditReferenceValue>> references) {
        if (references.isEmpty()) {
            return;
        }

        /*
        QAuditRefValue qAuditRefValue = QAuditRefValueMapping.get().defaultAlias();
        SQLInsertClause insertBatch = jdbcSession.newInsert(qAuditRefValue);
        for (String refName : references.keySet()) {
            for (AuditReferenceValue refValue : references.get(refName)) {
                // id will be generated, but we're not interested in those here
                PolyString targetName = refValue.getTargetName();
                insertBatch.set(qAuditRefValue.recordId, recordId)
                        .set(qAuditRefValue.name, refName)
                        .set(qAuditRefValue.oid, refValue.getOid())
                        .set(qAuditRefValue.type, RUtil.qnameToString(refValue.getType()))
                        .set(qAuditRefValue.targetNameOrig, PolyString.getOrig(targetName))
                        .set(qAuditRefValue.targetNameNorm, PolyString.getNorm(targetName))
                        .addBatch();
            }
        }
        if (insertBatch.getBatchCount() == 0) {
            return; // strange, no values anywhere?
        }

        insertBatch.setBatchToBulk(true);
        insertBatch.execute();
        */
    }

    private void insertResourceOids(
            JdbcSession jdbcSession, long recordId, Set<String> resourceOids) {
        if (resourceOids.isEmpty()) {
            return;
        }

        /*
        QAuditResource qAuditResource = QAuditResourceMapping.get().defaultAlias();
        SQLInsertClause insertBatch = jdbcSession.newInsert(qAuditResource);
        for (String resourceOid : resourceOids) {
            insertBatch.set(qAuditResource.recordId, recordId)
                    .set(qAuditResource.resourceOid, resourceOid)
                    .addBatch();
        }

        insertBatch.setBatchToBulk(true);
        insertBatch.execute();
        */
    }

    @Override
    public void cleanupAudit(CleanupPolicyType policy, OperationResult parentResult) {
        Objects.requireNonNull(policy, "Cleanup policy must not be null.");
        Objects.requireNonNull(parentResult, "Operation result must not be null.");

        // TODO review monitoring performance of these cleanup operations
        // It looks like the attempts (and wasted time) are not counted correctly
        cleanupAuditMaxRecords(policy, parentResult);
        cleanupAuditMaxAge(policy, parentResult);
    }

    private void cleanupAuditMaxAge(CleanupPolicyType policy, OperationResult parentResult) {
        if (policy.getMaxAge() == null) {
            return;
        }

        final String operation = "deletingMaxAge";

        long opHandle = registerOperationStart(OP_CLEANUP_AUDIT_MAX_AGE);
        int attempt = 1;

        Duration duration = policy.getMaxAge();
        if (duration.getSign() > 0) {
            duration = duration.negate();
        }
        Date minValue = new Date();
        duration.addTo(minValue);

        long start = System.currentTimeMillis();
        boolean first = true;
        Holder<Integer> totalCountHolder = new Holder<>(0);
        try {
            while (true) {
                try {
                    logger.info("{} audit cleanup, deleting up to {} (duration '{}'), batch size {}{}.",
                            first ? "Starting" : "Continuing with ",
                            minValue, duration, CLEANUP_AUDIT_BATCH_SIZE,
                            first ? "" : ", up to now deleted " + totalCountHolder.getValue() + " entries");
                    first = false;
                    int count;
                    do {
                        // the following method may restart due to concurrency
                        // (or any other) problem - in any iteration
                        long batchStart = System.currentTimeMillis();
                        logger.debug(
                                "Starting audit cleanup batch, deleting up to {} (duration '{}'),"
                                        + " batch size {}, up to now deleted {} entries.",
                                minValue, duration, CLEANUP_AUDIT_BATCH_SIZE, totalCountHolder.getValue());

                        count = batchDeletionAttempt(
                                (session, tempTable) -> selectRecordsByMaxAge(session, tempTable, minValue),
                                totalCountHolder, batchStart, parentResult);
                    } while (count > 0);
                    return;
                } catch (RuntimeException ex) {
                    // TODO
//                    attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, parentResult);
//                    pm.registerOperationNewAttempt(opHandle, attempt);
                }
            }
        } finally {
            registerOperationFinish(opHandle, attempt);
            logger.info("Audit cleanup based on age finished; deleted {} entries in {} seconds.",
                    totalCountHolder.getValue(), (System.currentTimeMillis() - start) / 1000L);
        }
    }

    private void cleanupAuditMaxRecords(CleanupPolicyType policy, OperationResult parentResult) {
        if (policy.getMaxRecords() == null) {
            return;
        }

        final String operation = "deletingMaxRecords";

        long opHandle = registerOperationStart(OP_CLEANUP_AUDIT_MAX_RECORDS);
        int attempt = 1;

        int recordsToKeep = policy.getMaxRecords();

        long start = System.currentTimeMillis();
        boolean first = true;
        Holder<Integer> totalCountHolder = new Holder<>(0);
        try {
            while (true) {
                try {
                    logger.info("{} audit cleanup, keeping at most {} records, batch size {}{}.",
                            first ? "Starting" : "Continuing with ", recordsToKeep, CLEANUP_AUDIT_BATCH_SIZE,
                            first ? "" : ", up to now deleted " + totalCountHolder.getValue() + " entries");
                    first = false;
                    int count;
                    do {
                        // the following method may restart due to concurrency
                        // (or any other) problem - in any iteration
                        long batchStart = System.currentTimeMillis();
                        logger.debug(
                                "Starting audit cleanup batch, keeping at most {} records,"
                                        + " batch size {}, up to now deleted {} entries.",
                                recordsToKeep, CLEANUP_AUDIT_BATCH_SIZE, totalCountHolder.getValue());

                        count = batchDeletionAttempt(
                                (session, tempTable) -> selectRecordsByNumberToKeep(session, tempTable, recordsToKeep),
                                totalCountHolder, batchStart, parentResult);
                    } while (count > 0);
                    return;
                } catch (RuntimeException ex) {
                    // TODO
//                    attempt = baseHelper.logOperationAttempt(null, operation, attempt, ex, parentResult);
//                    pm.registerOperationNewAttempt(opHandle, attempt);
                }
            }
        } finally {
            registerOperationFinish(opHandle, attempt);
            logger.info("Audit cleanup based on record count finished; deleted {} entries in {} seconds.",
                    totalCountHolder.getValue(), (System.currentTimeMillis() - start) / 1000L);
        }
    }

    /**
     * Deletes one batch of records using recordsSelector to select records
     * according to particular cleanup policy.
     */
    private int batchDeletionAttempt(
            BiFunction<JdbcSession, String, Integer> recordsSelector,
            Holder<Integer> totalCountHolder, long batchStart, OperationResult parentResult) {

        OperationResult result = parentResult.createSubresult("batchDeletionAttempt");

        try (JdbcSession jdbcSession = sqlRepoContext.newJdbcSession().startTransaction()) {
            try {
                /* TODO completely rework and just delete the main entries, the rest will cascade
                TemporaryTableDialect ttDialect = TemporaryTableDialect
                        .getTempTableDialect(sqlConfiguration().getDatabaseType());

                // create temporary table
                final String tempTable =
                        ttDialect.generateTemporaryTableName(QAuditEventRecord.TABLE_NAME);
                createTemporaryTable(jdbcSession, tempTable);
                logger.trace("Created temporary table '{}'.", tempTable);

                int count = recordsSelector.apply(jdbcSession, tempTable);
                logger.trace("Inserted {} audit record ids ready for deleting.", count);

                // drop records from m_audit_item, m_audit_event, m_audit_delta, and others
                jdbcSession.executeStatement(
                        createDeleteQuery(QAuditItem.TABLE_NAME,
                                tempTable, QAuditItem.RECORD_ID));
                jdbcSession.executeStatement(
                        createDeleteQuery(QAuditDelta.TABLE_NAME,
                                tempTable, QAuditDelta.RECORD_ID));
                jdbcSession.executeStatement(
                        createDeleteQuery(QAuditPropertyValue.TABLE_NAME,
                                tempTable, QAuditPropertyValue.RECORD_ID));
                jdbcSession.executeStatement(
                        createDeleteQuery(QAuditRefValue.TABLE_NAME,
                                tempTable, QAuditRefValue.RECORD_ID));
                jdbcSession.executeStatement(
                        createDeleteQuery(QAuditResource.TABLE_NAME,
                                tempTable, QAuditResource.RECORD_ID));
                jdbcSession.executeStatement(
                        createDeleteQuery(QAuditEventRecord.TABLE_NAME,
                                tempTable, QAuditEventRecord.ID));

                // drop temporary table
                if (ttDialect.dropTemporaryTableAfterUse()) {
                    logger.debug("Dropping temporary table.");
                    jdbcSession.executeStatement(
                            ttDialect.getDropTemporaryTableString() + ' ' + tempTable);
                }

                jdbcSession.commit();
                // commit would happen automatically, but if it fails, we don't change the numbers
                int totalCount = totalCountHolder.getValue() + count;
                totalCountHolder.setValue(totalCount);
                logger.debug("Audit cleanup batch finishing successfully in {} milliseconds; total count = {}",
                        System.currentTimeMillis() - batchStart, totalCount);

                return count;
                */
                return -1; // TODO
            } catch (RuntimeException ex) {
                logger.debug("Audit cleanup batch finishing with exception in {} milliseconds; exception = {}",
                        System.currentTimeMillis() - batchStart, ex.getMessage());
                handleGeneralRuntimeException(ex, jdbcSession, result);
                throw new AssertionError("We shouldn't get here.");
            }
        } catch (Throwable t) {
            result.recordFatalError(t);
            throw t;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    private int selectRecordsByMaxAge(
            JdbcSession jdbcSession, String tempTable, Date minValue) {

        QAuditEventRecord aer = QAuditEventRecordMapping.get().defaultAlias();
        SQLQuery<Long> populateQuery = jdbcSession.newQuery()
                .select(aer.id)
                .from(aer)
                .where(aer.timestamp.lt(Instant.ofEpochMilli(minValue.getTime())))
                // we limit the query, but we don't care about order, eventually we'll get them all
                .limit(CLEANUP_AUDIT_BATCH_SIZE);

//        QAuditTemp tmp = new QAuditTemp("tmp", tempTable);
//        return (int) jdbcSession.newInsert(tmp).select(populateQuery).execute();
        return -1; // TODO
    }

    private int selectRecordsByNumberToKeep(
            JdbcSession jdbcSession, String tempTable, int recordsToKeep) {
        /*
        QAuditEventRecord aer = QAuditEventRecordMapping.get().defaultAlias();
        long totalAuditRecords = jdbcSession.newQuery().from(aer).fetchCount();

        // we will find the number to delete and limit it to range [0,CLEANUP_AUDIT_BATCH_SIZE]
        long recordsToDelete = Math.max(0,
                Math.min(totalAuditRecords - recordsToKeep, CLEANUP_AUDIT_BATCH_SIZE));
        logger.debug("Total audit records: {}, records to keep: {} => records to delete in this batch: {}",
                totalAuditRecords, recordsToKeep, recordsToDelete);
        if (recordsToDelete == 0) {
            return 0;
        }

        SQLQuery<Long> populateQuery = jdbcSession.newQuery()
                .select(aer.id)
                .from(aer)
                .orderBy(aer.timestamp.asc())
                .limit(recordsToDelete);

        QAuditTemp tmp = new QAuditTemp("tmp", tempTable);
        return (int) jdbcSession.newInsert(tmp).select(populateQuery).execute();
        */
        return -1; // TODO
    }

    /*
    private String createDeleteQuery(
            String objectTable, String tempTable, ColumnMetadata idColumn) {
        if (sqlConfiguration().isUsingMySqlCompatible()) {
            return createDeleteQueryAsJoin(objectTable, tempTable, idColumn);
        } else if (sqlConfiguration().isUsingPostgreSQL()) {
            return createDeleteQueryAsJoinPostgreSQL(objectTable, tempTable, idColumn);
        } else {
            // todo consider using join for other databases as well
            return createDeleteQueryAsSubquery(objectTable, tempTable, idColumn);
        }
    }

    private String createDeleteQueryAsJoin(
            String objectTable, String tempTable, ColumnMetadata idColumn) {
        return "DELETE FROM main, temp USING " + objectTable + " AS main"
                + " INNER JOIN " + tempTable + " as temp"
                + " WHERE main." + idColumn.getName() + " = temp.id";
    }

    private String createDeleteQueryAsJoinPostgreSQL(
            String objectTable, String tempTable, ColumnMetadata idColumn) {
        return "delete from " + objectTable + " main using " + tempTable
                + " temp where main." + idColumn.getName() + " = temp.id";
    }

    private String createDeleteQueryAsSubquery(
            String objectTable, String tempTable, ColumnMetadata idColumn) {
        return "delete from " + objectTable
                + " where " + idColumn.getName() + " in (select id from " + tempTable
                + ')';
    }
    */

    @Override
    public boolean supportsRetrieval() {
        return true;
    }

    @Override
    public void applyAuditConfiguration(SystemConfigurationAuditType configuration) {
        this.auditConfiguration = CloneUtil.clone(configuration);
    }

    @Override
    public int countObjects(
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult parentResult) {
        OperationResult operationResult = parentResult.subresult(opNamePrefix + OP_COUNT_OBJECTS)
                .addParam("query", query)
                .build();

        try {
            var queryContext = SqaleQueryContext.from(
                    AuditEventRecordType.class, sqlRepoContext);
            return sqlQueryExecutor.count(queryContext, query, options);
        } catch (RepositoryException | RuntimeException e) {
            // TODO
//            baseHelper.handleGeneralException(e, operationResult);
            throw new SystemException(e);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    @Override
    @NotNull
    public SearchResultList<AuditEventRecordType> searchObjects(
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull OperationResult parentResult)
            throws SchemaException {

        OperationResult operationResult = parentResult.subresult(opNamePrefix + OP_SEARCH_OBJECTS)
                .addParam("query", query)
                .build();

        try {
            var queryContext = SqaleQueryContext.from(
                    AuditEventRecordType.class, sqlRepoContext);
            SearchResultList<AuditEventRecordType> result =
                    sqlQueryExecutor.list(queryContext, query, options);
            return result;
        } catch (RepositoryException | RuntimeException e) {
            // TODO
//            baseHelper.handleGeneralException(e, operationResult);
            throw new SystemException(e);
        } catch (Throwable t) {
            operationResult.recordFatalError(t);
            throw t;
        } finally {
            operationResult.computeStatusIfUnknown();
        }
    }

    protected long registerOperationStart(String kind) {
        return registerOperationStart(kind, AuditEventRecordType.class);
    }
}