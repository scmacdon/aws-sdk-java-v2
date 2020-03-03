/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.core.retry;

import software.amazon.awssdk.annotations.Immutable;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.internal.capacity.DefaultTokenBucketRetryCondition;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.AndRetryCondition;
import software.amazon.awssdk.core.retry.conditions.MaxNumberOfRetriesCondition;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.utils.ToString;
import software.amazon.awssdk.utils.builder.CopyableBuilder;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;

/**
 * Interface for specifying a retry policy to use when evaluating whether or not a request should be retried. An implementation
 * of this interface can be provided to {@link ClientOverrideConfiguration#retryPolicy()} or the {@link #builder()}} can be used
 * to construct a retry policy from SDK provided policies or policies that directly implement {@link BackoffStrategy} and/or
 * {@link RetryCondition}.
 *
 * When using the {@link #builder()} the SDK will use default values for fields that are not provided. The default number of
 * retries that will be used is {@link SdkDefaultRetrySetting#defaultMaxAttempts()} - 1. The default retry condition is
 * {@link RetryCondition#defaultRetryCondition()} and the default backoff strategy is {@link BackoffStrategy#defaultStrategy()}.
 *
 * @see RetryCondition for a list of SDK provided retry condition strategies
 * @see BackoffStrategy for a list of SDK provided backoff strategies
 */
@Immutable
@SdkPublicApi
public final class RetryPolicy implements ToCopyableBuilder<RetryPolicy.Builder, RetryPolicy> {
    private final boolean furtherRefinementsAllowed;
    private final RetryMode retryMode;
    private final BackoffStrategy backoffStrategy;
    private final BackoffStrategy throttlingBackoffStrategy;
    private final Integer numRetries;
    private final RetryCondition retryCondition;
    private final RetryCondition retryCapacityCondition;

    private final RetryCondition aggregateRetryCondition;

    private RetryPolicy(BuilderImpl builder) {
        this.furtherRefinementsAllowed = builder.furtherRefinementsAllowed;
        this.retryMode = builder.retryMode;
        this.backoffStrategy = builder.backoffStrategy;
        this.throttlingBackoffStrategy = builder.throttlingBackoffStrategy;
        this.numRetries = builder.numRetries;
        this.retryCondition = builder.retryCondition;
        this.retryCapacityCondition = builder.retryCapacityCondition;

        this.aggregateRetryCondition = generateAggregateRetryCondition();
    }

    private RetryCondition generateAggregateRetryCondition() {
        RetryCondition aggregate = AndRetryCondition.create(MaxNumberOfRetriesCondition.create(numRetries),
                                                            retryCondition);
        if (retryCapacityCondition != null) {
            return AndRetryCondition.create(aggregate, retryCapacityCondition);
        }
        return aggregate;
    }

    public RetryMode retryMode() {
        return retryMode;
    }

    public boolean furtherRefinementsAllowed() {
        return furtherRefinementsAllowed;
    }

    public RetryCondition aggregateRetryCondition() {
        return aggregateRetryCondition;
    }

    public RetryCondition retryCondition() {
        return retryCondition;
    }

    public BackoffStrategy backoffStrategy() {
        return backoffStrategy;
    }

    public BackoffStrategy throttlingBackoffStrategy() {
        return throttlingBackoffStrategy;
    }

    public Integer numRetries() {
        return numRetries;
    }

    public Builder toBuilder() {
        return builder(retryMode).furtherRefinementsAllowed(furtherRefinementsAllowed)
                                 .numRetries(numRetries)
                                 .retryCondition(retryCondition)
                                 .backoffStrategy(backoffStrategy)
                                 .throttlingBackoffStrategy(throttlingBackoffStrategy)
                                 .retryCapacityCondition(retryCapacityCondition);
    }

