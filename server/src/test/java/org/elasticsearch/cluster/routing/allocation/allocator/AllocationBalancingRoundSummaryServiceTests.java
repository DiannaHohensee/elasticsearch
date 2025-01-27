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
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.DeterministicTaskQueue;
import org.elasticsearch.core.TimeValue;
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
        .put(AllocationBalancingRoundSummaryService.ENABLE_BALANCER_ROUND_SUMMARIES_SETTING.getKey(), false)
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

    // Construction parameters for the service.

    ThreadPool testThreadPool;

    @Before
    public void setUpThreadPool() {
        deterministicTaskQueue = new DeterministicTaskQueue();
        testThreadPool = deterministicTaskQueue.getThreadPool();
    }

    public void testDisabledService() {
        var service = new AllocationBalancingRoundSummaryService(testThreadPool, disabledClusterSettings);

        try (var mockLog = MockLog.capture(AllocationBalancingRoundSummaryService.class)) {
            /**
             * Add a summary and check it is not logged.
             */

            service.addBalancerRoundSummary(new BalancingRoundSummary(50));
            service.verifyNumberOfSummaries(0); // when summaries are disabled, summaries are not retained when added.
            mockLog.addExpectation(
                new MockLog.UnseenEventExpectation(
                    "Running balancer summary logging",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "Balancing round summaries:*"
                )
            );

            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);
        }
    }

    public void testEnableService() {
        var service = new AllocationBalancingRoundSummaryService(testThreadPool, enabledClusterSettings);

        try (var mockLog = MockLog.capture(AllocationBalancingRoundSummaryService.class)) {
            /**
             * Add a summary and check it is logged.
             */

            service.addBalancerRoundSummary(new BalancingRoundSummary(50));
            service.verifyNumberOfSummaries(1);
            mockLog.addExpectation(
                new MockLog.SeenEventExpectation(
                    "Running balancer summary logging",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "Balancing round summaries:*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);

            /**
             * Add a second summary, check for more logging.
             */

            service.addBalancerRoundSummary(new BalancingRoundSummary(200));
            service.verifyNumberOfSummaries(1);
            mockLog.addExpectation(
                new MockLog.SeenEventExpectation(
                    "Running balancer summary logging a second time",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "Balancing round summaries:*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);
        }
    }

    public void testCombineSummaries() {
        var service = new AllocationBalancingRoundSummaryService(testThreadPool, enabledClusterSettings);

        try (var mockLog = MockLog.capture(AllocationBalancingRoundSummaryService.class)) {
            service.addBalancerRoundSummary(new BalancingRoundSummary(50));
            service.addBalancerRoundSummary(new BalancingRoundSummary(100));
            service.verifyNumberOfSummaries(2);
            mockLog.addExpectation(
                new MockLog.SeenEventExpectation(
                    "Running balancer summary logging of combined summaries",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "*150*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);
        }
    }

    public void testNoSummariesToReport() {
        var service = new AllocationBalancingRoundSummaryService(testThreadPool, enabledClusterSettings);

        try (var mockLog = MockLog.capture(AllocationBalancingRoundSummaryService.class)) {
            /**
             * First add some summaries to report, ensuring that the logging is active.
             */

            service.addBalancerRoundSummary(new BalancingRoundSummary(50));
            service.verifyNumberOfSummaries(1);
            mockLog.addExpectation(
                new MockLog.SeenEventExpectation(
                    "Running balancer summary logging of combined summaries",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "Balancing round summaries:*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);

            /**
             * Now check that there are no further log messages because there were no further summaries added.
             */

            mockLog.addExpectation(
                new MockLog.UnseenEventExpectation(
                    "No balancer round summary to log",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "Balancing round summaries:*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);
        }
    }

    public void testEnableAndThenDisableService() {
        ClusterSettings clusterSettings = new ClusterSettings(enabledSummariesSettings, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);

        var service = new AllocationBalancingRoundSummaryService(testThreadPool, clusterSettings);

        try (var mockLog = MockLog.capture(AllocationBalancingRoundSummaryService.class)) {
            /**
             * Add some summaries, but then disable the service before logging occurs. Disabling the service should drain and discard any
             * summaries waiting to be reported.
             */

            service.addBalancerRoundSummary(new BalancingRoundSummary(50));
            service.verifyNumberOfSummaries(1);

            Settings newSettings = Settings.builder()
                .put(AllocationBalancingRoundSummaryService.ENABLE_BALANCER_ROUND_SUMMARIES_SETTING.getKey(), false)
                .build();
            clusterSettings.applySettings(newSettings);

            service.verifyNumberOfSummaries(0);

            /**
             * Verify that new added summaries are not retained, since the service is disabled.
             */

            service.addBalancerRoundSummary(new BalancingRoundSummary(50));
            service.verifyNumberOfSummaries(0);
            mockLog.addExpectation(
                new MockLog.UnseenEventExpectation(
                    "Running balancer summary logging",
                    AllocationBalancingRoundSummaryService.class.getName(),
                    Level.INFO,
                    "Balancing round summaries:*"
                )
            );

            deterministicTaskQueue.advanceTime();
            deterministicTaskQueue.runAllRunnableTasks();
            mockLog.awaitAllExpectationsMatched();
            service.verifyNumberOfSummaries(0);
        }
    }
}
