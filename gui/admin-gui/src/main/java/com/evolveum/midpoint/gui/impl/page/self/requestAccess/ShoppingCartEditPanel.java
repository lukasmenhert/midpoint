/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.component.DisplayNamePanel;
import com.evolveum.midpoint.gui.api.factory.wrapper.PrismObjectWrapperFactory;
import com.evolveum.midpoint.gui.api.factory.wrapper.WrapperContext;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismObjectWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.AssignmentsDetailsPanel;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.dialog.Popupable;
import com.evolveum.midpoint.web.component.prism.ItemVisibility;
import com.evolveum.midpoint.web.component.util.EnableBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ShoppingCartEditPanel extends BasePanel<ShoppingCartItem> implements Popupable {

    private static final long serialVersionUID = 1L;

    private static final Trace LOGGER = TraceManager.getTrace(ShoppingCartEditPanel.class);

    private static final String ID_BUTTONS = "buttons";
    private static final String ID_SAVE = "save";
    private static final String ID_CLOSE = "close";
    private static final String ID_RELATION = "relation";
    private static final String ID_ADMINISTRATIVE_STATUS = "administrativeStatus";
    private static final String ID_CUSTOM_VALIDITY = "customValidity";
    private static final String ID_EXTENSION = "extension";

    private Fragment footer;

    private IModel<RequestAccess> requestAccess;

    private IModel<List<QName>> relationChoices;

    private IModel<CustomValidity> customValidityModel;

    private IModel<PrismContainerValueWrapper<AssignmentType>> assignmentExtension;

    private boolean validitySettingsEnabled;

    public ShoppingCartEditPanel(IModel<ShoppingCartItem> model, IModel<RequestAccess> requestAccess, boolean validitySettingsEnabled) {
        super(Popupable.ID_CONTENT, model);

        this.requestAccess = requestAccess;
        this.validitySettingsEnabled = validitySettingsEnabled;

        initModels();
        initLayout();
        initFooter();
    }

    private void initModels() {
        relationChoices = new LoadableModel<>(false) {

            @Override
            protected List<QName> load() {
                return requestAccess.getObject().getAvailableRelations(getPageBase());
            }
        };

        customValidityModel = new LoadableModel<>(false) {

            @Override
            protected CustomValidity load() {
                ShoppingCartItem item = getModelObject();

                ActivationType activation = item.getAssignment().getActivation();
                if (activation == null) {
                    return new CustomValidity();
                }

                CustomValidity cv = new CustomValidity();
                cv.setFrom(XmlTypeConverter.toDate(activation.getValidFrom()));
                cv.setTo(XmlTypeConverter.toDate(activation.getValidTo()));

                return cv;
            }
        };

        assignmentExtension = new LoadableModel<>(false) {
            @Override
            protected PrismContainerValueWrapper load() {
                try {
                    Task task = getPageBase().getPageTask();
                    OperationResult result = task.getResult();

                    // we'll clone our assignment for this assignment/extension prism container value wrapper black magic
                    // hack as not to polute original object with mess from wrappers
                    // we'll copy new extension container value to original assignment during save operation
                    AssignmentType assigment = getModelObject().getAssignment().clone();

                    // virtual containers are now collected for Objects, not containers, therefore empty user is created here
                    UserType user = new UserType();
                    user.getAssignment().add(assigment);
                    PrismObjectWrapperFactory<UserType> userWrapperFactory = getPageBase().findObjectWrapperFactory(user.asPrismObject().getDefinition());

                    WrapperContext context = new WrapperContext(task, result);

                    context.setDetailsPageTypeConfiguration(Arrays.asList(createExtensionPanelConfiguration()));
                    context.setCreateIfEmpty(true);

                    // create whole wrapper, instead of only the concrete container value wrapper
                    PrismObjectWrapper<UserType> userWrapper = userWrapperFactory.createObjectWrapper(user.asPrismObject(), ItemStatus.NOT_CHANGED, context);

                    PrismContainerWrapper<AssignmentType> assignmentWrapper = userWrapper.findContainer(UserType.F_ASSIGNMENT);
                    PrismContainerValueWrapper<AssignmentType> valueWrapper = assignmentWrapper.getValues().iterator().next();
                    valueWrapper.setShowEmpty(true);

                    return valueWrapper;
                } catch (Exception ex) {
                    LOGGER.debug("Couldn't load extensions", ex);
                }

                return null;
            }
        };
    }

    private ContainerPanelConfigurationType createExtensionPanelConfiguration() {
        ContainerPanelConfigurationType c = new ContainerPanelConfigurationType();
        c.identifier("sample-panel");
        c.type(AssignmentType.COMPLEX_TYPE);
        c.panelType("formPanel");

        return c;
    }

    private void initLayout() {
        DropDownChoice relation = new DropDownChoice(ID_RELATION, () -> requestAccess.getObject().getRelation(), relationChoices,
                WebComponentUtil.getRelationChoicesRenderer());
        relation.add(new EnableBehaviour(() -> false));
        add(relation);

        AssignmentsDetailsPanel extension = new AssignmentsDetailsPanel(ID_EXTENSION, assignmentExtension, false, createExtensionPanelConfiguration()) {

            @Override
            protected DisplayNamePanel<AssignmentType> createDisplayNamePanel(String displayNamePanelId) {
                DisplayNamePanel panel = super.createDisplayNamePanel(displayNamePanelId);
                panel.add(new VisibleBehaviour(() -> false));
                return panel;
            }

            @Override
            protected @NotNull List<ITab> createTabs() {
                return new ArrayList<>();
            }

            @Override
            protected ItemVisibility getBasicTabVisibity(ItemWrapper<?, ?> itemWrapper) {
                return itemWrapper.getPath().startsWith(ItemPath.create(AssignmentHolderType.F_ASSIGNMENT, AssignmentType.F_EXTENSION)) ? ItemVisibility.AUTO : ItemVisibility.HIDDEN;
            }
        };
        extension.add(new VisibleBehaviour(() -> {
            try {
                PrismContainerWrapper cw = assignmentExtension.getObject().findItem(ItemPath.create(AssignmentType.F_EXTENSION));
                if (cw == null || cw.isEmpty()) {
                    return false;
                }
                PrismContainerValueWrapper pcvw = (PrismContainerValueWrapper) cw.getValue();
                List items = pcvw.getItems();

                return items != null && !items.isEmpty();
            } catch (SchemaException ex) {
                return true;
            }
        }));
        add(extension);

        IModel<ActivationStatusType> model = new Model<>() {

            @Override
            public ActivationStatusType getObject() {
                AssignmentType assignment = getModelObject().getAssignment();
                ActivationType activation = assignment.getActivation();
                if (activation == null) {
                    return null;
                }

                return activation.getAdministrativeStatus();
            }

            @Override
            public void setObject(ActivationStatusType status) {
                AssignmentType assignment = getModelObject().getAssignment();
                ActivationType activation = assignment.getActivation();
                if (activation == null) {
                    activation = new ActivationType();
                    assignment.setActivation(activation);
                }

                activation.setAdministrativeStatus(status);
            }
        };

        DropDownChoice administrativeStatus = new DropDownChoice(ID_ADMINISTRATIVE_STATUS, model,
                WebComponentUtil.createReadonlyModelFromEnum(ActivationStatusType.class),
                WebComponentUtil.getEnumChoiceRenderer(this));
        administrativeStatus.setNullValid(true);
        add(administrativeStatus);

        CustomValidityPanel customValidity = new CustomValidityPanel(ID_CUSTOM_VALIDITY, customValidityModel);
        customValidity.add(new EnableBehaviour(() -> validitySettingsEnabled));
        add(customValidity);
    }

    private void initFooter() {
        footer = new Fragment(Popupable.ID_FOOTER, ID_BUTTONS, this);
        footer.add(new AjaxSubmitLink(ID_SAVE) {

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                ShoppingCartItem item = getModelObject();
                AssignmentType assignment = item.getAssignment();
                ActivationType activation = assignment.getActivation();
                if (activation == null) {
                    activation = new ActivationType();
                    assignment.setActivation(activation);
                }

                CustomValidity cv = customValidityModel.getObject();
                XMLGregorianCalendar from = XmlTypeConverter.createXMLGregorianCalendar(cv.getFrom());
                XMLGregorianCalendar to = XmlTypeConverter.createXMLGregorianCalendar(cv.getTo());

                activation.validFrom(from).validTo(to);

                savePerformed(target, ShoppingCartEditPanel.this.getModel());
            }
        });
        footer.add(new AjaxLink<>(ID_CLOSE) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                closePerformed(target, ShoppingCartEditPanel.this.getModel());
            }
        });
    }

    @Override
    public Component getFooter() {
        return footer;
    }

    @Override
    public int getWidth() {
        return 1000;
    }

    @Override
    public int getHeight() {
        return 100;
    }

    @Override
    public String getWidthUnit() {
        return "px";
    }

    @Override
    public String getHeightUnit() {
        return "px";
    }

    @Override
    public IModel<String> getTitle() {
        return () -> getString("ShoppingCartEditPanel.title", getModelObject().getName());
    }

    @Override
    public Component getContent() {
        return this;
    }

    protected void savePerformed(AjaxRequestTarget target, IModel<ShoppingCartItem> model) {
        // this is just a nasty "pre-save" code to handle assignment extension via wrappers -> apply it to our assignment stored in request access
        PrismObjectWrapper<UserType> wrapper = assignmentExtension.getObject().getParent().findObjectWrapper();
        List<AssignmentType> modifiedList = wrapper.getObject().asObjectable().getAssignment();
        if (modifiedList.isEmpty()) {
            return;
        }

        AssignmentType newOne = modifiedList.get(0);

        AssignmentType a = getModelObject().getAssignment();
        a.setExtension(newOne.getExtension());
    }

    protected void closePerformed(AjaxRequestTarget target, IModel<ShoppingCartItem> model) {

    }
}