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

package software.amazon.awssdk.core.internal.http.pipeline.stages.utils;

import static software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting.SDK_RETRY_INFO_HEADER;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.core.Response;
import software.amazon.awssdk.core.SdkStandardLogger;
import software.amazon.awssdk.core.capacity.RequestCapacity;
import software.amazon.awssdk.core.capacity.RequestCapacityContext;
import software.amazon.awssdk.core.capacity.RequestCapacityExceededException;
import software.amazon.awssdk.core.capacity.TokenBucketRequestCapacity;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.exception.NonRetryableException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.internal.InternalCoreExecutionAttribute;
import software.amazon.awssdk.core.internal.http.HttpClientDependencies;
import software.amazon.awssdk.core.internal.http.RequestExecutionContext;
import software.amazon.awssdk.core.internal.retry.ClockSkewAdjuster;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.RetryUtils;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

@SdkInternalApi
public class RetryableStageHelper {
    private final SdkHttpFullRequest request;
    private final RequestExecutionContext context;
    private final RetryPolicy retryPolicy;
    private final RequestCapacity requestCapacity;
    private final HttpClientDependencies dependencies;

    private int attemptNumber = 0;
    private SdkHttpResponse lastResponse = null;
    private SdkException lastException = null;
    private Duration lastBackoffDelay = null;

    public RetryableStageHelper(SdkHttpFullRequest request,
                                RequestExecutionContext context,
                                HttpClientDependencies dependencies) {
        this.request = request;
        this.context = context;
        this.retryPolicy = dependencies.clientConfiguration().option(SdkClientOption.RETRY_POLICY);
        this.requestCapacity = dependencies.clientConfiguration().option(SdkClientOption.REQUEST_CAPACITY);
        this.dependencies = dependencies;
    }

    public void startingAttempt() {
        ++attemptNumber;
        context.executionAttributes().putAttribute(InternalCoreExecutionAttribute.EXECUTION_ATTEMPT, attemptNumber);
    }

    public boolean retryPolicyAllowsRetry() {
        if (!isRetry()) {
            return true;
        }

        if (lastException instanceof NonRetryableException) {
            return false;
        }

        return retryPolicy.retryCondition().shouldRetry(retryPolicyContext());
    }

    public SdkException retryPolicyDisallowedRetryException() {
        return lastException;
    }

    public Duration getBackoffDelay() {
        Duration result;
        if (!isRetry()) {
            result = Duration.ZERO;
        } else if (RetryUtils.isThrottlingException(lastException)) {
            result = retryPolicy.throttlingBackoffStrategy().computeDelayBeforeNextRetry(retryPolicyContext());
        } else {
            result = retryPolicy.backoffStrategy().computeDelayBeforeNextRetry(retryPolicyContext());
        }
        lastBackoffDelay = result;
        return result;
    }

    public void logBackingOff(Duration backoffDelay) {
        SdkStandardLogger.REQUEST_LOGGER.debug(() -> "Retryable error detected. Will retry in " +
                                                     backoffDelay.toMillis() + "ms. Request attempt number " +
                                                     attemptNumber);
    }

    public boolean requestCapacityAllowsRequest() {
        return requestCapacity.shouldAttemptRequest(requestCapacityContext());
    }

    public SdkException requestCapacityDisallowedRequestException() {
        SdkStandardLogger.REQUEST_LOGGER.debug(() -> "No request capacity exists to make this request.");
        if (lastException == null) {
            return RequestCapacityExceededException.create("Request capacity exceeded on this client.");
        } else {
            return lastException;
        }
    }

    public SdkHttpFullRequest requestToSend() {
        if (requestCapacity instanceof TokenBucketRequestCapacity) {
            TokenBucketRequestCapacity capacity = (TokenBucketRequestCapacity) requestCapacity;

            int availableRetryCapacity = capacity.currentCapacity();
            return request.toBuilder()
                          .putHeader(SDK_RETRY_INFO_HEADER,
                                     String.format("%s/%s/%s",
                                                   attemptNumber - 1,
                                                   lastBackoffDelay.toMillis(),
                                                   availableRetryCapacity >= 0 ? availableRetryCapacity : ""))
                          .build();
        }

        return request;
    }

    public void logSendingRequest() {
        SdkStandardLogger.REQUEST_LOGGER.debug(() -> (isRetry() ? "Retrying" : "Sending") + " Request: " + request);
    }

    public void adjustClockIfClockSkew(Response<?> response) {
        ClockSkewAdjuster clockSkewAdjuster = dependencies.clockSkewAdjuster();
        if (!response.isSuccess() && clockSkewAdjuster.shouldAdjust(response.exception())) {
            dependencies.updateTimeOffset(clockSkewAdjuster.getAdjustmentInSeconds(response.httpResponse()));
        }
    }

    public void attemptSucceeded() {
        requestCapacity.requestSucceeded(requestCapacityContext());
    }


    public int getAttemptNumber() {
        return attemptNumber;
    }

    public SdkException getLastException() {
        return lastException;
    }

    public void setLastException(Throwable lastException) {
        if (lastException instanceof CompletionException) {
            setLastException(lastException.getCause());
        } else if (lastException instanceof SdkException) {
            this.lastException = (SdkException) lastException;
        } else {
            this.lastException = SdkClientException.create("Unable to execute HTTP request: " + lastException.getMessage(),
                                                           lastException);
        }
    }

    public void setLastResponse(SdkHttpResponse lastResponse) {
        this.lastResponse = lastResponse;
    }

    private boolean isRetry() {
        return attemptNumber > 1;
    }

    private RetryPolicyContext retryPolicyContext() {
        return RetryPolicyContext.builder()
                                 .request(request)
                                 .originalRequest(context.originalRequest())
                                 .exception(lastException)
                                 .retriesAttempted(attemptNumber - 2)
                                 .executionAttributes(context.executionAttributes())
                                 .httpStatusCode(lastResponse == null ? null : lastResponse.statusCode())
                                 .build();
    }

    private RequestCapacityContext requestCapacityContext() {
        return RequestCapacityContext.builder()
                                     .attemptNumber(attemptNumber)
                                     .latestFailure(lastException)
                                     .executionAttributes(context.executionAttributes())
                                     .build();
    }
}
