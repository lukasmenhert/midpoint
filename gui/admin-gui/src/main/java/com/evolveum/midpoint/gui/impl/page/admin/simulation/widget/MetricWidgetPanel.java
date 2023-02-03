/*
 * Copyright (c) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.simulation.widget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;

import com.evolveum.midpoint.gui.api.component.Badge;
import com.evolveum.midpoint.gui.api.component.BadgePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.GuiDisplayTypeUtil;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.component.data.column.CompositedIconPanel;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIcon;
import com.evolveum.midpoint.gui.impl.component.icon.CompositedIconBuilder;
import com.evolveum.midpoint.gui.impl.component.icon.IconCssStyle;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.SimulationMetricValuesTypeUtil;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;

/**
 * Created by Viliam Repan (lazyman).
 */
public class MetricWidgetPanel extends WidgetPanel<DashboardWidgetType> {

    private static final long serialVersionUID = 1L;

    private static final String ID_TITLE = "title";
    private static final String ID_OPEN = "open";
    private static final String ID_TREND_BADGE = "trendBadge";
    private static final String ID_VALUE = "value";
    private static final String ID_VALUE_DESCRIPTION = "valueDescription";
    private static final String ID_ICON = "icon";
    private static final String ID_ICON_CONTAINTER = "iconContainer";
    private static final String ID_CHART_CONTAINER = "chartContainer";

    private IModel<List<SimulationMetricValuesType>> metricValues;

    private IModel<SimulationMetricDefinitionType> metricDefinition;

    public MetricWidgetPanel(String id, IModel<DashboardWidgetType> model) {
        super(id, model);

        initModels();
        initLayout();
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        super.onComponentTag(tag);

        checkComponentTag(tag, "div");
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        Component comp = get(ID_CHART_CONTAINER);
        if (comp == null || !comp.isVisibleInHierarchy()) {
            return;
        }

        Object[] array = metricValues.getObject().stream()
                .map(SimulationMetricValuesTypeUtil::getValue)
                .map(BigDecimal::doubleValue)
                .toArray();

        if (array.length == 0) {
            return;
        }

        String options = "{ height: 85, lineColor: '#92c1dc', endColor: '#92c1dc' }";
        String data = "[" + StringUtils.join(array, ", ") + "]";

        response.render(OnDomReadyHeaderItem.forScript(
                "MidPointTheme.createSparkline('#" + comp.getMarkupId() + "', " + options + ", " + data + ");"));
    }

    private void initModels() {
        metricValues = new LoadableDetachableModel<>() {

            @Override
            protected List<SimulationMetricValuesType> load() {
                DashboardWidgetType widget = getModelObject();
                if (widget == null || widget.getData() == null || widget.getData().getMetricRef() == null) {
                    return Collections.emptyList();
                }

                DashboardWidgetDataType data = widget.getData();
                SimulationMetricReferenceType metricRef = data.getMetricRef();

                ObjectFilter filter = createObjectFilter(data.getCollection());
                if (filter == null) {
                    return Collections.emptyList();
                }

                ObjectQuery query = getPrismContext().queryFor(SimulationResultType.class)
                        .filter(filter).build();

                PageBase page = getPageBase();
                OperationResult result = page.getPageTask().getResult();
                List<PrismObject<SimulationResultType>> results = WebModelServiceUtils.searchObjects(SimulationResultType.class, query, result, page);

                return results.stream()
                        .map(r -> r.asObjectable().getMetric())
                        .reduce(new ArrayList<>(), (list, metric) -> {
                            list.addAll(metric);
                            return list;
                        })
                        .stream().filter(m -> metricRef.equals(m.getRef()))
                        .collect(Collectors.toList());
            }
        };

        metricDefinition = new LoadableDetachableModel<>() {

            @Override
            protected SimulationMetricDefinitionType load() {
                return null;// todo
            }
        };
    }

