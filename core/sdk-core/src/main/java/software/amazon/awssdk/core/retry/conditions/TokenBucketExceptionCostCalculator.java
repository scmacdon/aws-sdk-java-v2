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

import java.util.function.Function;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.retry.RetryUtils;

@SdkPublicApi
@FunctionalInterface
public interface TokenBucketExceptionCostCalculator extends Function<SdkException, Integer> {
    static Builder builder() {
        return new Builder();
    }

    final class Builder {
        private Integer throttlingExceptionCost;
        private Integer defaultExceptionCost;

        private Builder() {}

        public Builder throttlingExceptionCost(int cost) {
            this.throttlingExceptionCost = cost;
            return this;
        }

        public Builder defaultExceptionCost(int cost) {
            this.defaultExceptionCost = cost;
            return this;
        }

        public TokenBucketExceptionCostCalculator build() {
            return e -> {
                if (throttlingExceptionCost != null && RetryUtils.isThrottlingException(e)) {
                    return throttlingExceptionCost;
                }

                return defaultExceptionCost;
            };
        }
    }
}
