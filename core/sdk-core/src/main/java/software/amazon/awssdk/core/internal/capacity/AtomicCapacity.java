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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.retry.conditions.TokenBucketRetryCondition.Capacity;
import software.amazon.awssdk.utils.Validate;

@SdkInternalApi
public class AtomicCapacity {
    private final int maxCapacity;
    private final AtomicInteger capacity;

    public AtomicCapacity(int capacity) {
        this.maxCapacity = capacity;
        this.capacity = new AtomicInteger(capacity);
    }

    public Optional<Capacity> tryAcquire(int amountToAcquire) {
        Validate.isTrue(amountToAcquire >= 0, "Amount must not be negative.");

        if (amountToAcquire == 0) {
            return Optional.of(Capacity.builder()
                                       .capacityAcquired(0)
                                       .capacityRemaining(capacity.get())
                                       .build());
        }

        while (true) {
            int currentCapacity = capacity.get();

            int newCapacity = currentCapacity - amountToAcquire;
            if (newCapacity < 0) {
                return Optional.empty();
            }

            if (capacity.compareAndSet(currentCapacity, newCapacity)) {
                return Optional.of(Capacity.builder()
                                           .capacityAcquired(amountToAcquire)
                                           .capacityRemaining(newCapacity)
                                           .build());
            }
        }
    }

    public int currentCapacity() {
        return capacity.get();
    }

    public void release(int amountToRelease) {
        Validate.isTrue(amountToRelease >= 0, "Amount must not be negative.");

        if (amountToRelease == 0) {
            return;
        }

        while (true) {
            int currentCapacity = capacity.get();

            if (currentCapacity == maxCapacity) {
                return;
            }

            int newCapacity = Math.min(currentCapacity + amountToRelease, maxCapacity);
            if (capacity.compareAndSet(currentCapacity, newCapacity)) {
                return;
            }
        }
    }
}