    private ObjectFilter createObjectFilter(CollectionRefSpecificationType collection) {
        if (collection == null) {
            return null;
        }

        SearchFilterType search;
        if (collection.getCollectionRef() != null) {
            com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType collectionRef = collection.getCollectionRef();
            PrismObject<ObjectCollectionType> obj = WebModelServiceUtils.loadObject(collectionRef, getPageBase());
            if (obj == null) {
                return null;
            }

            ObjectCollectionType objectCollection = obj.asObjectable();
            search = objectCollection.getFilter();
        } else {
            search = collection.getFilter();
        }

        if (search == null) {
            return null;
        }

        try {
            return getPageBase().getQueryConverter().createObjectFilter(SimulationResultType.class, search);
        } catch (Exception ex) {
            ex.printStackTrace();
            //todo viliam
//            LOGGER.debug("Couldn't create search filter", ex);
//            page.error("Couldn't create search filter, reason: " + ex.getMessage());
        }

        return null;
    }

    private void initLayout() {
        add(AttributeModifier.prepend("class", "d-flex flex-column border rounded bg-white"));

        Label title = new Label(ID_TITLE, () -> {
            DisplayType display = getModelObject().getDisplay();
            return display != null ? WebComponentUtil.getTranslatedPolyString(display.getLabel()) : "Some thing or other";  // todo fix
        });
        add(title);

        AjaxLink open = new AjaxLink<>(ID_OPEN) {
            @Override
            public void onClick(AjaxRequestTarget target) {
                onMoreInfoPerformed(target);
            }
        };
        open.add(new VisibleBehaviour(() -> isMoreInfoVisible()));
        add(open);

        BadgePanel trendBadge = new BadgePanel(ID_TREND_BADGE, () -> {
            Badge badge = new Badge();
            badge.setCssClass("badge badge-success trend trend-success");   // todo viliam
            badge.setIconCssClass("fa-solid fa-arrow-trend-up mr-1");
            badge.setText("+3,14%");
            return badge;
        });
        add(trendBadge);

        Label value = new Label(ID_VALUE, createValueModel());
        add(value);

        Label valueDescription = new Label(ID_VALUE_DESCRIPTION, createDescriptionModel());
        add(valueDescription);

        WebMarkupContainer iconContainer = new WebMarkupContainer(ID_ICON_CONTAINTER);
        iconContainer.add(new VisibleBehaviour(() -> metricValues.getObject().isEmpty()));
        add(iconContainer);

        CompositedIconPanel icon = new CompositedIconPanel(ID_ICON, () -> createIcon());
        iconContainer.add(icon);

        WebMarkupContainer chartContainer = new WebMarkupContainer(ID_CHART_CONTAINER);
        chartContainer.add(new VisibleBehaviour(() -> !metricValues.getObject().isEmpty()));
        chartContainer.setOutputMarkupId(true);
        add(chartContainer);
    }

    private IModel<String> createValueModel() {
        return () -> {
            List<SimulationMetricValuesType> values = metricValues.getObject();
            if (values.isEmpty()) {
                return null;
            }

            BigDecimal value = SimulationMetricValuesTypeUtil.getValue(values.get(values.size() - 1));

            return value.toString();
        };
    }

    private IModel<String> createDescriptionModel() {
        return () -> "jklo";
    }

    private boolean isMoreInfoVisible() {
        DashboardWidgetDataType data = getModelObject().getData();
        if (data == null || data.getMetricRef() == null) {
            return false;
        }

        return !metricValues.getObject().isEmpty();
    }

    protected void onMoreInfoPerformed(AjaxRequestTarget target) {

    }

    private CompositedIcon createIcon() {
        DisplayType display = metricDefinition.getObject().getDisplay();
        if (display == null) {
            return null;
        }

        // todo should we try to load MarkType -> display if it's mark metric?

        CompositedIconBuilder builder = new CompositedIconBuilder();
        builder.setBasicIcon(GuiDisplayTypeUtil.getIconCssClass(display), IconCssStyle.CENTER_STYLE)
                .appendColorHtmlValue(GuiDisplayTypeUtil.getIconColor(display));

        return builder.build();
    }
}
