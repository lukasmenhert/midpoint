package com.evolveum.midpoint.provisioning.impl.shadowcache;

import com.evolveum.midpoint.provisioning.impl.ShadowCaretaker;
import com.evolveum.midpoint.provisioning.impl.shadowmanager.ShadowManager;
import com.evolveum.midpoint.util.annotation.Experimental;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Beans useful for non-Spring components within this package.
 */
@Experimental
@Component
class LocalBeans {

    @Autowired AccessChecker accessChecker;
    @Autowired AdoptionHelper adoptionHelper;
    @Autowired CommonHelper commonHelper;
    @Autowired ShadowCache shadowCache;
    @Autowired ShadowCaretaker shadowCaretaker;
    @Autowired ShadowManager shadowManager;

}
