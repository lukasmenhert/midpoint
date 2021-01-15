/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.api.prism.wrapper;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.gui.impl.prism.wrapper.PrismReferenceValueWrapperImpl;
import com.evolveum.midpoint.prism.PrismReference;
import com.evolveum.midpoint.prism.PrismReferenceDefinition;
import com.evolveum.midpoint.prism.Referencable;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.web.component.search.Search;
import com.evolveum.midpoint.web.component.search.SearchItem;
import com.evolveum.midpoint.web.component.search.SpecialSearchItem;

/**
 * @author katka
 */
public interface PrismReferenceWrapper<R extends Referencable> extends ItemWrapper<PrismReference, PrismReferenceValueWrapperImpl<R>>, PrismReferenceDefinition {

    ObjectFilter getFilter();
    void setFilter(ObjectFilter filter);

    List<QName> getTargetTypes();

    Set<Function<Search, SearchItem>> getSpecialSearchItemFunctions();
    void setSpecialSearchItemFunctions(Set<Function<Search, SearchItem>> specialItems);
}
