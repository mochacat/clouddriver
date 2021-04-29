/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.orchestration;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.kork.annotations.Alpha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

// TODO JAVADOC

/**
 * This class is to support older naming convention for operations. Do NOT use this for new
 * operations - this is just used until older operations are migrated to new scheme
 */
@Alpha
public class ApplicationContextAtomicOperationsRegistry implements AtomicOperationsRegistry {

  @Autowired ApplicationContext applicationContext;

  // Note: I needed to make this method public, not sure if that is OK.
  // When it wasn't public this method clashed with AtomicOperationsRegistry's
  // getAtomicOperationConverter for some reason...
  // maybe because I literally copied groovy into a java file :thinking-face
  @Override
  public AtomicOperationConverter getAtomicOperationConverter(
      String description, String cloudProvider) {
    return (AtomicOperationConverter) applicationContext.getBean(description);
  }

  // Note: I needed to make this method public, not sure if that is OK.
  // When it wasn't public this method clashed with AtomicOperationsRegistry's
  // getAtomicOperationDescriptionValidator for some reason...
  // maybe because I literally copied groovy into a java file :thinking-face
  @Override
  public DescriptionValidator getAtomicOperationDescriptionValidator(
      String validator, String cloudProvider) {
    return (DescriptionValidator) applicationContext.getBean(validator);
  }
}
