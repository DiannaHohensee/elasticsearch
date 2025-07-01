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
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.ClusterInfo;
import org.elasticsearch.cluster.ClusterInfoService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.routing.RerouteService;
import org.elasticsearch.common.Priority;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.gateway.GatewayService;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Monitors the node-level write loads across the cluster and initiates (coming soon) a rebalancing round (via
 * {@link RerouteService#reroute}) whenever a node crosses the node-level write load thresholds. Also maintains
 * node-level write-load stats for each data node that can be supplied to callers: these stats are pushed from
 * data nodes whenever an actionable change occurs, such as crossing the utilization or queue time thresholds.
 *
 * Runs on the master node.
 *
 * TODO: implement when we have WriteLoadConstraintDecider#canRemain returning Decision#NOT_PREFERRED.
 */
public class WriteLoadConstraintMonitor {
    private static final Logger logger = LogManager.getLogger(WriteLoadConstraintMonitor.class);
    private final WriteLoadConstraintSettings writeLoadConstraintSettings;
    private final Supplier<ClusterState> clusterStateSupplier;
    private final LongSupplier currentTimeMillisSupplier;
    private final RerouteService rerouteService;

    public WriteLoadConstraintMonitor(
        Settings settings,
        ClusterSettings clusterSettings,
        LongSupplier currentTimeMillisSupplier,
        Supplier<ClusterState> clusterStateSupplier,
        RerouteService rerouteService
    ) {
        this.writeLoadConstraintSettings = new WriteLoadConstraintSettings(settings, clusterSettings);
        this.clusterStateSupplier = clusterStateSupplier;
        this.currentTimeMillisSupplier = currentTimeMillisSupplier;
        this.rerouteService = rerouteService;
    }

    /**
     * Receives a copy of the latest {@link ClusterInfo} whenever the {@link ClusterInfoService} collects it.
     */
    public void onNewInfo(ClusterInfo clusterInfo) {
        final ClusterState state = clusterStateSupplier.get();
        if (state.blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            logger.debug("skipping monitor as the cluster state is not recovered yet");
            return;
        }

        if (writeLoadConstraintSettings.getWriteLoadConstraintEnabled() == WriteLoadConstraintSettings.WriteLoadDeciderStatus.DISABLED) {
            logger.trace("skipping monitor because the write load decider is disabled");
            return;
        }

        logger.trace("processing new cluster info");

        boolean reroute = false;
        String explanation = "";
        final long currentTimeMillis = currentTimeMillisSupplier.getAsLong();

        if (reroute) {
            logger.debug("rerouting shards: [{}]", explanation);
            rerouteService.reroute("disk threshold monitor", Priority.NORMAL, ActionListener.wrap(ignored -> {
                final var reroutedClusterState = clusterStateSupplier.get();

                // NOMERGE: update any stats after the desired balance computation has run.

            }, e -> logger.debug("reroute failed", e)));
        } else {
            logger.trace("no reroute required");
        }
    }
}