    @Override
    public String toString() {
        return ToString.builder("RetryPolicy")
                       .add("furtherRefinementsAllowed", furtherRefinementsAllowed)
                       .add("aggregateRetryCondition", aggregateRetryCondition)
                       .add("backoffStrategy", backoffStrategy)
                       .add("throttlingBackoffStrategy", throttlingBackoffStrategy)
                       .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RetryPolicy that = (RetryPolicy) o;

        if (furtherRefinementsAllowed != that.furtherRefinementsAllowed) {
            return false;
        }
        if (!aggregateRetryCondition.equals(that.aggregateRetryCondition)) {
            return false;
        }
        if (!backoffStrategy.equals(that.backoffStrategy)) {
            return false;
        }
        if (!throttlingBackoffStrategy.equals(that.throttlingBackoffStrategy)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = aggregateRetryCondition.hashCode();
        result = 31 * result + Boolean.hashCode(furtherRefinementsAllowed);
        result = 31 * result + backoffStrategy.hashCode();
        result = 31 * result + throttlingBackoffStrategy.hashCode();
        return result;
    }

    public static RetryPolicy defaultRetryPolicy() {
        return forRetryMode(RetryMode.defaultRetryModeInstance());
    }

    public static Builder builder() {
        return new BuilderImpl(RetryMode.defaultRetryModeInstance());
    }

    public static Builder builder(RetryMode retryMode) {
        return new BuilderImpl(retryMode);
    }

    public static RetryPolicy forRetryMode(RetryMode retryMode) {
        return RetryPolicy.builder(retryMode).build();
    }

    public static RetryPolicy none() {
        return RetryPolicy.builder()
                          .numRetries(0)
                          .backoffStrategy(BackoffStrategy.none())
                          .throttlingBackoffStrategy(BackoffStrategy.none())
                          .retryCondition(RetryCondition.none())
                          .build();
    }

    public interface Builder extends CopyableBuilder<Builder, RetryPolicy> {
        Builder furtherRefinementsAllowed(boolean furtherRefinementsAllowed);

        boolean furtherRefinementsAllowed();

        Builder backoffStrategy(BackoffStrategy backoffStrategy);

        BackoffStrategy backoffStrategy();

        Builder throttlingBackoffStrategy(BackoffStrategy backoffStrategy);

        BackoffStrategy throttlingBackoffStrategy();

        Builder retryCondition(RetryCondition retryCondition);

        RetryCondition retryCondition();

        Builder retryCapacityCondition(RetryCondition retryCapacityCondition);

        RetryCondition retryCapacityCondition();

        Builder numRetries(Integer numRetries);

        Integer numRetries();
        
        RetryPolicy build();
    }

    /**
     * Builder for a {@link RetryPolicy}.
     */
    private static final class BuilderImpl implements Builder {
        private final RetryMode retryMode;

        private boolean furtherRefinementsAllowed;
        private Integer numRetries;
        private BackoffStrategy backoffStrategy;
        private BackoffStrategy throttlingBackoffStrategy;
        private RetryCondition retryCondition;
        private RetryCondition retryCapacityCondition;

        private BuilderImpl(RetryMode retryMode) {
            this.retryMode = retryMode;
            this.numRetries = SdkDefaultRetrySetting.maxAttempts(retryMode) - 1;
            this.furtherRefinementsAllowed = true;
            this.backoffStrategy = BackoffStrategy.defaultStrategy();
            this.throttlingBackoffStrategy = BackoffStrategy.defaultThrottlingStrategy();
            this.retryCondition = RetryCondition.defaultRetryCondition();
            this.retryCapacityCondition = DefaultTokenBucketRetryCondition.forRetryMode(retryMode);
        }

        @Override
        public Builder furtherRefinementsAllowed(boolean furtherRefinementsAllowed) {
            this.furtherRefinementsAllowed = furtherRefinementsAllowed;
            return this;
        }

        public void setFurtherRefinementsAllowed(boolean furtherRefinementsAllowed) {
            furtherRefinementsAllowed(furtherRefinementsAllowed);
        }

        @Override
        public boolean furtherRefinementsAllowed() {
            return furtherRefinementsAllowed;
        }

        @Override
        public Builder numRetries(Integer numRetries) {
            this.numRetries = numRetries;
            return this;
        }

        public void setNumRetries(Integer numRetries) {
            numRetries(numRetries);
        }

        @Override
        public Integer numRetries() {
            return numRetries;
        }

        @Override
        public Builder backoffStrategy(BackoffStrategy backoffStrategy) {
            this.backoffStrategy = backoffStrategy;
            return this;
        }

        public void setBackoffStrategy(BackoffStrategy backoffStrategy) {
            backoffStrategy(backoffStrategy);
        }

        @Override
        public BackoffStrategy backoffStrategy() {
            return backoffStrategy;
        }

        @Override
        public Builder throttlingBackoffStrategy(BackoffStrategy throttlingBackoffStrategy) {
            this.throttlingBackoffStrategy = throttlingBackoffStrategy;
            return this;
        }

        @Override
        public BackoffStrategy throttlingBackoffStrategy() {
            return throttlingBackoffStrategy;
        }

        public void setThrottlingBackoffStrategy(BackoffStrategy throttlingBackoffStrategy) {
            this.throttlingBackoffStrategy = throttlingBackoffStrategy;
        }

        @Override
        public Builder retryCondition(RetryCondition retryCondition) {
            this.retryCondition = retryCondition;
            return this;
        }

        public void setRetryCondition(RetryCondition retryCondition) {
            retryCondition(retryCondition);
        }

        @Override
        public RetryCondition retryCondition() {
            return retryCondition;
        }

        @Override
        public Builder retryCapacityCondition(RetryCondition retryCapacityCondition) {
            this.retryCapacityCondition = retryCapacityCondition;
            return this;
        }

        public void setRetryCapacityCondition(RetryCondition retryCapacityCondition) {
            retryCapacityCondition(retryCapacityCondition);
        }

        @Override
        public RetryCondition retryCapacityCondition() {
            return this.retryCapacityCondition;
        }

        @Override
        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
