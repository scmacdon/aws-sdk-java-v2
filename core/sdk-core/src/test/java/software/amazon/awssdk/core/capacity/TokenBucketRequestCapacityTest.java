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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

public class TokenBucketRequestCapacityTest {
    private static final SdkException EXCEPTION = SdkClientException.create("");
    private static final SdkException EXCEPTION_2 = SdkClientException.create("");

    @Test
    public void firstAttemptIsFree() {
        TokenBucketRequestCapacity capacity = create(3, e -> 1);
        for (int i = 0; i < 10; ++i) {
            assertThat(capacity.shouldAttemptRequest(context(1, null))).isTrue();
            assertThat(capacity.currentCapacity()).isEqualTo(3);
        }
    }

    @Test
    public void maximumTokensCannotBeExceeded() {
        TokenBucketRequestCapacity capacity = create(3, e -> 1);
        for (int i = 1; i < 10; ++i) {
            capacity.requestSucceeded(context(i, null));
            assertThat(capacity.currentCapacity()).isEqualTo(3);
        }
    }

    @Test
    public void releasingMoreCapacityThanAvailableSetsCapacityToMax() {
        ExecutionAttributes attributes = new ExecutionAttributes();

        TokenBucketRequestCapacity capacity = create(11, e -> e == EXCEPTION ? 1 : 3);
        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION, attributes))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(10);
        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION_2, attributes))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(7);
        capacity.requestSucceeded(context(2, EXCEPTION_2, attributes));
        assertThat(capacity.currentCapacity()).isEqualTo(10);
        capacity.requestSucceeded(context(2, EXCEPTION_2, attributes));
        assertThat(capacity.currentCapacity()).isEqualTo(11);
    }

    @Test
    public void nonFirstAttemptsAreNotFree() {
        TokenBucketRequestCapacity capacity = create(2, e -> 1);

        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(1);

        assertThat(capacity.shouldAttemptRequest(context(3, EXCEPTION))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(0);

        assertThat(capacity.shouldAttemptRequest(context(4, EXCEPTION))).isFalse();
        assertThat(capacity.currentCapacity()).isEqualTo(0);
    }

    @Test
    public void exceptionCostIsHonored() {
        // EXCEPTION costs 1, anything else costs 10
        TokenBucketRequestCapacity capacity = create(20, e -> e == EXCEPTION ? 1 : 10);

        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(19);

        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION_2))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(9);

        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION_2))).isFalse();
        assertThat(capacity.currentCapacity()).isEqualTo(9);

        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(8);
    }

    @Test
    public void successReleasesAcquiredCost() {
        ExecutionAttributes attributes = new ExecutionAttributes();

        TokenBucketRequestCapacity capacity = create(20, e -> 10);

        assertThat(capacity.shouldAttemptRequest(context(1, null, attributes))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(20);

        assertThat(capacity.shouldAttemptRequest(context(2, EXCEPTION, attributes))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(10);

        capacity.requestSucceeded(context(2, EXCEPTION, attributes));
        assertThat(capacity.currentCapacity()).isEqualTo(20);
    }

    @Test
    public void firstRequestSuccessReleasesOne() {
        TokenBucketRequestCapacity capacity = create(20, e -> 10);

        assertThat(capacity.shouldAttemptRequest(context(2, null))).isTrue();
        assertThat(capacity.currentCapacity()).isEqualTo(10);

        capacity.requestSucceeded(context(1, null));
        assertThat(capacity.currentCapacity()).isEqualTo(11);

        capacity.requestSucceeded(context(1, null));
        assertThat(capacity.currentCapacity()).isEqualTo(12);
    }

    @Test
    public void capacitySeemsToBeThreadSafe() throws InterruptedException {
        int bucketSize = 5;
        TokenBucketRequestCapacity capacity = create(bucketSize, e -> 1);

        AtomicInteger concurrentCalls = new AtomicInteger(0);
        AtomicBoolean failure = new AtomicBoolean(false);
        int parallelism = bucketSize * 2;
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        for (int i = 0; i < parallelism; ++i) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 1000; ++j) {
                        ExecutionAttributes attributes = new ExecutionAttributes();
                        if (capacity.shouldAttemptRequest(context(2, EXCEPTION, attributes))) {
                            int calls = concurrentCalls.addAndGet(1);
                            if (calls > bucketSize) {
                                failure.set(true);
                            }
                            Thread.sleep(1);
                            concurrentCalls.addAndGet(-1);
                            capacity.requestSucceeded(context(3, EXCEPTION, attributes));
                        }
                        else {
                            Thread.sleep(1);
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    failure.set(true);
                }
            });

            // Stagger the threads a bit.
            Thread.sleep(1);
        }

        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            Assert.fail();
        }

        assertThat(failure.get()).isFalse();
    }

    private RequestCapacityContext context(int attempt, SdkException lastException) {
        return RequestCapacityContext.builder()
                                     .executionAttributes(new ExecutionAttributes())
                                     .attemptNumber(attempt)
                                     .latestFailure(lastException)
                                     .build();
    }

    private RequestCapacityContext context(int attempt, SdkException lastException, ExecutionAttributes attributes) {
        return RequestCapacityContext.builder()
                                     .executionAttributes(attributes)
                                     .attemptNumber(attempt)
                                     .latestFailure(lastException)
                                     .build();
    }

    private TokenBucketRequestCapacity create(int size, TokenBucketExceptionCostCalculator calculator) {
        return TokenBucketRequestCapacity.builder()
                                         .tokenBucketSize(size)
                                         .exceptionCostCalculator(calculator)
                                         .build();
    }

}