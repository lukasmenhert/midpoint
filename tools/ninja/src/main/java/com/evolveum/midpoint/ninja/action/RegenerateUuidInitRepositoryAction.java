/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action;

import com.evolveum.midpoint.ninja.action.worker.ProgressReporterWorker;
import com.evolveum.midpoint.ninja.action.worker.RegenerateUuidInitConsumerWorker;
import com.evolveum.midpoint.ninja.action.worker.RegenerateUuidInitProducerWorker;
import com.evolveum.midpoint.ninja.action.worker.SearchProducerWorker;
import com.evolveum.midpoint.ninja.opts.RegenerateUuidInitOptions;
import com.evolveum.midpoint.ninja.util.NinjaUtils;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.QueryFactory;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Ninja action realizing "regenerateUuid" command.
 */
public class RegenerateUuidInitRepositoryAction extends RepositoryAction<RegenerateUuidInitOptions> {

    private static final int QUEUE_CAPACITY_PER_THREAD = 100;
    private static final long CONSUMERS_WAIT_FOR_START = 2000L;

    protected String getOperationShortName() { return "regenerateUuidInit"; };

    protected String getOperationName() {
        return this.getClass().getName() + "." + getOperationShortName();
    }

    @Override
    public void execute() throws Exception {
        OperationResult result = new OperationResult(getOperationName());
        OperationStatus operation = new OperationStatus(context, result);

        // "+ 2" will be used for consumer and progress reporter
        ExecutorService executor = Executors.newFixedThreadPool(options.getMultiThread() + 2);

        BlockingQueue<PrismObject> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY_PER_THREAD * options.getMultiThread());

        List<RegenerateUuidInitProducerWorker> producers = createProducers(queue, operation);

        log.info("Starting " + getOperationShortName());
        operation.start();

        // execute as many producers as there are threads for them
        for (int i = 0; i < producers.size() && i < options.getMultiThread(); i++) {
            executor.execute(producers.get(i));
        }

        Thread.sleep(CONSUMERS_WAIT_FOR_START);

        executor.execute(new ProgressReporterWorker(context, options, queue, operation));

        Runnable consumer = createConsumer(queue, operation);
        executor.execute(consumer);

        // execute rest of the producers
        for (int i = options.getMultiThread(); i < producers.size(); i++) {
            executor.execute(producers.get(i));
        }

        executor.shutdown();
        executor.awaitTermination(NinjaUtils.WAIT_FOR_EXECUTOR_FINISH, TimeUnit.DAYS);

        handleResultOnFinish(operation, "Finished " + getOperationShortName());
    }

    private List<RegenerateUuidInitProducerWorker> createProducers(BlockingQueue<PrismObject> queue, OperationStatus operation)
            throws SchemaException, IOException {

        QueryFactory queryFactory = context.getPrismContext().queryFactory();
        List<RegenerateUuidInitProducerWorker> producers = new ArrayList<>();

        // Empty set > all types
        List<ObjectTypes> types = NinjaUtils.getTypes(Collections.emptySet());
        for (ObjectTypes type : types) {
            if (ObjectTypes.SHADOW.equals(type)) {
                // Shadow oid's can be skipped, because they are generated automatically
                continue;
            }

            producers.add(new RegenerateUuidInitProducerWorker(context, options, queue, operation, producers, type));
        }

        return producers;
    }

    protected Runnable createConsumer(BlockingQueue<PrismObject> queue, OperationStatus operation) {
        return new RegenerateUuidInitConsumerWorker(context, options, queue, operation);
    }
}
