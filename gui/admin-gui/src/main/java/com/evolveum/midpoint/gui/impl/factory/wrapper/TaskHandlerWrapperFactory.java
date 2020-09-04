/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.factory.wrapper;

import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.gui.api.factory.wrapper.WrapperContext;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableRowType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LookupTableType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringTranslationType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

@Component
public class TaskHandlerWrapperFactory extends PrismPropertyWrapperFactoryImpl<String> {

    @Override
    protected LookupTableType getPredefinedValues(PrismProperty<String> item, WrapperContext ctx) {
        PrismObject<?> prismObject = getParent(ctx);
        if (!TaskType.class.equals(prismObject.getCompileTimeClass())) {
            return super.getPredefinedValues(item, ctx);
        }

        LookupTableType parentLookup = super.getPredefinedValues(item, ctx);
        if (parentLookup != null) {
            return parentLookup;
        }

        TaskType task = (TaskType) prismObject.asObjectable();
           Collection<AssignmentType> assignmentTypes = task.getAssignment()
                .stream()
                .filter(WebComponentUtil::isArchetypeAssignment)
                .collect(Collectors.toList());

           Collection<String> handlers;
           if (assignmentTypes.isEmpty()) {
                    handlers = getTaskManager().getAllHandlerUris(true);
                } else if (assignmentTypes.size() == 1) {
                    AssignmentType archetypeAssignment = assignmentTypes.iterator().next();
                    handlers = getTaskManager().getHandlerUrisForArchetype(archetypeAssignment.getTargetRef().getOid(), true);
                } else {
                    throw new UnsupportedOperationException("More than 1 archetype, this is not supported");
                }
                LookupTableType lookupTableType = new LookupTableType(getPrismContext());

                handlers.forEach(handler -> {
                    LookupTableRowType row = new LookupTableRowType(getPrismContext());
                    row.setKey(handler);
                    handler = normalizeHandler(handler);
                    PolyStringType handlerLabel = new PolyStringType(handler);
                    PolyStringTranslationType translation = new PolyStringTranslationType();
                    translation.setKey(handler);
                    handlerLabel.setTranslation(translation);
                    row.setLabel(handlerLabel);
                    lookupTableType.getRow().add(row);
                });

                return lookupTableType;
    }

    private PrismObject<?> getParent(WrapperContext ctx) {
        return ctx.getObject();
    }

    private String normalizeHandler(String handler) {
        handler = StringUtils.remove(handler, "-3");
        handler = StringUtils.removeStart(handler, "http://midpoint.evolveum.com/xml/ns/public/").replace("-", "/").replace("#", "/");
        String[] split = handler.split("/");
        handler = "TaskHandlerSelector." + StringUtils.join(split, ".");
        return handler;
    }


    @Override
    public int getOrder() {
        return super.getOrder() - 10;
    }

    @Override
    public boolean match(ItemDefinition<?> def) {
        return TaskType.F_HANDLER_URI.equivalent(def.getItemName());
    }
}