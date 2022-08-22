/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.page.admin.resource.component.wizard.objectType.attributeMapping;

import com.evolveum.midpoint.gui.api.component.wizard.AbstractWizardBasicPanel;
import com.evolveum.midpoint.gui.api.factory.wrapper.WrapperContext;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.prism.ItemStatus;
import com.evolveum.midpoint.gui.api.prism.wrapper.ItemWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.impl.component.MultivalueContainerListPanelWithDetailsPanel;
import com.evolveum.midpoint.gui.impl.component.data.column.AbstractItemWrapperColumn;
import com.evolveum.midpoint.gui.impl.component.data.column.PrismPropertyWrapperColumn;
import com.evolveum.midpoint.gui.impl.page.admin.resource.ResourceDetailsModel;
import com.evolveum.midpoint.gui.impl.page.admin.resource.component.ResourceSchemaHandlingPanel;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxIconButton;
import com.evolveum.midpoint.web.component.data.column.IconColumn;
import com.evolveum.midpoint.web.session.UserProfileStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lskublik
 */
public abstract class AttributeMappingsTableWizardPanel extends AbstractWizardBasicPanel {

    private static final Trace LOGGER = TraceManager.getTrace(AttributeMappingsTableWizardPanel.class);

    private static final String PANEL_TYPE = "schemaHandling";
    private static final String ID_TABLE = "table";

    private final IModel<PrismContainerValueWrapper<ResourceObjectTypeDefinitionType>> valueModel;

    public AttributeMappingsTableWizardPanel(
            String id,
            ResourceDetailsModel model,
            IModel<PrismContainerValueWrapper<ResourceObjectTypeDefinitionType>> valueModel) {
        super(id, model);
        this.valueModel = valueModel;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        initLayout();
    }

    private void initLayout() {

        MultivalueContainerListPanelWithDetailsPanel<MappingType> table
                = new MultivalueContainerListPanelWithDetailsPanel<>(
                ID_TABLE, MappingType.class) {
            @Override
            protected WebMarkupContainer getMultivalueContainerDetailsPanel(ListItem item) {
                return new WebMarkupContainer(MultivalueContainerListPanelWithDetailsPanel.ID_ITEM_DETAILS);
            }

            @Override
            protected boolean isCreateNewObjectVisible() {
                return true;
            }

            @Override
            protected IModel<PrismContainerWrapper<MappingType>> getContainerModel() {
                return new LoadableDetachableModel<>() {
                    @Override
                    protected PrismContainerWrapper<MappingType> load() {
                        PrismContainerValueWrapper<ResourceObjectTypeDefinitionType> container = valueModel.getObject();
                        ItemDefinition<?> def = container.getDefinition().findContainerDefinition(
                                ItemPath.create(ResourceObjectTypeDefinitionType.F_ATTRIBUTE, ResourceAttributeDefinitionType.F_INBOUND));
                        try {
                            Task task = getPageBase().createSimpleTask("Create virtual item");
                            OperationResult result = task.getResult();
                            WrapperContext context = new WrapperContext(task, result);
                            context.setCreateIfEmpty(false);
                            PrismContainerWrapper<MappingType> virtualInboundMappingContainer = getPageBase().createItemWrapper(def.instantiate(), ItemStatus.ADDED, context);

                            PrismContainerWrapper<ResourceAttributeDefinitionType> mappingAttributeContainer =
                                    valueModel.getObject().findContainer(ResourceObjectTypeDefinitionType.F_ATTRIBUTE);

                            for (PrismContainerValueWrapper<ResourceAttributeDefinitionType> value : mappingAttributeContainer.getValues()) {

                                PrismContainerWrapper<MappingType> inboundContainer = value.findContainer(ResourceAttributeDefinitionType.F_INBOUND);
                                PrismPropertyDefinition<Object> propertyDef = container.getDefinition().findPropertyDefinition(
                                        ItemPath.create(ResourceObjectTypeDefinitionType.F_ATTRIBUTE, ResourceAttributeDefinitionType.F_REF));

                                for (PrismContainerValueWrapper<MappingType> inbound : inboundContainer.getValues()) {

                                    if (inbound.findProperty(ResourceAttributeDefinitionType.F_REF) == null) {

                                        ItemWrapper refItemWrapper = getPageBase().createItemWrapper(
                                                propertyDef.instantiate(),
                                                inbound,
                                                ItemStatus.ADDED,
                                                new WrapperContext(task, result));

                                        if (value.getRealValue() != null && value.getRealValue().getRef() != null) {
                                            refItemWrapper.getValue().setRealValue(value.getRealValue().getRef().clone());
                                        }

                                        refItemWrapper.setVisibleOverwrite(UserInterfaceElementVisibilityType.HIDDEN);
                                        inbound.addItem(refItemWrapper);
                                    }
                                    virtualInboundMappingContainer.getValues().add(inbound);
                                }
                            }
                            return virtualInboundMappingContainer;

                        } catch (SchemaException e) {
                            LOGGER.error("Couldn't instantiate virtual container for inbound mappings", e);
                        }
                        return null;
                    }
                };
            }

            @Override
            protected UserProfileStorage.TableId getTableId() {
                return UserProfileStorage.TableId.PANEL_INBOUND_MAPPING_WIZARD;
            }

            @Override
            protected List<IColumn<PrismContainerValueWrapper<MappingType>, String>> createDefaultColumns() {
                List<IColumn<PrismContainerValueWrapper<MappingType>, String>> columns = new ArrayList<>();

                IModel<PrismContainerDefinition<MappingType>> mappingTypeDef =
                        getMappingTypeDefinition();
                columns.add(new PrismPropertyWrapperColumn<MappingType, String>(
                        mappingTypeDef,
                        MappingType.F_NAME,
                        AbstractItemWrapperColumn.ColumnType.VALUE,
                        getPageBase()));

                Model<PrismContainerDefinition<ResourceAttributeDefinitionType>> resourceAttributeDef =
                        Model.of(PrismContext.get().getSchemaRegistry().findContainerDefinitionByCompileTimeClass(
                                ResourceAttributeDefinitionType.class));
                columns.add(new PrismPropertyWrapperColumn(
                        resourceAttributeDef,
                        ResourceAttributeDefinitionType.F_REF,
                        AbstractItemWrapperColumn.ColumnType.VALUE,
                        getPageBase()));

//                columns.add(new AbstractColumn<>(Model.of()) {
//                    @Override
//                    public void populateItem(
//                            Item<ICellPopulator<PrismContainerValueWrapper<MappingType>>> item,
//                            String componentId,
//                            IModel<PrismContainerValueWrapper<MappingType>> iModel) {
//                        item.add(new Icon);
//                    }
//                });

                columns.add(new IconColumn<>(Model.of()) {
                    @Override
                    protected DisplayType getIconDisplayType(IModel<PrismContainerValueWrapper<MappingType>> rowModel) {
                        return new DisplayType().beginIcon().cssClass("fa fa-minus text-secondary").end();
                    }

                    @Override
                    public String getCssClass() {
                        return "";
                    }
                });

                columns.add(new IconColumn<>(Model.of()) {
                    @Override
                    protected DisplayType getIconDisplayType(IModel<PrismContainerValueWrapper<MappingType>> rowModel) {
                        return new DisplayType().beginIcon().cssClass("fa fa-arrow-right-long text-secondary").end();
                    }

                    @Override
                    public String getCssClass() {
                        return "";
                    }
                });

                columns.add(new PrismPropertyWrapperColumn<MappingType, String>(
                        mappingTypeDef,
                        MappingType.F_TARGET,
                        AbstractItemWrapperColumn.ColumnType.VALUE,
                        getPageBase()));

                return columns;
            }

        };
//        ResourceAttributePanel table = new ResourceAttributePanel(
//                ID_TABLE,
//                PrismContainerWrapperModel.fromContainerValueWrapper(valueModel, ResourceObjectTypeDefinitionType.F_ATTRIBUTE),
//                getConfiguration()) {
//
//            @Override
//            protected boolean customEditItemPerformed(
//                    AjaxRequestTarget target,
//                    IModel<PrismContainerValueWrapper<ResourceAttributeDefinitionType>> rowModel,
//                    List<PrismContainerValueWrapper<ResourceAttributeDefinitionType>> listItems) {
//                if ((listItems != null && !listItems.isEmpty()) || rowModel != null) {
//                    IModel<PrismContainerValueWrapper<ResourceAttributeDefinitionType>> valueModel;
//                    if (rowModel == null) {
//                        valueModel = () -> listItems.iterator().next();
//                    } else {
//                        valueModel = rowModel;
//                    }
//                    onEditValue(valueModel, target);
//                } else {
//                    warn(createStringResource("MultivalueContainerListPanel.message.noItemsSelected").getString());
//                    target.add(getPageBase().getFeedbackPanel());
//                }
//                return true;
//            }
//
//            @Override
//            protected boolean isCreateNewObjectVisible() {
//                return false;
//            }
//        };
        table.setOutputMarkupId(true);
        add(table);
    }

