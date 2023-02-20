/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.opts;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.io.File;

/**
 * Created by Lukáš Meňhert (lukasmenhert).
 */
@Parameters(resourceBundle = "messages", commandDescriptionKey = "regenerateUuidInit")
public class RegenerateUuidInitOptions extends BaseRegenerateUuidOptions {

    public static final String P_MULTI_THREAD = "-l";
    public static final String P_MULTI_THREAD_LONG = "--multi-thread";

    @Parameter(names = {P_MULTI_THREAD, P_MULTI_THREAD_LONG}, descriptionKey = "baseImportExport.multiThread")

    private int multiThread = 1;

    public int getMultiThread() {
        return multiThread;
    }
}
