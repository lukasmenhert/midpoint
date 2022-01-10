/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.configuration.system;

import com.evolveum.midpoint.gui.impl.page.admin.configuration.component.GlobalPolicyRuleTabPanel;
import com.evolveum.midpoint.authentication.api.util.AuthConstants;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.authentication.api.authorization.AuthorizationAction;
import com.evolveum.midpoint.authentication.api.authorization.PageDescriptor;
import com.evolveum.midpoint.authentication.api.authorization.Url;
import com.evolveum.midpoint.web.model.PrismContainerWrapperModel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.GlobalPolicyRuleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;

import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;

import java.util.ArrayList;
import java.util.List;

@PageDescriptor(
        urls = {
                @Url(mountUrl = "/admin/config/system/globalPolicyRule", matchUrlForSecurity = "/admin/config/system/globalPolicyRule"),
        },
        action = {
                @AuthorizationAction(actionUri = AuthConstants.AUTH_CONFIGURATION_ALL,
                        label = AuthConstants.AUTH_CONFIGURATION_ALL_LABEL,
                        description = AuthConstants.AUTH_CONFIGURATION_ALL_DESCRIPTION),
                @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_CONFIGURATION_SYSTEM_CONFIG_URL,
                        label = "PageSystemConfiguration.auth.configSystemConfiguration.label",
                        description = "PageSystemConfiguration.auth.configSystemConfiguration.description")
        })
public class PageGlobalPolicyRule extends PageAbstractSystemConfiguration {


    @Override
    protected List<ITab> createTabs() {
        List<ITab> tabs = new ArrayList<>();
        tabs.add(new AbstractTab(createStringResource("pageSystemConfiguration.globalPolicyRule.title")) {

            private static final long serialVersionUID = 1L;

            @Override
            public WebMarkupContainer getPanel(String panelId) {
                PrismContainerWrapperModel<SystemConfigurationType, GlobalPolicyRuleType> model = createModel(getObjectModel(),
                        SystemConfigurationType.F_GLOBAL_POLICY_RULE);
                return new GlobalPolicyRuleTabPanel<>(panelId, model);
            }
        });
        return tabs;
    }
}
