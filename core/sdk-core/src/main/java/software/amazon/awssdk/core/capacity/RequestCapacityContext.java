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
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.utils.Validate;

@SdkPublicApi
public final class RequestCapacityContext {
    private final Integer attemptNumber;
    private final SdkException latestFailure;
    private final ExecutionAttributes executionAttributes;

    private RequestCapacityContext(Builder builder) {
        this.attemptNumber = Validate.notNull(builder.attemptNumber, "attemptNumber");
        this.latestFailure = builder.latestFailure;
        this.executionAttributes = Validate.notNull(builder.executionAttributes, "executionAttributes");
    }

    public static Builder builder() {
        return new Builder();
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public SdkException latestFailure() {
        return latestFailure;
    }

    public ExecutionAttributes executionAttributes() {
        return executionAttributes;
    }

    public static class Builder {
        private Integer attemptNumber;
        private SdkException latestFailure;
        private ExecutionAttributes executionAttributes;

        private Builder() {}

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public Builder latestFailure(SdkException latestFailure) {
            this.latestFailure = latestFailure;
            return this;
        }

        public Builder executionAttributes(ExecutionAttributes executionAttributes) {
            this.executionAttributes = executionAttributes;
            return this;
        }

        public RequestCapacityContext build() {
            return new RequestCapacityContext(this);
        }
    }
}
