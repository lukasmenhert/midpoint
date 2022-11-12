/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

/*
 * @author Martin Lizner
*/

package com.evolveum.midpoint.provisioning.impl.shadows.errors;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.ProvisioningOperationOptions;
import com.evolveum.midpoint.provisioning.impl.ProvisioningContext;
import com.evolveum.midpoint.provisioning.impl.shadows.ProvisioningOperationState;
import com.evolveum.midpoint.provisioning.impl.shadows.ProvisioningOperationState.AddOperationState;
import com.evolveum.midpoint.provisioning.impl.shadows.ProvisioningOperationState.ModifyOperationState;
import com.evolveum.midpoint.provisioning.impl.shadows.manager.ShadowManager;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import static com.evolveum.midpoint.provisioning.util.ProvisioningUtil.selectLiveShadow;
import static com.evolveum.midpoint.schema.util.ObjectTypeUtil.asObjectable;

@Component
class MaintenanceExceptionHandler extends ErrorHandler {

    private static final Trace LOGGER = TraceManager.getTrace(MaintenanceExceptionHandler.class);

    private static final String OPERATION_HANDLE_GET_ERROR = MaintenanceExceptionHandler.class.getName() + ".handleGetError";
    private static final String OPERATION_HANDLE_ADD_ERROR = MaintenanceExceptionHandler.class.getName() + ".handleAddError";
    private static final String OPERATION_HANDLE_MODIFY_ERROR = MaintenanceExceptionHandler.class.getName() + ".handleModifyError";
    private static final String OPERATION_HANDLE_DELETE_ERROR = MaintenanceExceptionHandler.class.getName() + ".handleDeleteError";

    @Autowired private ShadowManager shadowManager;

    @Override
    public ShadowType handleGetError(
            @NotNull ProvisioningContext ctx,
            @NotNull ShadowType repositoryShadow,
            @NotNull Exception cause,
            @NotNull OperationResult failedOperationResult,
            @NotNull OperationResult parentResult) {
        // TODO maybe I should put the code back here...
        throw new UnsupportedOperationException("MaintenanceException cannot occur during GET operation.");
    }

    @Override
    public OperationResultStatus handleAddError(
            ProvisioningContext ctx,
            ShadowType shadowToAdd,
            ProvisioningOperationOptions options,
            AddOperationState opState,
            Exception cause,
            OperationResult failedOperationResult,
            Task task,
            OperationResult parentResult) throws SchemaException {

        OperationResult result = parentResult.createSubresult(OPERATION_HANDLE_ADD_ERROR);
        result.addParam("exception", cause.getMessage());
        try {
            OperationResultStatus status;

            // TODO why querying by secondary identifiers? Maybe because the primary identifier is usually generated by the
            //  resource ... but is it always the case?

            // TODO shouldn't we have similar code for CommunicationException handling?
            //  For operation grouping, etc?

            // Think again if this is the best place for this functionality.

            ObjectQuery query = ObjectAlreadyExistHandler.createQueryBySecondaryIdentifier(shadowToAdd);
            LOGGER.trace("Going to find matching shadows using the query:\n{}", query.debugDumpLazily(1));
            List<PrismObject<ShadowType>> matchingShadows = shadowManager.searchShadows(ctx, query, null, result);
            LOGGER.trace("Found {}: {}", matchingShadows.size(), matchingShadows);
            ShadowType liveShadow = asObjectable(selectLiveShadow(matchingShadows));
            LOGGER.trace("Live shadow found: {}", liveShadow);

            if (liveShadow != null) {
                if (ShadowUtil.isExists(liveShadow)) {
                    LOGGER.trace("Found a live shadow that seems to exist on the resource: {}", liveShadow);
                    status = OperationResultStatus.SUCCESS;
                } else {
                    LOGGER.trace("Found a live shadow that was probably not yet created on the resource: {}", liveShadow);
                    status = OperationResultStatus.IN_PROGRESS;
                }
                opState.setRepoShadow(liveShadow);
            } else {
                status = OperationResultStatus.IN_PROGRESS;
            }

            failedOperationResult.setStatus(status);
            result.setStatus(status); // TODO
            if (status == OperationResultStatus.IN_PROGRESS) {
                opState.markToRetry(failedOperationResult);
            }
            return status;
        } catch (Throwable t) {
            result.recordException(t);
            throw t;
        } finally {
            result.close();
        }
    }

    @Override
    public OperationResultStatus handleModifyError(
            @NotNull ProvisioningContext ctx,
            @NotNull ShadowType repoShadow,
            @NotNull Collection<? extends ItemDelta<?, ?>> modifications,
            @Nullable ProvisioningOperationOptions options,
            @NotNull ModifyOperationState opState,
            @NotNull Exception cause,
            OperationResult failedOperationResult,
            @NotNull OperationResult parentResult) {

        OperationResult result = parentResult.createSubresult(OPERATION_HANDLE_MODIFY_ERROR);
        result.addParam("exception", cause.getMessage());
        try {
            failedOperationResult.setStatus(OperationResultStatus.IN_PROGRESS);
            result.setInProgress();
            return opState.markToRetry(failedOperationResult);
        } catch (Throwable t) {
            result.recordException(t);
            throw t;
        } finally {
            result.close();
        }
    }

    @Override
    public OperationResultStatus handleDeleteError(
            ProvisioningContext ctx,
            ShadowType repoShadow,
            ProvisioningOperationOptions options,
            ProvisioningOperationState.DeleteOperationState opState,
            Exception cause,
            OperationResult failedOperationResult,
            OperationResult parentResult) {
        OperationResult result = parentResult.createSubresult(OPERATION_HANDLE_DELETE_ERROR);
        result.addParam("exception", cause.getMessage());
        try {
            failedOperationResult.setStatus(OperationResultStatus.IN_PROGRESS);
            result.setInProgress();
            return opState.markToRetry(failedOperationResult);
        } catch (Throwable t) {
            result.recordException(t);
            throw t;
        } finally {
            result.close();
        }
    }

    @Override
    protected void throwException(Exception cause, ProvisioningOperationState<?> opState, OperationResult result) throws MaintenanceException {
        recordCompletionError(cause, opState, result);
        if (cause instanceof MaintenanceException) {
            throw (MaintenanceException)cause;
        } else {
            throw new MaintenanceException(cause.getMessage(), cause);
        }
    }
}
