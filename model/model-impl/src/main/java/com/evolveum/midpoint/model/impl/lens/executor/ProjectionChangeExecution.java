/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.executor;

import static com.evolveum.midpoint.model.api.ProgressInformation.ActivityType.RESOURCE_OBJECT_OPERATION;
import static com.evolveum.midpoint.model.api.ProgressInformation.StateType.ENTERING;
import static com.evolveum.midpoint.model.impl.lens.ChangeExecutor.OPERATION_EXECUTE_PROJECTION;
import static com.evolveum.midpoint.prism.delta.ItemDeltaCollectionsUtil.findItemDeltasSubPath;

import java.util.Collection;

import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.model.api.ProgressInformation;
import com.evolveum.midpoint.model.api.context.SynchronizationIntent;
import com.evolveum.midpoint.model.api.context.SynchronizationPolicyDecision;
import com.evolveum.midpoint.model.impl.ModelBeans;
import com.evolveum.midpoint.model.impl.lens.*;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.repo.api.PreconditionViolationException;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * Represents execution of a change on given projection.
 *
 * Main responsibilities:
 *
 * 1. Determining if the change should be executed
 * 2. Reporting progress
 * 3. Executing reconciliation scripts (delegated)
 * 4. Delta refinements (broken contexts treatment, empty to delete delta conversion, higher-order deletion checks, ...)
 * 5. Updating focus-shadow links (delegated)
 *
 * The delta execution is delegated to {@link DeltaExecution}.
 */
public class ProjectionChangeExecution<O extends ObjectType> {

    /** For the time being we keep the parent logger name. */
    private static final Trace LOGGER = TraceManager.getTrace(ChangeExecutor.class);

    @NotNull private final LensContext<O> context;
    @NotNull private final LensProjectionContext projCtx;
    @NotNull private final Task task;
    @NotNull private final ModelBeans b;

    /**
     * Delta to be executed. It is gradually updated as needed.
     */
    private ObjectDelta<ShadowType> projectionDelta;

    /** What is the current state of the shadow. */
    private ShadowLivenessState shadowLivenessState;

    private boolean restartRequested;

    public ProjectionChangeExecution(@NotNull LensContext<O> context, @NotNull LensProjectionContext projCtx, @NotNull Task task,
            @NotNull ModelBeans modelBeans) {
        this.context = context;
        this.projCtx = projCtx;
        this.task = task;
        this.b = modelBeans;
    }

