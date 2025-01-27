/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation.allocator;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds combined {@link BalancingRoundSummary} results. Essentially holds a list of the balancing events and the summed up changes
 * across all those events: what allocation work was done across some period of time.
 *
 * @param numberOfBalancingRounds How many balancing round summaries are combined in this report.
 * @param duration A list of how long each balancing round took.
 */
public record CombinedBalancingRoundSummary(int numberOfBalancingRounds, ArrayList<Long> duration) {

    public static final CombinedBalancingRoundSummary EMPTY_RESULTS = new CombinedBalancingRoundSummary(0, new ArrayList<>(0));

    public static CombinedBalancingRoundSummary combine(List<BalancingRoundSummary> summaries) {
        if (summaries.isEmpty()) {
            return EMPTY_RESULTS;
        }

        int numSummaries = 0;
        ArrayList<Long> durations = new ArrayList<>(summaries.size());
        for (BalancingRoundSummary summary : summaries) {
            ++numSummaries;
            durations.add(summary.duration());
        }
        return new CombinedBalancingRoundSummary(numSummaries, durations);
    }

}
