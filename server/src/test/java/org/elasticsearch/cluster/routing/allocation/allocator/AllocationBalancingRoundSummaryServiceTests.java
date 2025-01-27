/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.routing.allocation.allocator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.coordination.Coordinator;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.DeterministicTaskQueue;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.snapshots.SnapshotShutdownProgressTracker;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.MockLog;
import org.elasticsearch.threadpool.ThreadPool;
import org.junit.Before;

public class AllocationBalancingRoundSummaryServiceTests extends ESTestCase {
    private static final Logger logger = LogManager.getLogger(AllocationBalancingRoundSummaryServiceTests.class);

    final Settings enabledSummariesSettings = Settings.builder()
        .put(AllocationBalancingRoundSummaryService.ENABLE_BALANCER_ROUND_SUMMARIES_SETTING.getKey(), true)
        .build();
    final Settings disabledSummariesSettings = Settings.builder()
        .put(AllocationBalancingRoundSummaryService.ENABLE_BALANCER_ROUND_SUMMARIES_SETTING.getKey(), true)
        .build();
    final Settings enabledSummariesButDisabledIntervalSettings = Settings.builder()
        .put(AllocationBalancingRoundSummaryService.ENABLE_BALANCER_ROUND_SUMMARIES_SETTING.getKey(), true)
        .put(AllocationBalancingRoundSummaryService.BALANCER_ROUND_SUMMARIES_LOG_INTERVAL_SETTING.getKey(), TimeValue.MINUS_ONE)
        .build();

    ClusterSettings enabledClusterSettings = new ClusterSettings(enabledSummariesSettings, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
    ClusterSettings disabledClusterSettings = new ClusterSettings(disabledSummariesSettings, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
    ClusterSettings enabledSummariesButDisabledIntervalClusterSettings = new ClusterSettings(
        enabledSummariesButDisabledIntervalSettings,
        ClusterSettings.BUILT_IN_CLUSTER_SETTINGS
    );

    DeterministicTaskQueue deterministicTaskQueue;

    // Construction parameters for the Tracker.

    ThreadPool testThreadPool;

    @Before
    public void setUpThreadPool() {
        deterministicTaskQueue = new DeterministicTaskQueue();
        testThreadPool = deterministicTaskQueue.getThreadPool();
    }

    public void testMy() {

        try (var mockLog = MockLog.capture(Coordinator.class, SnapshotShutdownProgressTracker.class)) {
            mockLog.addExpectation(
                new MockLog.SeenEventExpectation(
                    //////// TODO (Dianna)
                    "no master status update requests",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "*master node reply to status update request [0]*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
        }
    }
}
