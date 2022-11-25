/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.model.api.visualizer.ModelContextVisualization;
import com.evolveum.midpoint.model.api.visualizer.Visualization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.TerminateSessionEvent;
import com.evolveum.midpoint.model.api.authentication.CompiledGuiProfile;
import com.evolveum.midpoint.model.api.authentication.CompiledObjectCollectionView;
import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.util.MergeDeltas;
import com.evolveum.midpoint.model.api.validator.StringLimitationResult;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ResourceShadowCoordinates;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.schema.processor.ResourceObjectDefinition;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.statistics.ConnectorOperationalStatus;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.enforcer.api.ItemSecurityConstraints;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.CheckedProducer;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.Producer;
import com.evolveum.midpoint.util.annotation.Experimental;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.ExecuteCredentialResetRequestType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.ExecuteCredentialResetResponseType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.PolicyItemsDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_3.UserSessionManagementType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;

/**
 * A service provided by the IDM Model that allows to improve the (user) interaction with the model.
 * It is supposed to provide services such as preview of changes, diagnostics and other informational
 * services. It should only provide access to read-only data or provide a temporary (throw-away) previews
 * of data. It should not change the state of IDM repository, resources or tasks.
 *
 * EXPERIMENTAL/UNSTABLE: This is likely to change at any moment without a notice. Depend on this interface on your own risk.
 *
 * @author Radovan Semancik
 *
 */
@Experimental
public interface ModelInteractionService {

    String CLASS_NAME_WITH_DOT = ModelInteractionService.class.getName() + ".";
    String PREVIEW_CHANGES = CLASS_NAME_WITH_DOT + "previewChanges";
    String GET_EDIT_OBJECT_DEFINITION = CLASS_NAME_WITH_DOT + "getEditObjectDefinition";
    String GET_EDIT_SHADOW_DEFINITION = CLASS_NAME_WITH_DOT + "getEditShadowDefinition";
    String GET_ALLOWED_REQUEST_ASSIGNMENT_ITEMS = CLASS_NAME_WITH_DOT + "getAllowedRequestAssignmentItems";
    String GET_ASSIGNABLE_ROLE_SPECIFICATION = CLASS_NAME_WITH_DOT + "getAssignableRoleSpecification";
    String GET_CREDENTIALS_POLICY = CLASS_NAME_WITH_DOT + "getCredentialsPolicy";
    String GET_AUTHENTICATIONS_POLICY = CLASS_NAME_WITH_DOT + "getAuthenticationsPolicy";
    String GET_REGISTRATIONS_POLICY = CLASS_NAME_WITH_DOT + "getRegistrationsPolicy";
    String GET_SECURITY_POLICY = CLASS_NAME_WITH_DOT + "resolveSecurityPolicy";
    String CHECK_PASSWORD = CLASS_NAME_WITH_DOT + "checkPassword";
    String GET_CONNECTOR_OPERATIONAL_STATUS = CLASS_NAME_WITH_DOT + "getConnectorOperationalStatus";
    String MERGE_OBJECTS_PREVIEW_DELTA = CLASS_NAME_WITH_DOT + "mergeObjectsPreviewDelta";
    String MERGE_OBJECTS_PREVIEW_OBJECT = CLASS_NAME_WITH_DOT + "mergeObjectsPreviewObject";
    String GET_DEPUTY_ASSIGNEES = CLASS_NAME_WITH_DOT + "getDeputyAssignees";
    String SUBMIT_TASK_FROM_TEMPLATE = CLASS_NAME_WITH_DOT + "submitTaskFromTemplate";

