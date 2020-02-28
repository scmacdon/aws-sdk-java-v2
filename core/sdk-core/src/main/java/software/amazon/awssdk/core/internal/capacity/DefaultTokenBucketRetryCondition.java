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

import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.conditions.TokenBucketExceptionCostCalculator;
import software.amazon.awssdk.core.retry.conditions.TokenBucketRetryCondition;

@SdkInternalApi
public class DefaultTokenBucketRetryCondition {
    private DefaultTokenBucketRetryCondition() {}

    public static TokenBucketRetryCondition forRetryMode(RetryMode mode) {
        return TokenBucketRetryCondition.builder()
                                         .tokenBucketSize(SdkDefaultRetrySetting.TOKEN_BUCKET_SIZE)
                                         .exceptionCostCalculator(getExceptionCostCalculator(mode))
                                         .build();
    }

    private static TokenBucketExceptionCostCalculator getExceptionCostCalculator(RetryMode mode) {
        switch (mode) {
            case LEGACY: return TokenBucketExceptionCostCalculator.builder()
                                                                  .throttlingExceptionCost(0)
                                                                  .defaultExceptionCost(5)
                                                                  .build();

            case STANDARD: return  TokenBucketExceptionCostCalculator.builder()
                                                                     .defaultExceptionCost(5)
                                                                     .build();

            default: throw new IllegalStateException("Unsupported RetryMode: " + mode);
        }
    }
}
