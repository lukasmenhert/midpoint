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
@Parameters(resourceBundle = "messages")
public class BaseRegenerateUuidOptions {

    public static final String P_INPUT_MAP = "-im";
    public static final String P_INPUT_MAP_LONG = "--input-map";

    @Parameter(names = { P_INPUT_MAP, P_INPUT_MAP_LONG }, descriptionKey = "baseRegenerateUuid.inputMap")
    private File inputMap;

    public File getInputMap() {
        return inputMap;
    }
}
