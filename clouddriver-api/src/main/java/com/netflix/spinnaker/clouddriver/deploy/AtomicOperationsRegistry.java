package com.netflix.spinnaker.clouddriver.deploy;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;

import javax.annotation.Nullable;

/**
 * A registry which does a lookup of AtomicOperationConverters and DescriptionValidators based on their names and
 * cloud providers
 */
public interface AtomicOperationsRegistry {
  /**
   * @param description
   * @param cloudProvider
   * @return
   */
  AtomicOperationConverter getAtomicOperationConverter(String description, String cloudProvider);

  /**
   * @param validator
   * @param cloudProvider
   * @return
   */
  @Nullable
  DescriptionValidator getAtomicOperationDescriptionValidator(String validator, String cloudProvider);
}