    private LoadableModel<PrismContainerDefinition<MappingType>> getMappingTypeDefinition() {
        return new LoadableModel<>() {
            @Override
            protected PrismContainerDefinition<MappingType> load() {
                return PrismContext.get().getSchemaRegistry().findContainerDefinitionByCompileTimeClass(MappingType.class);
            }
        };
    }

    public MultivalueContainerListPanelWithDetailsPanel getTable() {
        return ((ResourceSchemaHandlingPanel) get(ID_TABLE)).getTable();
    }

    ContainerPanelConfigurationType getConfiguration() {
        return WebComponentUtil.getContainerConfiguration(
                getResourceModel().getObjectDetailsPageConfiguration().getObject(),
                PANEL_TYPE);
    }

    @Override
    protected void addCustomButtons(RepeatingView buttons) {
        AjaxIconButton newObjectTypeButton = new AjaxIconButton(
                buttons.newChildId(),
                Model.of("fa fa-circle-plus"),
                getPageBase().createStringResource("AttributeMappingsTableWizardPanel.addNewAttributeMapping")) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                onAddNewObject(target);
            }
        };
        newObjectTypeButton.showTitleAsLabel(true);
        newObjectTypeButton.add(AttributeAppender.append("class", "btn btn-primary"));
        buttons.add(newObjectTypeButton);
    }

    protected abstract void onAddNewObject(AjaxRequestTarget target);

    protected abstract void onEditValue(IModel<PrismContainerValueWrapper<ResourceAttributeDefinitionType>> value, AjaxRequestTarget target);

    @Override
    protected IModel<String> getBreadcrumbLabel() {
        return getPageBase().createStringResource("AttributeMappingsTableWizardPanel.title");
    }

    @Override
    protected IModel<String> getTextModel() {
        return getPageBase().createStringResource("AttributeMappingsTableWizardPanel.text");
    }

    @Override
    protected IModel<String> getSubTextModel() {
        return getPageBase().createStringResource("AttributeMappingsTableWizardPanel.subText");
    }
}
