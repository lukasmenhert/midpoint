/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.ninja.action.worker;

import com.evolveum.midpoint.ninja.impl.NinjaContext;
import com.evolveum.midpoint.ninja.impl.NinjaException;
import com.evolveum.midpoint.ninja.opts.ExportOptions;
import com.evolveum.midpoint.ninja.opts.RegenerateUuidInitOptions;
import com.evolveum.midpoint.ninja.util.Log;
import com.evolveum.midpoint.ninja.util.NinjaUtils;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptionsBuilder;
import com.evolveum.midpoint.schema.ResultHandler;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.util.exception.SchemaException;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Created by Lukáš Meňhert (lukasmenhert).
 */
public class RegenerateUuidInitProducerWorker extends BaseWorker<RegenerateUuidInitOptions, PrismObject> {

    private ObjectTypes type;

    public RegenerateUuidInitProducerWorker(NinjaContext context, RegenerateUuidInitOptions options, BlockingQueue<PrismObject> queue,
                                            OperationStatus operation, List<RegenerateUuidInitProducerWorker> producers,
                                            ObjectTypes type) {
        super(context, options, queue, operation, producers);

        this.type = type;
    }

    @Override
    public void run() {
        Log log = context.getLog();

        try {
            GetOperationOptionsBuilder optionsBuilder = context.getSchemaHelper().getOperationOptionsBuilder();
            optionsBuilder = NinjaUtils.addIncludeOptionsForExport(optionsBuilder, type.getClassDefinition());

            ResultHandler handler = (object, parentResult) -> {
                try {
                    queue.put(object);
                } catch (InterruptedException ex) {
                    log.error("Couldn't queue object {}, reason: {}", ex, object, ex.getMessage());
                }
                return true;
            };

            RepositoryService repository = context.getRepository();
            repository.searchObjectsIterative(type.getClassDefinition(), null, handler, optionsBuilder.build(), true, operation.getResult());
        } catch (SchemaException ex) {
            log.error("Unexpected exception, reason: {}", ex, ex.getMessage());
        } catch (NinjaException ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            markDone();

            if (isWorkersDone()) {
                if (!operation.isFinished()) {
                    operation.producerFinish();
                }
            }
        }
    }
}
