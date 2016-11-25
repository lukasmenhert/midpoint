/*
 * Copyright (c) 2010-2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.impl.policy.global;

import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.builder.DeltaBuilder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.wf.impl.policy.AbstractTestAssignmentApproval;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * Shouldn't be used, as global policy rules are not implemented yet.
 *
 * @author mederly
 */
public class TestAssignmentApprovalGlobal extends AbstractTestAssignmentApproval {

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);

		/*
			<globalPolicyRule>
				<policyConstraints>
					<assignment/>
				</policyConstraints>
				<policyActions>
					<approval>
						<approverRelation>approver</approverRelation>
					</approval>
				</policyActions>
			</globalPolicyRule>
		 */

		GlobalPolicyRuleType rule = new GlobalPolicyRuleType(prismContext);
		PolicyConstraintsType constraints = new PolicyConstraintsType(prismContext);
		constraints.getAssignment().add(new AssignmentPolicyConstraintType(prismContext));
		rule.setPolicyConstraints(constraints);
		PolicyActionsType actions = new PolicyActionsType(prismContext);
		ApprovalPolicyActionType approvalAction = new ApprovalPolicyActionType(prismContext);
		approvalAction.getApproverRelation().add(new QName("approverXX"));		// intentionally unqualified
		actions.setApproval(approvalAction);
		rule.setPolicyActions(actions);

		List<ItemDelta<?, ?>> deltas =
				DeltaBuilder.deltaFor(SystemConfigurationType.class, prismContext)
						.item(SystemConfigurationType.F_GLOBAL_POLICY_RULE)
						.replace(rule)
						.asItemDeltas();
		repositoryService.modifyObject(SystemConfigurationType.class, SystemObjectsType.SYSTEM_CONFIGURATION.value(), deltas, initResult);

	}
}
