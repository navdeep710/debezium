/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.embedded.spi;

import java.time.Duration;

import org.apache.kafka.connect.storage.OffsetBackingStore;

import io.debezium.config.Configuration;
import io.debezium.embedded.EmbeddedEngine;

/**
 * The policy that defines when the offsets should be committed to {@link OffsetBackingStore offset storage}.
 *
 * @author Randall Hauch
 */
@FunctionalInterface
public interface OffsetCommitPolicy {

    /**
     * An {@link OffsetCommitPolicy} that will commit offsets as frequently as possible. This may result in reduced
     * performance, but it has the least potential for seeing source records more than once upon restart.
     */
    public static class AlwaysCommitOffsetPolicy implements OffsetCommitPolicy {

        @Override
        public boolean performCommit(long numberOfMessagesSinceLastCommit, Duration timeSinceLastCommit) {
            return true;
        }
    }

    /**
     * An {@link OffsetCommitPolicy} that will commit offsets no more than the specified time period. If the specified
     * time is less than {@code 0} then the policy will behave as {@link AlwaysCommitOffsetPolicy}.
     * @see io.debezium.embedded.EmbeddedEngine.OFFSET_FLUSH_INTERVAL_MS
     */
    public static class PeriodicCommitOffsetPolicy implements OffsetCommitPolicy {

        private final Duration minimumTime;

        public PeriodicCommitOffsetPolicy(Configuration config) {
            minimumTime = Duration.ofMillis(config.getLong(EmbeddedEngine.OFFSET_FLUSH_INTERVAL_MS));
        }

        @Override
        public boolean performCommit(long numberOfMessagesSinceLastCommit, Duration timeSinceLastCommit) {
                return timeSinceLastCommit.compareTo(minimumTime) >= 0;
        }
    }

    static OffsetCommitPolicy always() {
        return new AlwaysCommitOffsetPolicy();
    }

    static OffsetCommitPolicy periodic(Configuration config) {
        return new PeriodicCommitOffsetPolicy(config);
    }

    /**
     * Determine if a commit of the offsets should be performed.
     *
     * @param numberOfMessagesSinceLastCommit the number of messages that have been received from the connector since last
     *            the offsets were last committed; never negative
     * @param timeSinceLastCommit the time that has elapsed since the offsets were last committed; never negative
     * @return {@code true} if the offsets should be committed, or {@code false} otherwise
     */
    boolean performCommit(long numberOfMessagesSinceLastCommit, Duration timeSinceLastCommit);

    /**
     * Obtain a new {@link OffsetCommitPolicy} that will commit offsets if this policy OR the other requests it.
     *
     * @param other the other commit policy; if null, then this policy instance is returned as is
     * @return the resulting policy; never null
     */
    default OffsetCommitPolicy or(OffsetCommitPolicy other) {
        if ( other == null ) {
            return this;
        }
        return (number, time) -> this.performCommit(number, time) || other.performCommit(number, time);
    }

    /**
     * Obtain a new {@link OffsetCommitPolicy} that will commit offsets if both this policy AND the other requests it.
     *
     * @param other the other commit policy; if null, then this policy instance is returned as is
     * @return the resulting policy; never null
     */
    default OffsetCommitPolicy and(OffsetCommitPolicy other) {
        if ( other == null ) {
            return this;
        }
        return (number, time) -> this.performCommit(number, time) && other.performCommit(number, time);
    }
}