    /**
     * Computes the most likely changes triggered by the provided delta. The delta may be any change of any object, e.g.
     * add of a user or change of a shadow. The resulting context will sort that out to "focus" and "projection" as needed.
     * The supplied delta will be used as a primary change. The resulting context will reflect both this primary change and
     * any resulting secondary changes.
     * <p>
     * The changes are only computed, NOT EXECUTED. It also does not change any state of any repository object or task. Therefore,
     * this method is safe to use anytime. However, it is reading the data from the repository and possibly also from the resources
     * therefore there is still potential for communication (and other) errors and invocation of this method may not be cheap.
     * However, as no operations are really executed there may be issues with resource dependencies. E.g. identifier that are generated
     * by the resource are not taken into account while recomputing the values. This may also cause errors if some expressions depend
     * on the generated values.
     *
     * @param task Task is expected to have execution mode != {@link com.evolveum.midpoint.schema.TaskExecutionMode#PRODUCTION} otherwise exception is thrown
     */
    <F extends ObjectType> ModelContext<F> previewChanges(
            Collection<ObjectDelta<? extends ObjectType>> deltas, ModelExecuteOptions options, Task task, OperationResult result)
            throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException, ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException;

    <F extends ObjectType> ModelContext<F> previewChanges(
            Collection<ObjectDelta<? extends ObjectType>> deltas, ModelExecuteOptions options, Task task, Collection<ProgressListener> listeners, OperationResult result)
            throws SchemaException, PolicyViolationException, ExpressionEvaluationException, ObjectNotFoundException, ObjectAlreadyExistsException, CommunicationException, ConfigurationException, SecurityViolationException;

    <F extends ObjectType> ModelContext<F> unwrapModelContext(LensContextType wrappedContext, Task task, OperationResult result) throws SchemaException, ConfigurationException, ObjectNotFoundException, CommunicationException, ExpressionEvaluationException;

    /**
     * <p>
     * Returns a schema that reflects editability of the object in terms of midPoint schema limitations and security. This method
     * merges together all the applicable limitations that midPoint knows of (schema, security, other constraints). It may be required
     * to pre-populate new object before calling this method, e.g. to put the object in a correct org in case that delegated administration
     * is used.
     * </p>
     * <p>
     * If null is returned then the access to the entire object is denied. It cannot be created or edited at all.
     * </p>
     * <p>
     * The returned definition contains all parts of static schema and run-time extensions. It does not contain parts of resource
     * "refined" schemas. Therefore for shadows it is only applicable to static parts of the shadow (not attributes).
     * </p>
     * <p>
     * This is <b>not</b> security-sensitive function. It provides data about security constraints but it does <b>not</b> enforce it and
     * it does not modify anything or reveal any data. The purpose of this method is to enable convenient display of GUI form fields,
     * e.g. to hide non-accessible fields from the form. The actual enforcement of the security is executed regardless of this
     * method.
     * </p>
     *
     * @param object object to edit
     * @return schema with correctly set constraint parts or null
     */
    <O extends ObjectType> PrismObjectDefinition<O> getEditObjectDefinition(PrismObject<O> object, AuthorizationPhaseType phase, Task task, OperationResult result) throws SchemaException, ConfigurationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, SecurityViolationException;

    PrismObjectDefinition<ShadowType> getEditShadowDefinition(
            ResourceShadowCoordinates coordinates,
            AuthorizationPhaseType phase,
            Task task,
            OperationResult result) throws SchemaException, ConfigurationException, ObjectNotFoundException,
            ExpressionEvaluationException, CommunicationException, SecurityViolationException;

