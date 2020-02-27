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

package software.amazon.awssdk.core.capacity;

import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.internal.capacity.AtomicCapacity;
import software.amazon.awssdk.utils.Validate;

@SdkPublicApi
public final class TokenBucketRequestCapacity implements RequestCapacity {
    private static final ExecutionAttribute<Integer> LAST_ACQUIRED_CAPACITY =
        new ExecutionAttribute<>("LegacyRequestCapacity.LAST_ACQUIRED_CAPACITY");

    private final AtomicCapacity capacity;
    private final TokenBucketExceptionCostCalculator exceptionCostCalculator;

    private TokenBucketRequestCapacity(Builder builder) {
        this.capacity = new AtomicCapacity(Validate.notNull(builder.tokenBucketSize, "tokenBucketSize"));
        this.exceptionCostCalculator = Validate.notNull(builder.exceptionCostCalculator, "exceptionCostCalculator");
    }

    public static Builder builder() {
        return new Builder();
    }

    public int currentCapacity() {
        return capacity.currentCapacity();
    }

    @Override
    public boolean shouldAttemptRequest(RequestCapacityContext context) {
        if (context.attemptNumber() == 1) {
            return true;
        }

        int costOfFailure = exceptionCostCalculator.apply(context.latestFailure());
        Validate.isTrue(costOfFailure >= 0, "Cost of failure must not be negative, but was " + costOfFailure);

        context.executionAttributes().putAttribute(LAST_ACQUIRED_CAPACITY, costOfFailure);

        return capacity.tryAcquire(costOfFailure);
    }

    @Override
    public void requestSucceeded(RequestCapacityContext context) {
        Integer lastAcquiredCapacity = context.executionAttributes().getAttribute(LAST_ACQUIRED_CAPACITY);

        if (lastAcquiredCapacity == null || lastAcquiredCapacity == 0) {
            capacity.release(1);
        } else {
            capacity.release(lastAcquiredCapacity);
        }
    }

    public static final class Builder {
        private Integer tokenBucketSize;
        private TokenBucketExceptionCostCalculator exceptionCostCalculator;

        /**
         * Create using {@link TokenBucketRequestCapacity#builder()}.
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

        public TokenBucketRequestCapacity build() {
            return new TokenBucketRequestCapacity(this);
        }
    }

}
