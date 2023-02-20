/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action.worker;

import com.evolveum.midpoint.ninja.impl.NinjaContext;
import com.evolveum.midpoint.ninja.impl.NinjaException;
import com.evolveum.midpoint.ninja.opts.RegenerateUuidInitOptions;
import com.evolveum.midpoint.ninja.util.Log;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class RegenerateUuidInitConsumerWorker extends BaseWorker<RegenerateUuidInitOptions, PrismObject> {

    private File inputMap;
    private boolean trailsEmptyLine = true;
    private static final String DEFAULT_INPUT_MAP_FILENAME = "input-map.csv";
    private static final char INPUT_MAP_DELIMITER = ';';

    private HashMap<String, String> oidToUuid = new HashMap<>();
    private HashMap<String, String> uuidToOid = new HashMap<>();

    public RegenerateUuidInitConsumerWorker(NinjaContext context, RegenerateUuidInitOptions options,
                                            BlockingQueue<PrismObject> queue,
                                            OperationStatus operation) {
        super(context, options, queue, operation);
    }

    @Override
    public void run() {
        Log log = context.getLog();

        init();

        try (
            FileWriter fw = new FileWriter(inputMap, true);
            BufferedWriter bw = new BufferedWriter(fw);
        ) {
            if (!trailsEmptyLine) {
                bw.newLine();
            }

            while (!shouldConsumerStop()) {
                PrismObject object = null;
                try {
                    object = queue.poll(CONSUMER_POLL_TIMEOUT, TimeUnit.SECONDS);
                    if (object == null) {
                        continue;
                    }

                    String currentOid = object.getOid();
                    // Check if oid adheres to uuid standard
                    try {
                        UUID.fromString(currentOid);
                    } catch (IllegalArgumentException e) {
                        // Check if key exists in hashMap
                        if (!oidToUuid.containsKey(currentOid)) {
                            // Regenerate oid
                            String newOid;
                            do {
                                newOid = UUID.randomUUID().toString();
                            } while (oidAlreadyExists(newOid) || uuidToOid.containsKey(newOid));

                            oidToUuid.put(currentOid, newOid);
                            uuidToOid.put(newOid, currentOid);

                            // Append to input map
                            append(bw, currentOid + INPUT_MAP_DELIMITER + newOid);
                            bw.flush();
                        }
                    }

                    operation.incrementTotal();
                } catch (Exception ex) {
                    log.error("Couldn't process object {}, reason: {}", ex, object, ex.getMessage());
                    operation.incrementError();
                }
            }
        } catch (IOException ex) {
            log.error("Unexpected exception, reason: {}", ex, ex.getMessage());
        } catch (NinjaException ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            markDone();

            if (isWorkersDone()) {
                operation.finish();
            }
        }
    }

    protected void init() {
        // Initialize map
        Log log = context.getLog();

        inputMap = options.getInputMap();
        if (inputMap == null) {
            inputMap = new File("./" + DEFAULT_INPUT_MAP_FILENAME);
        } else {
            try (Stream<String> stream = Files.lines(Paths.get(inputMap.getPath()))) {
                stream.forEach((line) -> {
                    String[] columns = line.split("" + INPUT_MAP_DELIMITER);
                    if (columns.length == 2) {
                        oidToUuid.put(columns[0].trim(), columns[1].trim());
                    } else {
                        log.info("Line '{}' doesnt contain two values separated by '{}', skipping.",
                                line, INPUT_MAP_DELIMITER);
                    }
                });
            } catch (IOException e) {
                throw new NinjaException("There was error while reading '" + inputMap.getPath() + "' file.", e);
            }
        }
    }

    private void append(BufferedWriter writer, String object) throws IOException {
        writer.write(object);
        writer.newLine();
    }

    private boolean oidAlreadyExists(String oid) {
        RepositoryService repository = context.getRepository();
        try {
            ObjectType object = repository.getObject(ObjectType.class, oid, null,
                    new OperationResult("Search by oid")).asObjectable();
        } catch (ObjectNotFoundException ex) {
            return false;
        } catch (SchemaException ex2) {
            context.getLog().error(ex2.getMessage());
        }

        return true;
    }
}
