package com.netflix.spinnaker.clouddriver.orchestration;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class VersionedOperationHelper {
  static <T extends VersionedCloudProviderOperation> List<T> findVersionMatches(
      @Nullable String version, List<T> converters) {
    return converters.stream()
        .filter(it -> it.acceptsVersion(version))
        .collect(Collectors.toList());
  }
}
