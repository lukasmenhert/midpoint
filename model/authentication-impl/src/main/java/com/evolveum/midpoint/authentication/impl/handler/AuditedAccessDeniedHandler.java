/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.authentication.impl.handler;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.evolveum.midpoint.authentication.impl.util.AuthSequenceUtil;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;

public class AuditedAccessDeniedHandler extends MidpointAccessDeniedHandler {

    private static final String OP_AUDIT_EVENT = AuditedAccessDeniedHandler.class.getName() + ".auditEvent";

    @Autowired private TaskManager taskManager;
    @Autowired private AuditService auditService;
    @Autowired private PrismContext prismContext;

    @Override
    protected boolean handleInternal(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        boolean ended = super.handleInternal(request, response, accessDeniedException);
        if (ended) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        auditEvent(request, authentication, accessDeniedException);

        return false;
    }

    private void auditEvent(HttpServletRequest request, Authentication authentication, AccessDeniedException accessDeniedException) {
        OperationResult result = new OperationResult(OP_AUDIT_EVENT); // Eventually we should get this from the caller

        MidPointPrincipal principal = AuthUtil.getPrincipalUser(authentication);
        PrismObject<? extends FocusType> user = principal != null ? principal.getFocus().asPrismObject() : null;

        String channel = SchemaConstants.CHANNEL_USER_URI;
        if (authentication instanceof MidpointAuthentication
                && ((MidpointAuthentication) authentication).getAuthenticationChannel() != null) {
            channel = ((MidpointAuthentication) authentication).getAuthenticationChannel().getChannelId();
        }

        Task task = taskManager.createTaskInstance();
        task.setOwner(user);
        task.setChannel(channel);

        AuditEventRecord record = new AuditEventRecord(AuditEventType.CREATE_SESSION, AuditEventStage.REQUEST);
        record.setInitiator(user);
        record.setParameter(AuthSequenceUtil.getName(user));

        record.setChannel(channel);
        record.setTimestamp(System.currentTimeMillis());
        record.setOutcome(OperationResultStatus.FATAL_ERROR);

        // probably not needed, as audit service would take care of it; but it doesn't hurt so let's keep it here
        record.setHostIdentifier(request.getLocalName());
        record.setRemoteHostAddress(request.getLocalAddr());
        record.setNodeIdentifier(taskManager.getNodeId());
        record.setSessionIdentifier(request.getRequestedSessionId());
        record.setMessage(accessDeniedException.getMessage());

        auditService.audit(record, task, result);
    }
}
