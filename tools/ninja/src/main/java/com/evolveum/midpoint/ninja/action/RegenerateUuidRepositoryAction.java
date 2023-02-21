/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action;

import com.evolveum.midpoint.ninja.action.worker.ProgressReporterWorker;
import com.evolveum.midpoint.ninja.action.worker.RegenerateUuidInitProducerWorker;
import com.evolveum.midpoint.ninja.impl.NinjaException;
import com.evolveum.midpoint.ninja.opts.RegenerateUuidOptions;
import com.evolveum.midpoint.ninja.util.Log;
import com.evolveum.midpoint.ninja.util.NinjaUtils;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ninja action realizing "regenerateUuid" command.
 */
public class RegenerateUuidRepositoryAction extends RepositoryAction<RegenerateUuidOptions> {

    private HashMap<String, String> oidToUuid = new HashMap<>();
    private File original;
    private File modified; //  regenerated-.*

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

        ExecutorService executor = Executors.newFixedThreadPool( 1);
        executor.execute(new ProgressReporterWorker(context, options, null, operation));

        // Reads input map and checks for input files existence
        init();

        // Generate new regenerated file based in input file-to-regenerate
        try (
            BufferedWriter writer = new BufferedWriter(new FileWriter(modified, false));
            BufferedReader reader = new BufferedReader(new FileReader(original, context.getCharset()));
        ) {
            String line = reader.readLine();

            while (line != null) {
                writer.write(substituteOids(line, operation));
                writer.newLine();
                writer.flush();
                // read next line
                line = reader.readLine();
            }
        }

        operation.finish();

        executor.shutdown();
        executor.awaitTermination(NinjaUtils.WAIT_FOR_EXECUTOR_FINISH, TimeUnit.DAYS);

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

    private String substituteOids(String line, OperationStatus operationStatus) {
        // As a word ( oid )
        // Double quoted ("oid")
        // Single quoted ('oid')
        // Value of xml element (<(/w)/s.*>oid<///w>)
        String result = line;

        for (Map.Entry<String, String> entry : oidToUuid.entrySet()) {
            String oid = entry.getKey();
            String uuid = entry.getValue();

            String replacement = "$1" + uuid + "$3";
            Pattern[] patterns = new Pattern[]{
                    Pattern.compile("(^|\\s)(" + oid + ")(\\s|$)"),
                    Pattern.compile("(')(" + oid + ")(')"),
                    Pattern.compile("(\")(" + oid + ")(\")"),
                    Pattern.compile("(>)(" + oid + ")(</)")
            };

            for(int i = 0; i < patterns.length; i++) {
                Matcher matcher = patterns[i].matcher(result);
                result = matcher.replaceAll(replacement);
                matcher.reset();

                while (matcher.find()) {
                    operationStatus.incrementTotal();
                }
            }
        }

        return result;
    }
}
