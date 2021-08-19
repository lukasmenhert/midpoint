/*
 * Copyright (c) 2010-2013 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.task.quartzimpl;

import static com.evolveum.midpoint.task.quartzimpl.TestTaskManagerBasic.NS_EXT;

import com.evolveum.midpoint.util.MiscUtil;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.task.api.TaskHandler;
import com.evolveum.midpoint.task.api.TaskRunResult;
import com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock task handler to be used in task manager tests.
 *
 * The functionality is similar to the NoOp activity (originally NoOpTaskHandler).
 */
public class MockTaskHandler implements TaskHandler {

    private static final Trace LOGGER = TraceManager.getTrace(MockTaskHandler.class);

    static final ItemName ITEM_DELAY = new ItemName(NS_EXT, "delay");
    private static final ItemName ITEM_STEPS = new ItemName(NS_EXT, "steps");

    private final AtomicBoolean hasRun = new AtomicBoolean(false);

    @Override
    public TaskRunResult run(@NotNull RunningTask task) {

        OperationResult result = task.getResult();
        TaskRunResult runResult = new TaskRunResult();

        PrismProperty<Integer> delayProp = task.getExtensionPropertyOrClone(ITEM_DELAY);
        PrismProperty<Integer> stepsProp = task.getExtensionPropertyOrClone(ITEM_STEPS);

        int delay = MoreObjects.firstNonNull(delayProp != null ? delayProp.getRealValue() : null, 0);
        int steps = MoreObjects.firstNonNull(stepsProp != null ? stepsProp.getRealValue() : null, 1);

        LOGGER.info("Run starting; progress = {}, steps to be executed = {}, delay for one step = {}, in task {}",
                task.getProgress(), steps, delay, task);

        for (int i = 0; i < steps; i++) {
            LOGGER.info("Executing step {} (numbered from one) of {} in task {}", i + 1, steps, task);

            MiscUtil.sleepNonInterruptibly(delay);

            task.incrementProgressAndStoreStatisticsIfTimePassed(result);

            if (!task.canRun()) {
                LOGGER.info("Got a shutdown request, finishing task {}", task);
                break;
            }
        }

        LOGGER.info("Run finishing; progress = {} in task {}", task.getProgress(), task);

        hasRun.set(true);

        result.computeStatusIfUnknown();
        runResult.setRunResultStatus(TaskRunResultStatus.FINISHED);

        return runResult;
    }

    @Override
    public String getArchetypeOid(@Nullable String handlerUri) {
        return SystemObjectsType.ARCHETYPE_UTILITY_TASK.value();
    }

    boolean hasRun() {
        return hasRun.get();
    }

    public void reset() {
        hasRun.set(false);
    }
}
