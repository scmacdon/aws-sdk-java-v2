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

package software.amazon.awssdk.core.retry;

import com.sun.tools.javac.util.StringUtils;
import java.util.Optional;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileFileSystemSetting;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.utils.Lazy;
import software.amazon.awssdk.utils.OptionalUtils;

@SdkPublicApi
public enum RetryMode {
    LEGACY,
    STANDARD;

    private static final Lazy<RetryMode> DEFAULT_RETRY_MODE = new Lazy<>(RetryMode::fromDefaultChain);

    public static RetryMode defaultRetryMode() {
        return DEFAULT_RETRY_MODE.getValue();
    }

    public static Optional<RetryMode> fromDefaultProfileFile() {
        return fromProfileFile(ProfileFile.defaultProfileFile());
    }

    public static Optional<RetryMode> fromSystemSettings() {
        return fromString(SdkSystemSetting.AWS_RETRY_MODE.getStringValueOrThrow());
    }

    public static Optional<RetryMode> fromProfileFile(ProfileFile profileFile) {
        return profileFile.profile(ProfileFileSystemSetting.AWS_PROFILE.getStringValueOrThrow())
                          .flatMap(p -> p.property(ProfileProperty.RETRY_MODE))
                          .flatMap(RetryMode::fromString);
    }

    private static RetryMode fromDefaultChain() {
        return OptionalUtils.firstPresent(RetryMode.fromSystemSettings(),
                                          RetryMode::fromDefaultProfileFile)
                            .orElse(RetryMode.LEGACY);
    }

    private static Optional<RetryMode> fromString(String string) {
        if (string == null || string.isEmpty()) {
            return Optional.empty();
        }

        switch (StringUtils.toLowerCase(string)) {
            case "legacy":
                return Optional.of(LEGACY);
            case "standard":
                return Optional.of(STANDARD);
            default:
                throw new IllegalStateException("Unsupported retry policy mode configured: " + string);
        }
    }
}