    public void execute(OperationResult parentResult) throws SchemaException, ObjectNotFoundException, CommunicationException,
            PreconditionViolationException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException {

        if (!shouldExecute()) {
            return;
        }

        shadowLivenessState = ShadowLivenessState.forShadow(projCtx.getObjectCurrent());

        OperationResult result = parentResult
                .subresult(OPERATION_EXECUTE_PROJECTION + "." + projCtx.getObjectTypeClass().getSimpleName())
                .addParam("resource", projCtx.getResource())
                .addArbitraryObjectAsContext("discriminator", projCtx.getResourceShadowDiscriminator())
                .build();

        boolean completed = true;
        try {
            LOGGER.trace("Executing projection context {}", projCtx.toHumanReadableString());

            context.reportProgress(new ProgressInformation(RESOURCE_OBJECT_OPERATION,
                    projCtx.getResourceShadowDiscriminator(), ENTERING));

            ScriptExecutor<O> scriptExecutor = new ScriptExecutor<>(context, projCtx, task, b);
            scriptExecutor.executeReconciliationScripts(BeforeAfterType.BEFORE, result);

            projectionDelta = projCtx.getExecutableDelta();

            emptyToDeleteDeltaIfNeeded();

            if (deletingHigherOrderContextWithLowerAlreadyDeleted()) {
                result.setStatus(OperationResultStatus.NOT_APPLICABLE);
                return;
            }

            boolean skipDeltaExecution;
            if (projCtx.isBroken() && !ObjectDelta.isDelete(projectionDelta)) {
                LOGGER.trace("Ignoring non-delete delta for broken context {}", projCtx.getResourceShadowDiscriminator());
                skipDeltaExecution = true;
            } else {
                skipDeltaExecution = ObjectDelta.isEmpty(projectionDelta);
            }

            if (!skipDeltaExecution) {
                DeltaExecution<O, ShadowType> deltaExecution = new DeltaExecution<>(context, projCtx, projectionDelta, null, task, b);
                deltaExecution.execute(result);
                shadowLivenessState = deltaExecution.getShadowLivenessState();
                if (projCtx.isAdd() && deltaExecution.getObjectAfterModification() != null) {
                    // FIXME This is suspicious. For example, the shadow creation can be delayed.
                    //  Also, ADD delta could become converted to MODIFY by delta executor, and so objectAfterModification
                    //  can be null.
                    //  This flag should be perhaps set by delta executor, like the "shadow in repo" is unset on object deletion
                    projCtx.setExists(true);
                }
            }

            updateLinks(result);

            scriptExecutor.executeReconciliationScripts(BeforeAfterType.AFTER, result);

            result.computeStatus();
            result.recordNotApplicableIfUnknown();

        } catch (ObjectAlreadyExistsException e) {

            // This exception is quite special. We have to decide how bad this really is.
            // This may be rename conflict - that would be bad.
            // Or this may be attempt to create account that already exists and just needs
            // to be linked. Which is no big deal and consistency mechanism (discovery) will
            // easily handle that. In that case it is done in "another task" which is
            // quasi-asynchronously executed from provisioning by calling notifyChange.
            // Once that is done then the account is already linked. And all we need to do
            // is to restart this whole operation.

            // check if this is a repeated attempt - ObjectAlreadyExistsException was not handled
            // correctly, e.g. if creating "Users" user in AD, whereas
            // "Users" is SAM Account Name which is used by a built-in group
            // - in such case, mark the context as broken
            if (isRepeatedAlreadyExistsException()) {
                // This is the bad case. Currently we do not do anything more intelligent than to look for
                // repeated error. If we get ObjectAlreadyExistsException twice then this is bad and we give up.
                // TODO: do something smarter here
                LOGGER.debug("Repeated ObjectAlreadyExistsException detected, marking projection {} as broken",
                        projCtx.toHumanReadableString());
                recordProjectionExecutionException(e, result);
                return;
            }

            // In his case we do not need to set account context as broken, instead we need to restart projector for this
            // context to recompute new account or find out if the account was already linked.
            // and also do not set fatal error to the operation result, this
            // is a special case
            // if it is fatal, it will be set later
            // but we need to set some result
            result.recordSuccess();
            restartRequested = true;
            completed = false;
            LOGGER.debug("ObjectAlreadyExistsException for projection {}, requesting projector restart",
                    projCtx.toHumanReadableString());
            projCtx.rot(); // todo
            projCtx.setSecondaryDelta(null); // todo
            projCtx.setObjectNew(null); // todo

        } catch (Throwable t) {

            recordProjectionExecutionException(t, result);

            // We still want to update the links here. E.g. this may be live sync case where we discovered new account
            // try to reconcile, but the reconciliation fails. We still want this shadow linked to user.
            updateLinks(result);

            ModelImplUtils.handleConnectorErrorCriticality(projCtx.getResource(), t, result);

        } finally {
            context.reportProgress(new ProgressInformation(RESOURCE_OBJECT_OPERATION,
                    projCtx.getResourceShadowDiscriminator(), result));

            LOGGER.trace("Setting completed flag for {} to {}", projCtx.toHumanReadableString(), completed);
            projCtx.setCompleted(completed);
        }
    }

    private boolean deletingHigherOrderContextWithLowerAlreadyDeleted() {
        if (ObjectDelta.isDelete(projectionDelta) && projCtx.isHigherOrder()) {
            // HACK ... for higher-order context check if this was already deleted
            LensProjectionContext lowerOrderContext = LensUtil.findLowerOrderContext(context, projCtx);
            return lowerOrderContext != null && lowerOrderContext.isDelete();
        } else {
            return false;
        }
    }

    /**
     * Converts empty to delete delta - for defined situations.
     */
    private void emptyToDeleteDeltaIfNeeded() {
        if (!ObjectDelta.isEmpty(projectionDelta)) {
            return;
        }

        if (projCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.DELETE
                || projCtx.getSynchronizationIntent() == SynchronizationIntent.DELETE) {
            LOGGER.trace("Converting empty to delete delta because intent or decision is DELETE");
            createDeleteDelta();
        } else if (projCtx.isBroken() && context.isForcedFocusDelete()) {
            LOGGER.trace("Converting empty to delete delta because context is broken and there is a forced focus deletion");
            createDeleteDelta();
        }
    }

    private void createDeleteDelta() {
        projectionDelta = b.prismContext.deltaFactory().object()
                .createDeleteDelta(projCtx.getObjectTypeClass(), projCtx.getOid());
    }

    private void recordProjectionExecutionException(Throwable e, OperationResult result) {
        result.recordFatalError(e);
        LOGGER.error("Error executing changes for {}: {}", projCtx.toHumanReadableString(), e.getMessage(), e);
        projCtx.setSynchronizationPolicyDecision(SynchronizationPolicyDecision.BROKEN);
    }

