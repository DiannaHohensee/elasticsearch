/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation.allocator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the lifecycle of a series of {@link BalancingRoundSummary} results from allocation balancing rounds and creates reports thereof.
 * Summarizing balancer rounds and reporting the results will provide information with which to do cost-benefit analyses of the work that
 * shard allocation rebalancing executes.
 *
 * Any successfully added summary via {@link #addBalancerRoundSummary(BalancingRoundSummary)} will eventually be collected/drained and
 * reported via {@link #drainSummaries()}. This should still be done in the event of the node stepping down from master, on the assumption
 * that all summaries are added while master and should be drained for reporting.
 */
public class AllocationBalancingRoundSummaryService {
    private static final Logger logger = LogManager.getLogger(AllocationBalancingRoundSummaryService.class);

    // TODO (Dianna) a setting to disable logging by default. Also, no need to worry about APM metrics. Just set logging up first.
    // Then, only track if enabled -- otherwise we'll never drain the queue!

    /**
     * A concurrency-safe list of balancing round summaries. Balancer rounds are run and added here serially, so the queue will naturally
     * progress from newer to older results.
     */
    private ConcurrentLinkedQueue<BalancingRoundSummary> summaries = new ConcurrentLinkedQueue<>();

    /**
     * Returns a combined summary of all unreported allocation round summaries: may summarize a single balancer round, multiple, or none.
     *
     * @return returns {@link CombinedBalancingRoundSummary#EMPTY_RESULTS} if there are no unreported balancing rounds.
     */
    public CombinedBalancingRoundSummary drainSummaries() {
        ArrayList<BalancingRoundSummary> batchOfSummaries = new ArrayList<>();
        while (summaries.isEmpty() == false) {
            batchOfSummaries.add(summaries.poll());
        }
        return CombinedBalancingRoundSummary.combine(batchOfSummaries);
    }

    public void addBalancerRoundSummary(BalancingRoundSummary summary) {
        summaries.add(summary);

        if (summaries.size() == 5) {
            drainAndLogSummaries();
        }
    }

    private void drainAndLogSummaries() {
        var combinedSummaries = drainSummaries();
        logger.info("");
    }

}
