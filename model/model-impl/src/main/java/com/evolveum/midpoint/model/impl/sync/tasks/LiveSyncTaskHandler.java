/*
 * Copyright (c) 2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.sync.tasks;

import javax.annotation.PostConstruct;

import com.evolveum.midpoint.schema.constants.Channel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.impl.ModelConstants;
import com.evolveum.midpoint.model.impl.sync.tasks.SyncTaskHelper.TargetInfo;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.result.OperationConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskPartitionDefinitionType;

/**
 * The task handler for a live synchronization.
 * <p>
 * This handler takes care of executing live synchronization "runs". It means that the handler "run" method will
 * be called every few seconds. The responsibility is to scan for changes that happened since the last run.
 *
 * @author Radovan Semancik
 */
@Component
public class LiveSyncTaskHandler implements TaskHandler {

    public static final String HANDLER_URI = ModelConstants.NS_SYNCHRONIZATION_TASK_PREFIX + "/live-sync/handler-3";

    @Autowired private TaskManager taskManager;
    @Autowired private ProvisioningService provisioningService;
    @Autowired private SyncTaskHelper helper;

    private static final Trace LOGGER = TraceManager.getTrace(LiveSyncTaskHandler.class);
    private static final String CONTEXT = "Live Sync";

    @PostConstruct
    private void initialize() {
        taskManager.registerHandler(HANDLER_URI, this);
    }

    @NotNull
    @Override
    public StatisticsCollectionStrategy getStatisticsCollectionStrategy() {
        return new StatisticsCollectionStrategy()
                .fromStoredValues()
                .maintainIterationStatistics()
                .maintainSynchronizationStatistics()
                .maintainActionsExecutedStatistics();
    }

    @Override
    public TaskRunResult run(RunningTask task, TaskPartitionDefinitionType partition) {
        LOGGER.trace("LiveSyncTaskHandler.run starting for {}, partition: {}", task, partition);

        OperationResult opResult = new OperationResult(OperationConstants.LIVE_SYNC);
        TaskRunResult runResult = new TaskRunResult();
        runResult.setOperationResult(opResult);

        try {
            try {
                TargetInfo targetInfo = helper.getTargetInfo(LOGGER, task, opResult, CONTEXT);
                LOGGER.trace("Task target: {}", targetInfo);

                // Calling synchronize(..) in provisioning.
                // This will detect the changes and notify model about them.
                // It will use extension of task to store synchronization state
                ModelImplUtils.clearRequestee(task);
                int changesProcessed = provisioningService.synchronize(targetInfo.coords, task, partition, opResult);

                LOGGER.trace("LiveSyncTaskHandler.run stopping (resource {}); changes processed: {}", targetInfo.resource, changesProcessed);
                opResult.createSubresult(OperationConstants.LIVE_SYNC_STATISTICS).recordStatus(OperationResultStatus.SUCCESS, "Changes processed: " + changesProcessed);
            } catch (Throwable t) {
                throw helper.convertException(t, partition);
            }

            return helper.processFinish(runResult);
        } catch (TaskException e) {
            return helper.processTaskException(e, LOGGER, CONTEXT, runResult);
        }
    }

    @Override
    public String getCategoryName(Task task) {
        return TaskCategory.LIVE_SYNCHRONIZATION;
    }

    @Override
    public String getArchetypeOid() {
        return SystemObjectsType.ARCHETYPE_LIVE_SYNC_TASK.value();
    }

    @Override
    public String getDefaultChannel() {
        return Channel.LIVE_SYNC.getUri();
    }
}