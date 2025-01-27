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
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.threadpool.Scheduler;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of a series of {@link BalancingRoundSummary} results from allocation balancing rounds and creates reports thereof.
 * Summarizing balancer rounds and reporting the results will provide information with which to do cost-benefit analyses of the work that
 * shard allocation rebalancing executes.
 *
 * Any successfully added summary via {@link #addBalancerRoundSummary(BalancingRoundSummary)} will eventually be collected/drained and
 * reported. This should still be done in the event of the node stepping down from master, on the assumption that all summaries are only
 * added while master and should be drained for reporting. There is no need to start/stop this service with master election/stepdown because
 * balancer rounds will no longer be supplied when not master. It will simply drain the last summaries and then have nothing more to do.
 */
public class AllocationBalancingRoundSummaryService {

    /** Turns on or off balancing round summary reporting. */
    public static final Setting<Boolean> ENABLE_BALANCER_ROUND_SUMMARIES_SETTING = Setting.boolSetting(
        "cluster.routing.allocation.desired_balance.enable_balancer_round_summaries",
        false,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    /** Controls how frequently in time balancer round summaries are logged. If less than zero, effectively disables reporting. */
    public static final Setting<TimeValue> BALANCER_ROUND_SUMMARIES_LOG_INTERVAL_SETTING = Setting.timeSetting(
        "cluster.routing.allocation.desired_balance.balanace_round_summaries_interval",
        TimeValue.timeValueSeconds(10),
        TimeValue.MINUS_ONE,
        Setting.Property.NodeScope,
        Setting.Property.Dynamic
    );

    private static final Logger logger = LogManager.getLogger(AllocationBalancingRoundSummaryService.class);
    private final ThreadPool threadPool;
    private volatile boolean enableBalancerRoundSummaries = false;
    private volatile TimeValue summaryReportInterval = TimeValue.MINUS_ONE;

    /**
     * A concurrency-safe list of balancing round summaries. Balancer rounds are run and added here serially, so the queue will naturally
     * progress from newer to older results.
     */
    private ConcurrentLinkedQueue<BalancingRoundSummary> summaries = new ConcurrentLinkedQueue<>();

    /** This reference is set when reporting is scheduled. If it is null, then reporting is inactive. */
    private AtomicReference<Scheduler.Cancellable> scheduledReportFuture = new AtomicReference<>();

    public AllocationBalancingRoundSummaryService(ThreadPool threadPool, ClusterSettings clusterSettings) {
        this.threadPool = threadPool;
        clusterSettings.initializeAndWatch(ENABLE_BALANCER_ROUND_SUMMARIES_SETTING, value -> {
            this.enableBalancerRoundSummaries = value;
            updateBalancingRoundSummaryReporting();
        });
        clusterSettings.initializeAndWatch(BALANCER_ROUND_SUMMARIES_LOG_INTERVAL_SETTING, value -> {
            this.summaryReportInterval = value;
            updateBalancingRoundSummaryReporting();
        });
    }

    /**
     * Adds the summary of a balancing round. If summaries are enabled, this will eventually be reported (logging, etc.). If balancer round
     * summaries are not enabled in the cluster, then the summary is immediately discarded (so as not to fill up a data structure that will
     * never be drained).
     */
    public void addBalancerRoundSummary(BalancingRoundSummary summary) {
        if (enableBalancerRoundSummaries == false || summaryReportInterval.millis() < 0) {
            return;
        }

        summaries.add(summary);
    }

    /**
     * Reports on all the balancer round summaries added since the last call to this method, if there are any. Then reschedules itself per
     * the {@link #ENABLE_BALANCER_ROUND_SUMMARIES_SETTING} and {@link #BALANCER_ROUND_SUMMARIES_LOG_INTERVAL_SETTING} settings.
     */
    private void reportSummariesAndThenReschedule() {
        drainAndReportSummaries();
        rescheduleReporting(this.enableBalancerRoundSummaries, this.summaryReportInterval);
    }

    /**
     * Drains all the waiting balancer round summaries (if there are any) and reports them.
     */
    private void drainAndReportSummaries() {
        var combinedSummaries = drainSummaries();
        if (combinedSummaries == CombinedBalancingRoundSummary.EMPTY_RESULTS) {
            return;
        }

        logger.info("Balancing round summaries: " + combinedSummaries);
    }

    /**
     * Returns a combined summary of all unreported allocation round summaries: may summarize a single balancer round, multiple, or none.
     *
     * @return returns {@link CombinedBalancingRoundSummary#EMPTY_RESULTS} if there are no unreported balancing rounds.
     */
    private CombinedBalancingRoundSummary drainSummaries() {
        ArrayList<BalancingRoundSummary> batchOfSummaries = new ArrayList<>();
        while (summaries.isEmpty() == false) {
            batchOfSummaries.add(summaries.poll());
        }
        return CombinedBalancingRoundSummary.combine(batchOfSummaries);
    }

    /**
     * Returns whether reporting is turned on with the given point-in-time summary setting values.
     */
    private boolean shouldReschedule(boolean enableValue, TimeValue intervalValue) {
        if (enableValue == false || intervalValue.millis() < 0) {
            return false;
        }
        return true;
    }

    /**
     * Schedules a periodic task to drain and report the latest balancer round summaries, or cancels the already running task, if the latest
     * setting values dictate a change to enable or disable reporting. A change to {@link #BALANCER_ROUND_SUMMARIES_LOG_INTERVAL_SETTING}
     * will only take effect when the periodic task completes and reschedules itself.
     */
    private void updateBalancingRoundSummaryReporting() {
        var currentEnableValue = this.enableBalancerRoundSummaries;
        var currentIntervalValue = this.summaryReportInterval;
        if (shouldReschedule(currentEnableValue, currentIntervalValue)) {
            startReporting(currentIntervalValue);
        } else {
            cancelReporting();
            // Clear the data structure so that we don't retain unnecessary memory.
            drainSummaries();
        }
    }

    /**
     * Schedules a reporting task, if one is not already scheduled. The reporting task will reschedule itself going forward.
     */
    private void startReporting(TimeValue intervalValue) {
        if (scheduledReportFuture.get() == null) {
            scheduleReporting(intervalValue);
        }
    }

    /**
     * Cancels the future reporting task and resets {@link #scheduledReportFuture} to null.
     * Note that this is best-effort: cancellation can race with {@link #rescheduleReporting}. But that is okay because the subsequent
     * {@link #rescheduleReporting} will use the latest settings and choose to cancel reporting if appropriate.
     */
    private void cancelReporting() {
        var future = scheduledReportFuture.getAndSet(null);
        if (future != null) {
            future.cancel();
        }
    }

    private void scheduleReporting(TimeValue intervalValue) {
        scheduledReportFuture.set(
            threadPool.schedule(this::reportSummariesAndThenReschedule, intervalValue, threadPool.executor(ThreadPool.Names.GENERIC))
        );
    }

    /**
     * Looks at the given setting values and decides whether to schedule another reporting task or cancel reporting now.
     */
    private void rescheduleReporting(boolean enableValue, TimeValue intervalValue) {
        if (shouldReschedule(enableValue, intervalValue)) {
            // It's possible that this races with a concurrent call to cancel reporting, but that's okay. The next rescheduleReporting call
            // will check the latest settings and cancel.
            scheduleReporting(intervalValue);
        } else {
            cancelReporting();
        }
    }

    // @VisibleForTesting
    protected void verifyNumberOfSummaries(int numberOfSummaries) {
        assert numberOfSummaries == summaries.size();
    }

}
