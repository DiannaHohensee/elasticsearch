/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.snapshots;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.snapshots.IndexShardSnapshotStatus;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.MockLog;
import org.elasticsearch.threadpool.TestThreadPool;
import org.junit.After;
import org.junit.Before;

import java.util.concurrent.TimeUnit;

public class SnapshotShutdownProgressTrackerTests extends ESTestCase {
    private static final Logger logger = LogManager.getLogger(SnapshotShutdownProgressTrackerTests.class);

    final Settings settings = Settings.builder()
        .put(
            SnapshotShutdownProgressTracker.SNAPSHOT_PROGRESS_DURING_SHUTDOWN_INTERVAL_TIME_SETTING.getKey(),
            TimeValue.timeValueMillis(200)
        )
        .build();
    TestThreadPool testThreadPool = new TestThreadPool("thread-pool-for-test");
    private MockLog mockLog;

    @Before
    public void setUpThreadPool() {
        testThreadPool = new TestThreadPool("test");
    }

    @After
    public void shutdownThreadPool() {
        assert TestThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Before
    public void setUpMockLog() {
        mockLog = MockLog.capture(SnapshotShutdownProgressTrackerTests.class);
    }

    @After
    public void tearDownMockLog() {
        mockLog.close();
    }

    /**
     * Increments the tracker's shard snapshot completion stats. Evenly adds to each type of {@link IndexShardSnapshotStatus.Stage} stat
     * supported by the tracker.
     */
    void simulateShardSnapshotsCompleting(SnapshotShutdownProgressTracker tracker, int numShardSnapshots) {
        for (int i = 0; i < numShardSnapshots; ++i) {
            logger.info("~~~simulateShardSnapshotsCompleting i: " + i);
            tracker.incNumberOfShardSnapshotsInProgress();
            tracker.decNumberOfShardSnapshotsInProgress(switch (i % 4) {
                case 0 -> IndexShardSnapshotStatus.Stage.DONE;
                case 1 -> IndexShardSnapshotStatus.Stage.ABORTED;
                case 2 -> IndexShardSnapshotStatus.Stage.FAILURE;
                case 3 -> IndexShardSnapshotStatus.Stage.PAUSED;
                default -> IndexShardSnapshotStatus.Stage.PAUSING;  // decNumberOfShardSnapshotsInProgress will assert receiving this value.
            });
        }
    }

    public void testTrackerLogsStats() throws Exception {
        SnapshotShutdownProgressTracker tracker = new SnapshotShutdownProgressTracker(settings, testThreadPool);

        mockLog.addExpectation(
            new MockLog.UnseenEventExpectation(
                "unset start timestamp",
                SnapshotShutdownProgressTracker.class.getCanonicalName(),
                Level.INFO,
                "Node shutdown cluster state update received at [-1]*Finished signalling shard snapshots to pause [-1]*"
            )
        );
//        mockLog.addExpectation(
//            new MockLog.SeenEventExpectation(
//                "unset pausing complete timestamp",
//                SnapshotShutdownProgressTracker.class.getCanonicalName(),
//                Level.INFO,
//                "**"
//            )
//        );
//        mockLog.addExpectation(
//            new MockLog.SeenEventExpectation(
//                "expected ==0 completion stats. Debug info: " + tracker.logAllStatsForTesting(),
//                SnapshotShutdownProgressTracker.class.getCanonicalName(),
//                Level.INFO,
//                "*Shard snapshot completion stats since shutdown began: Done [0]; Failed [0]; Aborted [0]; Paused [0]*"
//            )
//        );

        // Simulate starting shutdown -- should reset the completion stats and start logging
        tracker.onClusterStateAddShutdown();

        // Wait for the initial progress log message with no shard snapshot completions.
        assertBusy(mockLog::assertAllExpectationsMatched);

        // Simulate updating the shard snapshot completion stats.
        mockLog.addExpectation(
            new MockLog.SeenEventExpectation(
                "expected >0 completion stats. Debug info: " + tracker.logAllStatsForTesting(),
                SnapshotShutdownProgressTracker.class.getCanonicalName(),
                Level.INFO,
                "*Shard snapshot completion stats since shutdown began: Done [2]; Failed [1]; Aborted [1]; Paused [1]*"
            )
        );
        simulateShardSnapshotsCompleting(tracker, 5);
        tracker.assertStatsForTesting(2, 1, 1, 1);

        // Wait for the next periodic log message to include the new completion stats.
        assertBusy(mockLog::assertAllExpectationsMatched);
    }

    public void testTrackerLogsStartAndPauseTimestamps() throws Exception {
        SnapshotShutdownProgressTracker tracker = new SnapshotShutdownProgressTracker(settings, testThreadPool);

        // Simulate updating the shard snapshot completion stats.
        simulateShardSnapshotsCompleting(tracker, 6);
        tracker.assertStatsForTesting(2, 2, 1, 1);

        mockLog.addExpectation(
            new MockLog.UnseenEventExpectation(
                "unset pausing complete timestamp",
                SnapshotShutdownProgressTracker.class.getName(),
                Level.INFO,
                "*Finished signalling shard snapshots to pause [-1]*"
            )
        );

        // Set a pausing complete timestamp.
        tracker.onClusterStatePausingSetForAllShardSnapshots();

        // Simulate starting shutdown -- should reset the completion stats and start logging
        tracker.onClusterStateAddShutdown();

        // Wait for the first log message to ensure the pausing timestamp was set.
        assertBusy(mockLog::assertAllExpectationsMatched);
    }
}
