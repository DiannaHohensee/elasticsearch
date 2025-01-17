/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation.allocator;

public class BalancingRoundSummaryBuilder {
    public static final BalancingRoundSummaryBuilder EMPTY_SUMMARY_BUILDER = new BalancingRoundSummaryBuilder();

    private long eventStartTime = 0;
    private long duration = 0;

    public BalancingRoundSummary build() {
        return new BalancingRoundSummary(eventStartTime, duration);
    }

    public void setEventStartTime(long startTime) {
        this.eventStartTime = startTime;
    }

    public void setEventEndTime(long endTime) {
        this.duration = endTime - this.eventStartTime;
    }
}
