/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja;

import org.testng.annotations.Test;

/**
 * Created by Lukáš Meňhert (lukasmenhert).
 */
public class RegenerateUuidRepositoryTest {

    @Test
    public void regeneratePhaseInit() throws Exception {
        String[] input = new String[]{
                "-m", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev",
                "regenerateUuidInit", "-im", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev/regen-map-a.csv"};
        Main.main(input);
    }

    @Test
    public void regeneratePhaseInitWithoutInputMap() throws Exception {
        String[] input = new String[]{
                "-m", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev",
                "regenerateUuidInit"};
        Main.main(input);
    }

    @Test
    public void regeneratePhase() throws Exception {
        String[] input = new String[]{
                "-m", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev",
                "regenerateUuid",
                "-im", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev/regen-map-a.csv",
                "-fr", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev/regenerate-file.xml"
        };
        Main.main(input);
    }

    @Test
    public void playground() throws Exception {
        /*String[] input = new String[]{
                "-m", "/Users/lukasmenhert/Inalogy/Projects/midpoint-docker/compose/midpoint-4.0.4/midpoint-home-dev",
                "export", "-O", "/Users/lukasmenhert/export-this.zip", "-z"};
        */

        String[] input = new String[]{"-h"};

        Main.main(input);
    }
}
