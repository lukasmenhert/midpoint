/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.opts;

import java.io.File;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
/**
 * Created by Lukáš Meňhert (lukasmenhert).
 */
@Parameters(resourceBundle = "messages", commandDescriptionKey = "regenerateUuid")
public class RegenerateUuidOptions extends BaseRegenerateUuidOptions {

    public static final String P_FILE_TO_REGENERATE = "-fr";
    public static final String P_FILE_TO_REGENERATE_LONG = "--file-to-regenerate";

    @Parameter(names = { P_FILE_TO_REGENERATE, P_FILE_TO_REGENERATE_LONG }, descriptionKey = "regenerateUuid.fileToRegenerate")
    private File fileToRegenerate;

    public File getFileToRegenerate() {
        return fileToRegenerate;
    }
}
