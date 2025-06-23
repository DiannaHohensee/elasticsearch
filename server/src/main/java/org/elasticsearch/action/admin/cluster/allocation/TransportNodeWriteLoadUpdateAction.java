/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.action.admin.cluster.allocation;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.injection.guice.Inject;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 * A data node will send a {@link NodeWriteLoadUpdateRequest} to the master node to update the
 * {@link org.elasticsearch.cluster.routing.allocation.WriteLoadConstraintMonitor} whenever
 * a significant node-level write load change occurs.
 */
public class TransportNodeWriteLoadUpdateAction extends TransportMasterNodeAction<NodeWriteLoadUpdateRequest, NodeWriteLoadUpdateResponse> {
    public static final ActionType<NodeWriteLoadUpdateResponse> TYPE = new ActionType<>(
        "cluster:admin/cluster/allocation/node_write_load_update"
    );

    private final ThreadPool threadPool;

    @Inject
    public TransportNodeWriteLoadUpdateAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters
    ) {
        super(
            TYPE.name(),
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            NodeWriteLoadUpdateRequest::new,
            NodeWriteLoadUpdateResponse::new,
            EsExecutors.DIRECT_EXECUTOR_SERVICE
        );
        this.threadPool = threadPool;
    }

    @Override
    protected void masterOperation(
        Task task,
        NodeWriteLoadUpdateRequest request,
        ClusterState state,
        ActionListener<NodeWriteLoadUpdateResponse> listener
    ) throws Exception {

    }

    @Override
    protected ClusterBlockException checkBlock(NodeWriteLoadUpdateRequest request, ClusterState state) {
        return null;
    }
}
