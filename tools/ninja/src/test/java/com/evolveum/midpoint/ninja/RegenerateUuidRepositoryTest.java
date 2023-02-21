/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja;

import com.evolveum.midpoint.ninja.action.worker.RegenerateUuidInitConsumerWorker;
import com.evolveum.midpoint.ninja.opts.RegenerateUuidOptions;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Created by Lukáš Meňhert (lukasmenhert).
 */
public class RegenerateUuidRepositoryTest {

    private static final String MIDPOINT_HOME = "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/" +
            "midpoint-4.0.4/midpoint-home-dev";

    @Test
    public void regeneratePhaseInit() {
        String[] input = new String[]{
                "-m", MIDPOINT_HOME,
                "regenerateUuidInit",
                "-im", MIDPOINT_HOME + "/regen-mapa.csv"
        };
        Main.main(input);
    }

    @Test
    public void regeneratePhaseInitWithoutInputMap() {
        String[] input = new String[]{
                "-m", MIDPOINT_HOME,
                "regenerateUuidInit"};
        Main.main(input);

        AssertJUnit.assertTrue(new File("./" + RegenerateUuidInitConsumerWorker.DEFAULT_INPUT_MAP_FILENAME).exists());
    }

    @Test
    public void regeneratePhase() {
        String[] input = new String[]{
                "-m", MIDPOINT_HOME,
                "regenerateUuid",
                "-im", MIDPOINT_HOME + "/regen-map-a.csv",
                "-fr", MIDPOINT_HOME + "/kdm_export.xml"
        };
        Main.main(input);
    }

    //@Test
    @Test(expectedExceptions = { IllegalStateException.class })
    public void regeneratePhaseMandatoryInputMapFileException() {
        String[] input = new String[]{
                "-m", MIDPOINT_HOME,
                "regenerateUuid",
                "-fr", MIDPOINT_HOME + "/regenerate-file.xml"
        };
        Main.main(input);
        //throw new IllegalStateException("Exce");
    }

    @Test
    public void playground() throws Exception {
        /*String[] input = new String[]{
                "-m", MIDPOINT_HOME,
                "export", "-O", "/Users/lukasmenhert/export-this.zip", "-z"};
        */

        String[] input = new String[]{"-h"};
        Main.main(input);
    }
}
