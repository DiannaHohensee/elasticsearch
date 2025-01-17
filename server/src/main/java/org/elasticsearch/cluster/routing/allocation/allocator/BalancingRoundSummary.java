/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation.allocator;


/**
 * Summarizes the impact to the cluster as a result of a rebalancing round.
 * TODO: this is a WIP, see ES-10341.
 *
 * @param eventStartTime Time at which the desired balance calculation began due to a cluster event.
 * @param duration Time it took to complete desired balance calculation.
 */
public record BalancingRoundSummary(
    long eventStartTime,
    long duration
) {

    public static final BalancingRoundSummary EMPTY_SUMMARY = new BalancingRoundSummary(0,0);

    @Override
    public String toString() {
        return "BalancingRoundSummary{"
            + "eventStartTime="
            + eventStartTime
            + ", duration="
            + duration
            + '}';
    }

}
