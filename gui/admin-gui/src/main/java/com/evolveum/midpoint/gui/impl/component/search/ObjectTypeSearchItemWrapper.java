/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.component.search;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.schema.expression.VariablesMap;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.web.component.search.SearchValue;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectTypeSearchItemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchBoxModeType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

public class ObjectTypeSearchItemWrapper extends AbstractSearchItemWrapper<QName> {

    private QName oldType;
    private boolean typeChanged;
    private boolean allowAllTypesSearch;

    private List<QName> supportedTypeList = new ArrayList<>();

    private QName defaultObjectType;
    public ObjectTypeSearchItemWrapper(ObjectTypeSearchItemConfigurationType config) {
        this(config.getSupportedTypes(), config.getDefaultValue(), false);
    }

    public ObjectTypeSearchItemWrapper(ObjectTypeSearchItemConfigurationType config, boolean allowAllTypesSearch) {
        this(config.getSupportedTypes(), config.getDefaultValue(), allowAllTypesSearch);
    }

    public ObjectTypeSearchItemWrapper(List<QName> supportedTypeList, QName defaultObjectType, boolean allowAllTypesSearch) {
        this.supportedTypeList = supportedTypeList;
        this.defaultObjectType = defaultObjectType;
    }

    public Class<ObjectTypeSearchItemPanel> getSearchItemPanelClass() {
        return ObjectTypeSearchItemPanel.class;
    }

    public List<QName> getAvailableValues() {
        return getSupportedTypeList();
    }

    public boolean isTypeChanged() {
        return typeChanged;
    }

    public void setTypeChanged(boolean typeChanged) {
        this.typeChanged = typeChanged;
    }

    @Override
    public DisplayableValue<QName> getDefaultValue() {
        return new SearchValue(getDefaultObjectType());
    }

    public List<QName> getSupportedTypeList() {
        return supportedTypeList;
    }

    public void setSupportedTypeList(List<QName> supportedTypeList) {
        this.supportedTypeList = supportedTypeList;
    }

    public QName getDefaultObjectType() {
        return defaultObjectType;
    }

    public void setDefaultObjectType(QName defaultObjectType) {
        this.defaultObjectType = defaultObjectType;
    }

    @Override
    public String getName() {
//        if (config != null && config.getDisplay() != null && config.getDisplay().getLabel() != null){
//            return WebComponentUtil.getTranslatedPolyString(config.getDisplay().getLabel());
//        }
        return PageBase.createStringResourceStatic("ContainerTypeSearchItem.name").getString();
    }

    @Override
    public String getHelp() {
//        if (config != null && config.getDisplay() != null && config.getDisplay().getHelp() != null){
//            return WebComponentUtil.getTranslatedPolyString(config.getDisplay().getHelp());
//        }
        return "";
    }

    @Override
    public boolean canRemoveSearchItem() {
        return false;
    }

    @Override
    public String getTitle() {
        return ""; //todo
    }

    @Override
    public boolean isApplyFilter(SearchBoxModeType searchBoxMode) {
        return !SearchBoxModeType.OID.equals(searchBoxMode);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public ObjectFilter createFilter(Class type, PageBase pageBase, VariablesMap variables) {
        return PrismContext.get().queryFor(type)
                .buildFilter();
    }

    public boolean isAllowAllTypesSearch() {
        return allowAllTypesSearch;
    }

    public void setAllowAllTypesSearch(boolean allowAllTypesSearch) {
        this.allowAllTypesSearch = allowAllTypesSearch;
    }
}