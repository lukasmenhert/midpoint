/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action;

import com.evolveum.midpoint.ninja.action.worker.ExportConsumerWorker;
import com.evolveum.midpoint.ninja.action.worker.ProgressReporterWorker;
import com.evolveum.midpoint.ninja.action.worker.SearchProducerWorker;
import com.evolveum.midpoint.ninja.impl.NinjaException;
import com.evolveum.midpoint.ninja.opts.ConnectionOptions;
import com.evolveum.midpoint.ninja.opts.ExportOptions;
import com.evolveum.midpoint.ninja.opts.RegenerateUuidOptions;
import com.evolveum.midpoint.ninja.util.Log;
import com.evolveum.midpoint.ninja.util.NinjaUtils;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.InOidFilter;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.QueryFactory;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ninja action realizing "regenerateUuid" command.
 */
public class RegenerateUuidRepositoryAction extends RepositoryAction<RegenerateUuidOptions> {

    private HashMap<String, String> oidToUuid = new HashMap<>();
    private File original;
    private File modified; //  +.regenerated

    private static final char INPUT_MAP_DELIMITER = ';';

    protected String getOperationShortName() { return "regenerateUuid"; };

    protected String getOperationName() {
        return this.getClass().getName() + "." + getOperationShortName();
    }

    @Override
    public void execute() throws Exception {
        OperationResult result = new OperationResult(getOperationName());
        OperationStatus operation = new OperationStatus(context, result);

        log.info("Starting " + getOperationShortName());
        operation.start();

        // Reads input map and checks for file existence;
        init();

        // Generate new regenerated file based in input file-to-regenerate
        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(modified, false));
            BufferedReader reader = new BufferedReader(new FileReader(original, context.getCharset()));
        ) {
            String line = reader.readLine();

            while (line != null) {
                writer.write(substituteOids(line));
                writer.newLine();
                writer.flush();
                // read next line
                line = reader.readLine();
            }
        }
        // HERE^

        operation.finish();

        handleResultOnFinish(operation, "Finished " + getOperationShortName());
    }

    protected void init() {
        Log log = context.getLog();
        File inputMap = options.getInputMap();
        original = options.getFileToRegenerate();

        if (inputMap == null) {
            throw new IllegalStateException("Option " + RegenerateUuidOptions.P_INPUT_MAP + " must be specified.");
        } else if (!inputMap.exists()) {
            throw new NinjaException("File '" + inputMap.getPath() + "' doesn't exist.");
        }

        if (original == null) {
            throw new IllegalStateException("Option " + RegenerateUuidOptions.P_FILE_TO_REGENERATE
                    + " must be specified.");
        } else if (!original.exists()) {
            throw new NinjaException("File '" + original.getPath() + "' doesn't exist.");
        }

        try (
            FileReader fr = new FileReader(inputMap, context.getCharset());
            BufferedReader reader = new BufferedReader(fr);
        ) {
            String line = reader.readLine();

            while (line != null) {
                String[] columns = line.split("" + INPUT_MAP_DELIMITER);
                if (columns.length == 2) {
                    oidToUuid.put(columns[0].trim(), columns[1].trim());
                } else {
                    log.info("Line '{}' of input map doesn't contain two values separated by '{}', skipping.",
                            line, INPUT_MAP_DELIMITER);
                }
                // read next line
                line = reader.readLine();
            }
        } catch (IOException e) {
            throw new NinjaException("There was error while reading '" + inputMap.getPath() + "' file.", e);
        }

        modified = new File(original.getParent() + "/regenerated-" + original.getName());
    }

    private String substituteOids(String line) {
        // Celé slovo (mezery)
        // Dvojité uvozovky (““)
        // Jednoduché uvozovky ('')
        // V tagu >OID< (např. <value>OID</value>)

        for (Map.Entry<String, String> entry : oidToUuid.entrySet()) {
            String oid = entry.getKey();
            String uuid = entry.getValue();

            Pattern wordRegex = Pattern.compile("");
            Pattern singleQuotedRegex = Pattern.compile("");
            Pattern doubleQuotedRegex = Pattern.compile("");
            Pattern elementValueRegex = Pattern.compile("");

            // Replace

        }

        return line;
    }
}