    private boolean shouldExecute() {
        if (projCtx.getWave() != context.getExecutionWave()) {
            LOGGER.trace("Skipping projection context {} because its wave ({}) is different from execution wave ({})",
                    projCtx.toHumanReadableString(), projCtx.getWave(), context.getExecutionWave());
            return false;
        }

        if (projCtx.isCompleted()) {
            LOGGER.trace("Skipping projection context {} because it's already completed", projCtx.toHumanReadableString());
            return false;
        }

        if (!projCtx.isCanProject()) {
            LOGGER.trace("Skipping projection context {} because canProject is false", projCtx.toHumanReadableString());
            return false;
        }

        // we should not get here, but just to be sure
        if (projCtx.getSynchronizationPolicyDecision() == SynchronizationPolicyDecision.IGNORE) {
            LOGGER.trace("Skipping ignored projection context {}", projCtx.toHumanReadableString());
            return false;
        }

        return true;
    }

    public boolean isRestartRequested() {
        return restartRequested;
    }

    private boolean isRepeatedAlreadyExistsException() {
        int deltas = projCtx.getExecutedDeltas().size();
        LOGGER.trace("isRepeatedAlreadyExistsException starting; number of executed deltas = {}", deltas);
        if (deltas < 2) {
            return false;
        }
        LensObjectDeltaOperation<ShadowType> lastDeltaOp = projCtx.getExecutedDeltas().get(deltas - 1);
        LensObjectDeltaOperation<ShadowType> previousDeltaOp = projCtx.getExecutedDeltas()
                .get(deltas - 2);
        // TODO check also previous execution result to see if it's
        // AlreadyExistException?
        ObjectDelta<ShadowType> lastDelta = lastDeltaOp.getObjectDelta();
        ObjectDelta<ShadowType> previousDelta = previousDeltaOp.getObjectDelta();
        boolean repeated;
        if (lastDelta.isAdd() && previousDelta.isAdd()) {
            repeated = isEquivalentAddDelta(lastDelta.getObjectToAdd(), previousDelta.getObjectToAdd());
        } else if (lastDelta.isModify() && previousDelta.isModify()) {
            repeated = isEquivalentModifyDelta(lastDelta.getModifications(), previousDelta.getModifications());
        } else {
            repeated = false;
        }
        LOGGER.trace(
                "isRepeatedAlreadyExistsException returning {}; based of comparison of previousDelta:\n{}\nwith lastDelta:\n{}",
                repeated, previousDelta, lastDelta);
        return repeated;
    }

    private boolean isEquivalentModifyDelta(Collection<? extends ItemDelta<?, ?>> modifications1,
            Collection<? extends ItemDelta<?, ?>> modifications2) {
        Collection<? extends ItemDelta<?, ?>> attrDeltas1 = findItemDeltasSubPath(modifications1, ShadowType.F_ATTRIBUTES);
        Collection<? extends ItemDelta<?, ?>> attrDeltas2 = findItemDeltasSubPath(modifications2, ShadowType.F_ATTRIBUTES);
        return MiscUtil.unorderedCollectionEquals(attrDeltas1, attrDeltas2);
    }

    private boolean isEquivalentAddDelta(PrismObject<ShadowType> object1, PrismObject<ShadowType> object2) {
        PrismContainer<ShadowAttributesType> attributes1 = object1.findContainer(ShadowType.F_ATTRIBUTES);
        PrismContainer<ShadowAttributesType> attributes2 = object2.findContainer(ShadowType.F_ATTRIBUTES);
        if (attributes1 == null || attributes2 == null || attributes1.size() != 1
                || attributes2.size() != 1) { // suspicious cases
            return false;
        }
        return attributes1.getValue().equivalent(attributes2.getValue());
    }

    /**
     * Make sure that the account is linked (or unlinked) as needed.
     */
    private void updateLinks(OperationResult result) throws ObjectNotFoundException, SchemaException {
        LensFocusContext<O> focusContext = context.getFocusContext();
        if (focusContext == null || !focusContext.represents(FocusType.class)) {
            LOGGER.trace("Missing or non-FocusType focus context, not updating the links");
            return;
        }

        if (projCtx.isHigherOrder()) {
            LOGGER.trace("Won't mess with links for higher-order contexts. "
                    + "The link should be dealt with during processing of zero-order context.");
            return;
        }

        //noinspection unchecked
        new LinkUpdater<>(context, (LensFocusContext<? extends FocusType>) focusContext, projCtx, shadowLivenessState, task, b)
                .updateLinks(result);
    }
}
