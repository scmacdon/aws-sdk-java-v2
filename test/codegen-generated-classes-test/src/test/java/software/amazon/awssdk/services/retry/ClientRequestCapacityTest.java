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

package software.amazon.awssdk.services.retry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.capacity.RequestCapacity;
import software.amazon.awssdk.core.capacity.RequestCapacityContext;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.protocolrestjson.ProtocolRestJsonClient;
import software.amazon.awssdk.services.protocolrestjson.ProtocolRestJsonClientBuilder;

public class ClientRequestCapacityTest {
    @Rule
    public WireMockRule wireMock = new WireMockRule(0);

    @Test
    public void legacyRequestCapacityExcludesThrottlingExceptions() throws InterruptedException {
        stubThrottlingResponse();

        ExecutorService executor = Executors.newFixedThreadPool(51);
        ProtocolRestJsonClient client = clientBuilder().overrideConfiguration(o -> o.retryMode(RetryMode.LEGACY)).build();
        for (int i = 0; i < 51; ++i) {
            executor.execute(() -> assertThatThrownBy(client::allTypes).isInstanceOf(SdkException.class));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 51 requests * 4 attempts = 204 requests
        verifyRequestCount(204);
    }

    @Test
    public void standardRequestCapacityIncludesThrottlingExceptions() throws InterruptedException {
        stubThrottlingResponse();

        ExecutorService executor = Executors.newFixedThreadPool(51);
        ProtocolRestJsonClient client = clientBuilder().overrideConfiguration(o -> o.retryMode(RetryMode.STANDARD)).build();
        for (int i = 0; i < 51; ++i) {
            executor.execute(() -> assertThatThrownBy(client::allTypes).isInstanceOf(SdkException.class));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Would receive 153 without throttling (51 requests * 3 attempts = 153 requests)
        verifyRequestCount(151);
    }

    @Test
    public void customRequestCapacityOverridesConfiguredRetryMode() {
        stubThrottlingResponse();
        ProtocolRestJsonClient client = clientBuilder().overrideConfiguration(o -> o.requestCapacity(new NoRetriesCapacity())
                                                                                    .retryMode(RetryMode.LEGACY)).build();
        assertThatThrownBy(client::allTypes).isInstanceOf(SdkException.class);
        verifyRequestCount(1);
    }

    private ProtocolRestJsonClientBuilder clientBuilder() {
        return ProtocolRestJsonClient.builder()
                                     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("akid", "skid")))
                                     .region(Region.US_EAST_1)
                                     .endpointOverride(URI.create("http://localhost:" + wireMock.port()));
    }

    private void verifyRequestCount(int count) {
        verify(count, anyRequestedFor(anyUrl()));
    }

    private void stubThrottlingResponse() {
        stubFor(post(anyUrl())
                    .willReturn(aResponse().withStatus(429)));
    }

    private class NoRetriesCapacity implements RequestCapacity {
        @Override
        public boolean shouldAttemptRequest(RequestCapacityContext context) {
            return context.attemptNumber() < 2;
        }

        @Override
        public void requestSucceeded(RequestCapacityContext context) {

        }
    }
}
