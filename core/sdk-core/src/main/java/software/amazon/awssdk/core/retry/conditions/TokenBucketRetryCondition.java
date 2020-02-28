/*
 * Copyright 2010-2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.awssdk.core.retry.conditions;

import java.util.Optional;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.internal.capacity.AtomicCapacity;
import software.amazon.awssdk.core.internal.capacity.DefaultTokenBucketRetryCondition;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.utils.Validate;

@SdkPublicApi
public class TokenBucketRetryCondition implements RetryCondition {
    private static final ExecutionAttribute<Capacity> LAST_ACQUIRED_CAPACITY =
        new ExecutionAttribute<>("TokenBucketRetryCondition.LAST_ACQUIRED_CAPACITY");

    private final AtomicCapacity capacity;
    private final TokenBucketExceptionCostCalculator exceptionCostCalculator;

    private TokenBucketRetryCondition(Builder builder) {
        this.capacity = new AtomicCapacity(Validate.notNull(builder.tokenBucketSize, "tokenBucketSize"));
        this.exceptionCostCalculator = Validate.notNull(builder.exceptionCostCalculator, "exceptionCostCalculator");
    }

    public static TokenBucketRetryCondition create() {
        return DefaultTokenBucketRetryCondition.forRetryMode(RetryMode.defaultRetryModeInstance());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Optional<Capacity> getCapacityForExecution(ExecutionAttributes attributes) {
        return Optional.ofNullable(attributes.getAttribute(LAST_ACQUIRED_CAPACITY));
    }

    public int tokensAvailable() {
        return capacity.currentCapacity();
    }

    @Override
    public boolean shouldRetry(RetryPolicyContext context) {
        int costOfFailure = exceptionCostCalculator.apply(context.exception());
        Validate.isTrue(costOfFailure >= 0, "Cost of failure must not be negative, but was " + costOfFailure);

        Optional<Capacity> capacity = this.capacity.tryAcquire(costOfFailure);

        capacity.ifPresent(c -> context.executionAttributes().putAttribute(LAST_ACQUIRED_CAPACITY, c));

        return capacity.isPresent();
    }

    @Override
    public void requestSucceeded(RetryPolicyContext context) {
        Capacity lastAcquiredCapacity = context.executionAttributes().getAttribute(LAST_ACQUIRED_CAPACITY);

        if (lastAcquiredCapacity == null || lastAcquiredCapacity.capacityAcquired() == 0) {
            capacity.release(1);
        } else {
            capacity.release(lastAcquiredCapacity.capacityAcquired());
        }
    }

    public static final class Builder {
        private Integer tokenBucketSize;
        private TokenBucketExceptionCostCalculator exceptionCostCalculator;

        /**
         * Create using {@link TokenBucketRetryCondition#builder()}.
         */
        private Builder() {}

        public Builder tokenBucketSize(int tokenBucketSize) {
            this.tokenBucketSize = tokenBucketSize;
            return this;
        }

        public Builder exceptionCostCalculator(TokenBucketExceptionCostCalculator exceptionCostCalculator) {
            this.exceptionCostCalculator = exceptionCostCalculator;
            return this;
        }

        public TokenBucketRetryCondition build() {
            return new TokenBucketRetryCondition(this);
        }
    }

    public static final class Capacity {
        private final int capacityAcquired;
        private final int capacityRemaining;

        private Capacity(Builder builder) {
            this.capacityAcquired = Validate.notNull(builder.capacityAcquired, "capacityAcquired");
            this.capacityRemaining = Validate.notNull(builder.capacityRemaining, "capacityRemaining");
        }

        public static Builder builder() {
            return new Builder();
        }

        public int capacityAcquired() {
            return capacityAcquired;
        }

        public int capacityRemaining() {
            return capacityRemaining;
        }

        public static class Builder {
            private Integer capacityAcquired;
            private Integer capacityRemaining;

            private Builder() {}

            public Builder capacityAcquired(Integer capacityAcquired) {
                this.capacityAcquired = capacityAcquired;
                return this;
            }

            public Builder capacityRemaining(Integer capacityRemaining) {
                this.capacityRemaining = capacityRemaining;
                return this;
            }

            public Capacity build() {
                return new Capacity(this);
            }
        }
    }
}
