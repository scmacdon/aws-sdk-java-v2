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

import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.utils.Validate;

@SdkInternalApi // TODO: internal
public class AtomicCapacity {
    private final int maxCapacity;
    private final AtomicInteger capacity;

    public AtomicCapacity(int capacity) {
        this.maxCapacity = capacity;
        this.capacity = new AtomicInteger(capacity);
    }

    public boolean tryAcquire(int amountToAcquire) {
        Validate.isTrue(amountToAcquire >= 0, "Amount must not be negative.");

        if (amountToAcquire == 0) {
            return true;
        }


        while (true) {
            int currentCapacity = capacity.get();

            if (currentCapacity < 0) {
                return false;
            }

            int newCapacity = currentCapacity - amountToAcquire;
            if (capacity.compareAndSet(currentCapacity, newCapacity)) {
                return true;
            }
        }
    }

    public void release(int amountToRelease) {
        Validate.isTrue(amountToRelease >= 0, "Amount must not be negative.");

        if (amountToRelease == 0) {
            return;
        }

        while (true) {
            int currentCapacity = capacity.get();

            if (currentCapacity >= maxCapacity) {
                return;
            }

            int newCapacity = Math.min(currentCapacity + amountToRelease, maxCapacity);
            if (capacity.compareAndSet(currentCapacity, newCapacity)) {
                return;
            }
        }
    }
}
