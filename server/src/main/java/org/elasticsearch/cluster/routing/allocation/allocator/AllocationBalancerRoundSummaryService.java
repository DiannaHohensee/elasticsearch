/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation.allocator;

import org.elasticsearch.threadpool.ThreadPool;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Receives balancing round reason and results, summarizing the impact of each balancing round and what cluster event initiated the shard
 * rebalancing. This will provide information with which to do a cost-benefit analysis of the work that the balancer does.
 *
 * TODO (Dianna): inject a ThreadPool into this service, so I can get timestamps and schedule processing asynchronously.
 */
public class AllocationBalancerRoundSummaryService {

    /**
     * The cluster event that initiated a rebalancing round. This will tell us why the balancer is doing some amount of rebalancing work.
     */
    enum ClusterRebalancingEvent {
        // ComputationFinishReason.STOP_EARLY is an event -- maybe can track it back to the cause.
        RerouteCommand,
        IndexCreation,
        IndexDeletion,
        NodeShutdownAndRemoval,
        NewNodeAdded
    }

    /**
     * Tracks and summarizes the more granular reasons why shards are moved between nodes.
     *
     * @param numShardMoves total number of shard moves in the rebalancing round
     * @param numAllocationDeciderForcedShardMoves total number of shards that must be moved because they violate an AllocationDecider rule
     * @param numRebalancingShardMoves total number of shards moved to improve cluster balance and are not otherwise required to move
     * @param numShutdownForcedShardMoves total number of shards that must move off of a node because it is shutting down
     * @param numStuckShards total number of shards violating an AllocationDecider on their current node and on every other cluster node
     */
    record ClusterShardMovements(
        long numShardMoves,
        long numAllocationDeciderForcedShardMoves,
        long numRebalancingShardMoves,
        long numShutdownForcedShardMoves,
        long numStuckShards
    ) {
        @Override
        public String toString() {
            return "ClusterShardMovements{"
                + "numShardMoves="
                + numShardMoves
                + ", numAllocationDeciderForcedShardMoves="
                + numAllocationDeciderForcedShardMoves
                + ", numRebalancingShardMoves="
                + numRebalancingShardMoves
                + ", numShutdownForcedShardMoves="
                + numShutdownForcedShardMoves
                + ", numStuckShards="
                + numStuckShards
                + '}';
        }
    };

    /**
     * Describes how each node was improved by a balancing round, and how much work that node did to achieve the shard rebalancing. General
     * cost-benefit information on the node-level.
     *
     * @param nodeWeightBeforeRebalancing
     * @param nodeWeightAfterRebalancing
     * @param dataMovedToNodeInMB
     * @param dataMovedAwayFromNodeInMB
     */
    record IndividualNodeRebalancingChangeStats(
        long nodeWeightBeforeRebalancing,
        long nodeWeightAfterRebalancing,
        long dataMovedToNodeInMB,
        long dataMovedAwayFromNodeInMB
    ) {
        @Override
        public String toString() {
            return "IndividualNodeRebalancingChangeStats{"
                + "nodeWeightBeforeRebalancing="
                + nodeWeightBeforeRebalancing
                + ", nodeWeightAfterRebalancing="
                + nodeWeightAfterRebalancing
                + ", dataMovedToNodeInMB="
                + dataMovedToNodeInMB
                + ", dataMovedAwayFromNodeInMB="
                + dataMovedAwayFromNodeInMB
                + '}';
        }
    };

    /**
     * Summarizes the impact to the cluster as a result of a rebalancing round.
     *
     * @param eventStartTimestamp Time at which the desired balance calculation began due to a cluster event.
     * @param computationEndTimestamp Time at which the new desired balance calculation was finished.
     * @param event Reports what provoked the rebalancing round. The rebalancer only runs when requested, not on a periodic basis.
     * @param computationFinishReason
     * @param shardMovements Lists the total number of shard moves, and breaks down the total into number shards moved by category,
     *                       like node shutdown
     * @param nodeChanges A Map of node name to {@link IndividualNodeRebalancingChangeStats} to describe what each node gained and how much
     *                    work each node performed for the balancing round.
     */
    record BalancingRoundSummary(
        Timestamp eventStartTimestamp,
        Timestamp computationEndTimestamp,
        ClusterRebalancingEvent event,
        DesiredBalance.ComputationFinishReason computationFinishReason,
        ClusterShardMovements shardMovements,
        Map<String, IndividualNodeRebalancingChangeStats> nodeChanges
    ) {
        @Override
        public String toString() {
            return "BalancingRoundSummary{"
                + "ClusterRebalancingEvent="
                + event
                + ", ClusterShardMovements="
                + shardMovements
                + ", NodeChangeStats={"
                + nodeChanges
                + "}"
                + '}';
        }
    };

    /**
     * Holds combined {@link BalancingRoundSummary} results. Essentially holds a list of the events and the summed up the changes across all
     * those events.
     *
     * @param eventsStartTime The earliest start time of all the combined balancing rounds.
     * @param eventsEndTime The latest end time of all the combined balancing rounds.
     * @param events TODO (Dianna): map by timestamp doesn't make sense...
     * @param shardMovements The sum of all shard movements across all combined balancing rounds.
     * @param nodeChanges The total change stats per node in the cluster from the earliest balancing round to the latest one.
     */
    record CombinedClusterBalancingRoundSummary(
        Timestamp eventsStartTime,
        Timestamp eventsEndTime,
        HashMap<Timestamp, ClusterRebalancingEvent> events,
        ClusterShardMovements shardMovements,
        Map<String, IndividualNodeRebalancingChangeStats> nodeChanges
    ) {};

    /** Value to return if no balancing rounds have occurred in the requested time period. */
    private final CombinedClusterBalancingRoundSummary EMPTY_RESULTS = new CombinedClusterBalancingRoundSummary(
        new Timestamp(0),
        new Timestamp(0),
        new HashMap<>(),
        new ClusterShardMovements(0,0,0,0,0),
        new HashMap<>()
    );

    private final ThreadPool threadPool;

    /**
     * A concurrency-safe list of balancing round summaries. Balancer rounds are run and added here serially, so the deque will naturally
     * progress from newer to older results.
     */
    private ConcurrentLinkedDeque<BalancingRoundSummary> summaries = new ConcurrentLinkedDeque<>();

    public AllocationBalancerRoundSummaryService(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }
    private CombinedClusterBalancingRoundSummary combinedSummaries() {
        // TOOD (Dianna): take this.summaries and merge/sum the stats
        return EMPTY_RESULTS;
    }

    public void summarizeAndSaveBalancerRoundResults() {

    }

    public void addBalancerRound() {
        // TODO (Dianna): formats into BalancingRoundSummary and adds it to the list
    }

    public Map<Timestamp, BalancingRoundSummary> getBalancerRoundSummary(boolean singleCombinedSummary) {
        var map = new HashMap<Timestamp, BalancingRoundSummary>();
        // TODO (Dianna): implement
        return map;
    }

}
