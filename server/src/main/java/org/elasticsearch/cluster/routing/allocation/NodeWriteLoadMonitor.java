/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.internal.Client;
import org.elasticsearch.cluster.NodeWriteLoad;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.util.concurrent.TaskExecutionTimeTrackingEsThreadPoolExecutor;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.concurrent.ExecutorService;

public class NodeWriteLoadMonitor {
    private static final Logger logger = LogManager.getLogger(NodeWriteLoadMonitor.class);

    private final Client client;
    private final ThreadPool threadPool;
    private final ClusterService clusterService;

    public NodeWriteLoadMonitor(Client client, ThreadPool threadPool, ClusterService clusterService) {
        this.client = client;
        this.threadPool = threadPool;
        this.clusterService = clusterService;
    }

    private void collectionWriteLoadAndMaybePublish() {
        ExecutorService executor = threadPool.executor(ThreadPool.Names.WRITE);
        assert executor instanceof TaskExecutionTimeTrackingEsThreadPoolExecutor
            : "WRITE threadpool should have an executor that exposes execution metrics, but is of type " + executor.getClass();
        var executionTrackingExecutor = (TaskExecutionTimeTrackingEsThreadPoolExecutor) executor;

        // TODO need to check settings for thresholds before forwarding.
        publishWriteLoadToMasterNode(
            new NodeWriteLoad(
                clusterService.localNode().getId(),
                executionTrackingExecutor.getMaximumPoolSize(),
                (int) executionTrackingExecutor.getPercentPoolUtilizationEWMA(),
                (long) executionTrackingExecutor.getQueuedTaskLatencyMillisEWMA()
            )
        );
    }

    private void publishWriteLoadToMasterNode(NodeWriteLoad nodeWriteLoad) {
        // client.execute(
        // TransportNodeWriteLoadUpdateAction.TYPE,
        // new NodeWriteLoadUpdateRequest(nodeWriteLoad),
        // // fork to snapshot meta since building the response is expensive for large snapshots
        // new RefCountAwareThreadedActionListener<>(
        // threadPool.executor(ThreadPool.Names.SNAPSHOT_META),
        // listener.delegateFailureAndWrap(
        // (l, nodeSnapshotStatuses) -> buildResponse(
        // snapshotsInProgress,
        // request,
        // currentSnapshots,
        // nodeSnapshotStatuses,
        // state.getMinTransportVersion(),
        // cancellableTask,
        // l
        // )
        // )
        // )
        // );
    }
}
