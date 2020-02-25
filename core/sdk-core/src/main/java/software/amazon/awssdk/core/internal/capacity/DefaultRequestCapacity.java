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

package software.amazon.awssdk.core.internal.capacity;

import java.util.function.Function;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.capacity.RequestCapacity;
import software.amazon.awssdk.core.capacity.RequestCapacityContext;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;

@SdkInternalApi
public class DefaultRequestCapacity implements RequestCapacity {
    private static final ExecutionAttribute<Integer> LAST_ACQUIRED_CAPACITY =
        new ExecutionAttribute<>("LegacyRequestCapacity.LAST_ACQUIRED_CAPACITY");

    private final AtomicCapacity capacity;
    private final Function<SdkException, Integer> exceptionCostCalculator;

    DefaultRequestCapacity(AtomicCapacity capacity, Function<SdkException, Integer> exceptionCostCalculator) {
        this.capacity = capacity;
        this.exceptionCostCalculator = exceptionCostCalculator;
    }

    @Override
    public boolean shouldAttemptRequest(RequestCapacityContext context) {
        if (context.attemptNumber() == 1) {
            return true;
        }

        int costOfFailure = costOfFailure(context.latestFailure());

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

    private int costOfFailure(SdkException latestFailure) {
        return exceptionCostCalculator.apply(latestFailure);
    }
}