    ResourceObjectDefinition getEditObjectClassDefinition(PrismObject<ShadowType> shadow, PrismObject<ResourceType> resource, AuthorizationPhaseType phase, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException;

    /**
     * Returns specification of processing of given metadata item (e.g. provenance).
     * The caller can use returned object to find out the processing of given metadata item
     * for various data items (e.g. givenName, familyName, etc).
     */
    @Experimental
    <O extends ObjectType> MetadataItemProcessingSpec getMetadataItemProcessingSpec(ItemPath metadataItemPath, PrismObject<O> object,
            Task task, OperationResult result) throws SchemaException, ConfigurationException, ObjectNotFoundException,
            ExpressionEvaluationException, CommunicationException, SecurityViolationException;

    /**
     * <p>
     * Returns a collection of all authorization actions known to the system. The format of returned data is designed for displaying
     * purposes.
     * </p>
     * <p>
     * Note: this method returns only the list of authorization actions that are known to the IDM Model component and the components
     * below. It does <b>not</b> return a GUI-specific authorization actions.
     * </p>
     *
     * @return
     */
    Collection<? extends DisplayableValue<String>> getActionUrls();

    /**
     * Returns an object that defines which roles can be assigned by the currently logged-in user.
     *
     * @param assignmentHolder Object of the operation. The object (usually user) to whom the roles should be assigned.
     * @param assignmentOrder order=0 means assignment, order>0 means inducement
     */
    <H extends AssignmentHolderType, R extends AbstractRoleType> RoleSelectionSpecification getAssignableRoleSpecification(PrismObject<H> assignmentHolder, Class<R> targetType, int assignmentOrder, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException, SecurityViolationException;

    /**
     * Returns filter for lookup of donors of power of attorney. The donors are the users that have granted
     * the power of attorney to the currently logged-in user.
     * <p>
     * TODO: authorization limitations
     *
     * @param searchResultType type of the expected search results
     * @param origFilter original filter (e.g. taken from GUI search bar)
     * @param targetAuthorizationAction Authorization action that the attorney is trying to execute
     * on behalf of donor. Only donors for which the use of this authorization was
     * not limited will be returned (that does not necessarily mean that the donor
     * is able to execute this action, it may be limited by donor's authorizations).
     * If the parameter is null then all donors are returned.
     * @param task task
     * @param parentResult operation result
     * @return original filter with AND clause limiting the search.
     */
    <T extends ObjectType> ObjectFilter getDonorFilter(Class<T> searchResultType, ObjectFilter origFilter, String targetAuthorizationAction, Task task, OperationResult parentResult) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException;

    /**
     * TODO
     * Question: does object make any sense here? E.g. when searching role members, the role OID should be determined from the query.
     *
     * @param includeSpecial include special authorizations, such as "self". If set to false those authorizations
     * will be ignored. This is a good way to avoid interference of "self" when checking for
     * authorizations such as ability to display role members.
     */
    <T extends ObjectType, O extends ObjectType> boolean canSearch(Class<T> resultType, Class<O> objectType, String objectOid, boolean includeSpecial, ObjectQuery query, Task task, OperationResult result) throws ObjectNotFoundException, CommunicationException, SchemaException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    /**
     * Returns decisions for individual items for "assign" authorization. This is usually applicable to assignment parameters.
     * The decisions are evaluated using the security context of a currently logged-in user.
     *
     * @param object object of the operation (user)
     * @param target target of the operation (role, org, service that is being assigned)
     */
    <O extends ObjectType, R extends AbstractRoleType> ItemSecurityConstraints getAllowedRequestAssignmentItems(PrismObject<O> object, PrismObject<R> target, Task task, OperationResult result) throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException;

    <F extends FocusType> SecurityPolicyType getSecurityPolicy(PrismObject<F> focus, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    SecurityPolicyType getSecurityPolicy(ResourceObjectDefinition rOCDef, Task task, OperationResult parentResult) throws SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectNotFoundException;

    /**
     * Returns an authentications policies as defined in the system configuration security policy. This method is designed to be used
     * during registration process or reset password process.
     * security questions, etc).
     *
     * @param task
     * @param parentResult
     * @return applicable credentials policy or null
     * @throws ObjectNotFoundException No system configuration or other major system inconsistency
     * @throws SchemaException Wrong schema or content of security policy
     */
    AuthenticationsPolicyType getAuthenticationPolicy(PrismObject<UserType> user, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    /**
     * Returns a policy for registration, e.g. type of the supported registrations (self, social,...)
     *
     * @param focus focus for who the policy should apply
     * @param task
     * @param parentResult
     * @return applicable credentials policy or null
     * @throws ObjectNotFoundException No system configuration or other major system inconsistency
     * @throws SchemaException Wrong schema or content of security policy
     */
    RegistrationsPolicyType getFlowPolicy(PrismObject<? extends FocusType> focus, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    /**
     * Returns a credential policy that applies to the specified user. This method is designed to be used
     * during credential reset so the GUI has enough information to set up the credential (e.g. password policies,
     * security questions, etc).
     *
     * @param focus focus for who the policy should apply
     * @param task
     * @param parentResult
     * @return applicable credentials policy or null
     * @throws ObjectNotFoundException No system configuration or other major system inconsistency
     * @throws SchemaException Wrong schema or content of security policy
     */
    CredentialsPolicyType getCredentialsPolicy(PrismObject<? extends FocusType> focus, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    /**
     * Returns currently applicable user profile, compiled for efficient use in the user interface.
     * Use profile contains configuration, customization and user preferences for the user interface.
     * Note: This operation bypasses the authorizations. It will always return the value regardless whether
     * the current user is authorized to read the underlying objects or not. However, it will always return only
     * values applicable for current user, therefore the authorization might be considered to be implicit in this case.
     */
    @NotNull
    CompiledGuiProfile getCompiledGuiProfile(Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    /**
     * @return list of logged in users with at least 1 active session (clusterwide)
     */
    List<UserSessionManagementType> getLoggedInPrincipals(Task task, OperationResult result);

    /**
     * Terminates specified sessions (clusterwide).
     */
    void terminateSessions(TerminateSessionEvent terminateSessionEvent, Task task, OperationResult result);

    SystemConfigurationType getSystemConfiguration(OperationResult parentResult) throws ObjectNotFoundException, SchemaException;

    DeploymentInformationType getDeploymentInformationConfiguration(OperationResult parentResult) throws ObjectNotFoundException, SchemaException;

    SystemConfigurationAuditType getAuditConfiguration(OperationResult parentResult) throws ObjectNotFoundException, SchemaException;

    List<MergeConfigurationType> getMergeConfiguration(OperationResult parentResult) throws ObjectNotFoundException, SchemaException;

    AccessCertificationConfigurationType getCertificationConfiguration(OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException;

    /**
     * Checks if the supplied password matches with current user password. This method is NOT subject to any
     * password expiration policies, it does not update failed login counters, it does not change any data or meta-data.
     * This method is NOT SUPPOSED to be used to validate password on login. This method is supposed to check
     * old password when the password is changed by the user. We assume that the user already passed normal
     * system authentication.
     * <p>
     * Note: no authorizations are checked in the implementation. It is assumed that authorizations will be
     * enforced at the page level.
     *
     * @return true if the password matches, false otherwise
     */
    boolean checkPassword(String userOid, ProtectedStringType password, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException;

    // TEMPORARY
    List<Visualization> visualizeDeltas(List<ObjectDelta<? extends ObjectType>> deltas, Task task, OperationResult result)
            throws SchemaException, ExpressionEvaluationException;

    <O extends ObjectType> ModelContextVisualization visualizeModelContext(ModelContext<O> context, Task task, OperationResult result)
            throws SchemaException, ExpressionEvaluationException, ConfigurationException;

    @NotNull
    Visualization visualizeDelta(ObjectDelta<? extends ObjectType> delta, Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException;

    @NotNull
    Visualization visualizeDelta(ObjectDelta<? extends ObjectType> delta, boolean includeOperationalItems, Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException;

    @NotNull
    Visualization visualizeDelta(ObjectDelta<? extends ObjectType> delta, boolean includeOperationalItems, ObjectReferenceType objectRef, Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException;

    @NotNull
    Visualization visualizeDelta(ObjectDelta<? extends ObjectType> delta, boolean includeOperationalItems, boolean includeOriginalObject, Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException;

    List<ConnectorOperationalStatus> getConnectorOperationalStatus(String resourceOid, Task task, OperationResult parentResult)
            throws SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, ExpressionEvaluationException;

    <O extends ObjectType> MergeDeltas<O> mergeObjectsPreviewDeltas(Class<O> type,
            String leftOid, String rightOid, String mergeConfigurationName, Task task, OperationResult result)
            throws ObjectNotFoundException, SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException, SecurityViolationException;

    <O extends ObjectType> PrismObject<O> mergeObjectsPreviewObject(Class<O> type,
            String leftOid, String rightOid, String mergeConfigurationName, Task task, OperationResult result)
            throws ObjectNotFoundException, SchemaException, ConfigurationException, ExpressionEvaluationException, CommunicationException, SecurityViolationException;

    /**
     * TEMPORARY. Need to find out better way how to deal with generated values
     *
     * @param policy
     * @param defaultLength
     * @param generateMinimalSize
     * @param object object for which we generate the value (e.g. user or shadow)
     * @param inputResult
     * @return
     * @throws ExpressionEvaluationException
     */
    <O extends ObjectType> String generateValue(ValuePolicyType policy, int defaultLength, boolean generateMinimalSize,
            PrismObject<O> object, String shortDesc, Task task, OperationResult inputResult) throws ExpressionEvaluationException, SchemaException, ObjectNotFoundException, CommunicationException, ConfigurationException, SecurityViolationException;

    <O extends ObjectType> void generateValue(
            PrismObject<O> object, PolicyItemsDefinitionType policyItemsDefinition, Task task, OperationResult parentResult) throws ObjectNotFoundException,
            SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException, ObjectAlreadyExistsException, PolicyViolationException;

    <O extends ObjectType> void validateValue(PrismObject<O> object, PolicyItemsDefinitionType policyItemsDefinition, Task task,
            OperationResult parentResult) throws ExpressionEvaluationException, SchemaException, ObjectNotFoundException,
            CommunicationException, ConfigurationException, SecurityViolationException, PolicyViolationException;

    /**
     * Gets "deputy assignees" i.e. users that are deputies of assignees. Takes limitations into account.
     * <p>
     * MAY NOT CHECK AUTHORIZATIONS (uses repository directly, at least at some places) - TODO
     * TODO parameterize on limitation kind
     */
    @NotNull
    List<ObjectReferenceType> getDeputyAssignees(AbstractWorkItemType workItem, Task task, OperationResult parentResult)
            throws SchemaException;

    @NotNull
    List<ObjectReferenceType> getDeputyAssignees(ObjectReferenceType assignee, QName limitationItemName, Task task, OperationResult parentResult)
            throws SchemaException;

    /**
     * Computes effective status for the current ActivationType in for an assignment
     */
    ActivationStatusType getAssignmentEffectiveStatus(String lifecycleStatus, ActivationType activationType);

    MidPointPrincipal assumePowerOfAttorney(PrismObject<? extends FocusType> donor, Task task, OperationResult result)
            throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException;

    MidPointPrincipal dropPowerOfAttorney(Task task, OperationResult result)
            throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException;

    <T> T runUnderPowerOfAttorney(Producer<T> producer, PrismObject<? extends FocusType> donor, Task task, OperationResult result)
            throws SchemaException, SecurityViolationException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException;

    default <T> T runUnderPowerOfAttorneyChecked(CheckedProducer<T> producer, PrismObject<? extends FocusType> donor, Task task, OperationResult result)
            throws CommonException {
        return MiscUtil.runChecked((p) -> runUnderPowerOfAttorney(p, donor, task, result), producer);
    }

    // Maybe a bit of hack: used to deduplicate processing of localizable message templates
    @NotNull
    LocalizableMessageType createLocalizableMessageType(LocalizableMessageTemplateType template,
            VariablesMap variables, Task task, OperationResult result)
            throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException,
            ConfigurationException, SecurityViolationException;

    ExecuteCredentialResetResponseType executeCredentialsReset(PrismObject<UserType> user,
            ExecuteCredentialResetRequestType executeCredentialResetRequest, Task task, OperationResult result)
            throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException,
            SecurityViolationException, ExpressionEvaluationException, ObjectAlreadyExistsException, PolicyViolationException;

    void refreshPrincipal(String oid, Class<? extends FocusType> clazz) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, SecurityViolationException, ExpressionEvaluationException;

    List<RelationDefinitionType> getRelationDefinitions();

    @NotNull
    TaskType submitTaskFromTemplate(String templateTaskOid, List<Item<?, ?>> extensionItems, Task opTask, OperationResult result)
            throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException, ObjectAlreadyExistsException, PolicyViolationException;

    @NotNull
    TaskType submitTaskFromTemplate(String templateTaskOid, Map<QName, Object> extensionValues, Task opTask, OperationResult result)
            throws CommunicationException, ObjectNotFoundException, SchemaException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException, ObjectAlreadyExistsException, PolicyViolationException;

    /**
     * Efficiently determines information about archetype policy applicable for a particular object.
     * Returns null if no archetype policy is applicable.
     * This is a "one stop" method for archetype policy in the GUI. The method returns archetype policy even
     * for "legacy" situations, e.g. if the policy needs to be determined from system configuration using legacy subtype.
     * GUI should not need to to any other processing to determine archetype information.
     * <p>
     * This method is invoked very often, usually when any object is displayed (including display of object lists
     * and search results). Therefore this method is supposed to be very efficient.
     * It should be using caching as much as possible.
     */
    <O extends AssignmentHolderType> ArchetypePolicyType determineArchetypePolicy(PrismObject<O> assignmentHolder, OperationResult result) throws SchemaException, ConfigurationException;

    ArchetypePolicyType mergeArchetypePolicies(PrismObject<ArchetypeType> archetype, OperationResult result)
            throws SchemaException, ConfigurationException;
    /**
     * Returns data structure that contains information about possible assignment targets for a particular holder object.
     * <p>
     * This method should be used when editing assignment holder (e.g. user) and looking for available assignment target.
     * The determineAssignmentHolderSpecification is a "reverse" version of this method.
     * <p>
     * This method is not used that often. It is used when an object is edited. But it should be quite efficient anyway.
     * It should use cached archetype information.
     */
    <O extends AssignmentHolderType> AssignmentCandidatesSpecification determineAssignmentTargetSpecification(PrismObject<O> assignmentHolder, OperationResult result) throws SchemaException, ConfigurationException;

    /**
     * This method is used to differentiate which archetypes can be added to object with holderType type.
     * e.g. when changing archetype within Change archetype functionality should provide only those archetypes which
     * can be assigned according to holderType.
     */
    <O extends AssignmentHolderType> List<ArchetypeType> getFilteredArchetypesByHolderType(PrismObject<O> object, OperationResult result) throws SchemaException;

    /**
     * Returns data structure that contains information about possible assignment holders for a particular target object.
     * <p>
     * This method should be used when editing assignment target (role, org, service) and looking for object that
     * can be potential members. The determineAssignmentTargetSpecification is a "reverse" version of this method.
     * <p>
     * This method is not used that often. It is used when an object is edited. But it should be quite efficient anyway.
     * It should use cached archetype information.
     */
    <O extends AbstractRoleType> AssignmentCandidatesSpecification determineAssignmentHolderSpecification(PrismObject<O> assignmentTarget, OperationResult result) throws SchemaException, ConfigurationException;

    /**
     * Returns all policy rules that apply to the collection.
     * Later, the policy rules are compiled from all the applicable sources (target, meta-roles, etc.).
     * But for now we support only policy rules that are directly placed in collection assignments.
     * EXPERIMENTAL. Quite likely to change later.
     */
    @Experimental
    @NotNull
    Collection<EvaluatedPolicyRule> evaluateCollectionPolicyRules(@NotNull PrismObject<ObjectCollectionType> collection,
            @Nullable CompiledObjectCollectionView collectionView, @Nullable Class<? extends ObjectType> targetTypeClass,
            @NotNull Task task, @NotNull OperationResult result)
            throws ObjectNotFoundException, SchemaException, SecurityViolationException, CommunicationException,
            ConfigurationException, ExpressionEvaluationException;

    @Experimental
    @NotNull
    CompiledObjectCollectionView compileObjectCollectionView(@NotNull CollectionRefSpecificationType collection, @Nullable Class<? extends Containerable> targetTypeClass, @NotNull Task task, @NotNull OperationResult result)
            throws SchemaException, CommunicationException, ConfigurationException, SecurityViolationException,
            ExpressionEvaluationException, ObjectNotFoundException;

    @Experimental
    @NotNull <O extends ObjectType> CollectionStats determineCollectionStats(@NotNull CompiledObjectCollectionView collectionView, @NotNull Task task, @NotNull OperationResult result)
            throws SchemaException, ObjectNotFoundException, SecurityViolationException, ConfigurationException, CommunicationException, ExpressionEvaluationException;

    /**
     * Applying all GuiObjectListViewsType to CompiledObjectCollectionView
     */
    @Experimental
    void applyView(CompiledObjectCollectionView existingView, GuiObjectListViewType objectListViewsType);

    /**
     * Compile object list view together with collection ref specification if present
     */
    void compileView(CompiledObjectCollectionView existingView, GuiObjectListViewType objectListViewsType, Task task, OperationResult result) throws SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException, ConfigurationException, ObjectNotFoundException;
    @Experimental
    <O extends ObjectType> List<StringLimitationResult> validateValue(ProtectedStringType protectedStringValue, ValuePolicyType pp, PrismObject<O> object, Task task, OperationResult parentResult)
            throws SchemaException, PolicyViolationException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException;

    /**
     * TODO document
     */
    @Experimental
    void processObjectsFromCollection(CollectionRefSpecificationType collection, QName typeForFilter, Predicate<PrismContainer> handler,
            Collection<SelectorOptions<GetOperationOptions>> options, VariablesMap variables, Task task, OperationResult result, boolean recordProgress) throws SchemaException,
            ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException;

    /**
     * TODO document and clean up the interface
     */
    @Experimental
    <C extends Containerable> SearchSpec<C> getSearchSpecificationFromCollection(CompiledObjectCollectionView collection, QName typeForFilter,
            Collection<SelectorOptions<GetOperationOptions>> options, VariablesMap variables, Task task, OperationResult result)
            throws ConfigurationException, SchemaException, ExpressionEvaluationException, CommunicationException, SecurityViolationException, ObjectNotFoundException;

    class SearchSpec<C extends Containerable> {
        public Class<C> type;
        public ObjectQuery query;
        public Collection<SelectorOptions<GetOperationOptions>> options;
    }

    @Experimental
    List<? extends Containerable> searchObjectsFromCollection(CollectionRefSpecificationType collectionConfig, QName typeForFilter,
            Collection<SelectorOptions<GetOperationOptions>> defaultOptions, ObjectPaging usedPaging, VariablesMap variables, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException;

    @Experimental
    Integer countObjectsFromCollection(CollectionRefSpecificationType collectionConfig, QName typeForFilter,
            Collection<SelectorOptions<GetOperationOptions>> defaultOptions, ObjectPaging usedPaging, VariablesMap variables, Task task, OperationResult result)
            throws SchemaException, ObjectNotFoundException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException;

    /**
     * See {@link ProvisioningService#expandConfigurationObject(PrismObject, Task, OperationResult)} for the description.
     *
     * TODO security aspects
     */
    @Experimental
    void expandConfigurationObject(
            @NotNull PrismObject<? extends ObjectType> configurationObject,
            @NotNull Task task,
            @NotNull OperationResult result) throws SchemaException, ConfigurationException, ObjectNotFoundException;

    /**
     * Executes given task in the background, i.e., adds it to the repository.
     *
     * The state before 4.6 was that GUI was responsible for submitting tasks that were needed for execution of long-running
     * activities (like recomputation of role members).
     *
     * The optimal state is that GUI declares the work that should be done (like "recompute members of role X") and the model
     * will then decide the optimal way of doing that (e.g., on foreground or on background) and executes the action.
     * When determining the way it needs to consider user preferences and/or authorizations, or the situation, like how many
     * members are there.
     *
     * The goal is to better isolate GUI from the rest of midPoint, and to provide means for 3rd party GUI implementations.
     * The current method should be seen as a (very rough) placeholder.
     *
     * The method does not require any authorizations. This is how it was before 4.6, and it remains so for the near future.
     */
    void switchToBackground(Task task, OperationResult result);

    /**
     * Returns Container Definition of Assignment Type with target type of assignment replaced by more concrete situation
     *
     * This allows for using more specific definition when searching for definitions for dereference, in GUI search or columns
     * where we are sure (on other criteria) only assignment types we are processing have concrete target type.
     *
     * @param orig Original definition of Assignment Type
     * @param targetType Concrete target type
     */
    PrismContainerDefinition<AssignmentType> assignmentTypeDefinitionWithConcreteTargetRefType(
            PrismContainerDefinition<AssignmentType> orig, QName targetType);
}
