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
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.capacity.RequestCapacity;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryUtils;

@SdkInternalApi
public class DefaultRequestCapacityFactory {
    private DefaultRequestCapacityFactory() {}

    public static DefaultRequestCapacityFactory create() {
        return new DefaultRequestCapacityFactory();
    }

    public RequestCapacity createRequestCapacity() {
        return new DefaultRequestCapacity(new AtomicCapacity(SdkDefaultRetrySetting.RETRY_THROTTLING_COST *
                                                             SdkDefaultRetrySetting.THROTTLED_RETRIES),
                                          getExceptionCostCalculator());
    }

    private Function<SdkException, Integer> getExceptionCostCalculator() {
        String retryMode = SdkSystemSetting.AWS_RETRY_MODE.getStringValueOrThrow();
        switch (retryMode) {
            case "legacy":
                return this::legacyExceptionCostCalculator;
            case "standard":
                return this::standardExceptionCostCalculator;
            default:
                throw new IllegalStateException("Unsupported retry policy mode: " + retryMode);
        }
    }

    private Integer legacyExceptionCostCalculator(SdkException exception) {
        if (RetryUtils.isThrottlingException(exception)) {
            return 0;
        }

        return 5;
    }

    private Integer standardExceptionCostCalculator(SdkException exception) {
        // TODO: Can we make network IO exceptions cost more?
        return 5;
    }
}
