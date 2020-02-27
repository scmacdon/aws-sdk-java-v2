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
import software.amazon.awssdk.core.internal.capacity.DefaultRequestCapacity;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;

/**
 * An interface used to limit the number of requests being sent to a service, regardless of the {@link RetryPolicy} configured
 * on the client.
 *
 * <p>
 * A {@link RetryPolicy} allows automatically
 */
@SdkPublicApi
@FunctionalInterface
public interface RequestCapacity {
    boolean shouldAttemptRequest(RequestCapacityContext context);

    default void requestSucceeded(RequestCapacityContext context) {

    }

    static RequestCapacity defaultRequestCapacity() {
        return DefaultRequestCapacity.forRetryMode(RetryMode.defaultRetryModeInstance());
    }

    static RequestCapacity forRetryMode(RetryMode mode) {
        return DefaultRequestCapacity.forRetryMode(mode);
    }

    static RequestCapacity unlimited() {
        return UnlimitedRequestCapacity.create();
    }
}
