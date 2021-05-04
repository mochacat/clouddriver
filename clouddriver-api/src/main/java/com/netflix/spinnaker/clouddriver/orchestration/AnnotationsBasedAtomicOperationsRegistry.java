package com.netflix.spinnaker.clouddriver.orchestration;

import com.google.common.base.Splitter;
import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.kork.exceptions.UserException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

public class AnnotationsBasedAtomicOperationsRegistry
    extends ApplicationContextAtomicOperationsRegistry {

  @Autowired List<CloudProvider> cloudProviders;

  @Override
  public AtomicOperationConverter getAtomicOperationConverter(
      String description, String cloudProvider) {
    // Legacy naming convention which is not generic and description name is specific to cloud
    // provider
    try {
      AtomicOperationConverter converter =
          super.getAtomicOperationConverter(description, cloudProvider);
      if (converter != null) {
        return converter;
      }
    } catch (NoSuchBeanDefinitionException e) {
      /**
       * If 'cloudProvider' is not specified then it means that caller was querying the bean as per
       * the old cloud provider specific name and if no bean found then we can't do anything here
       * other than throwing the NoSuchBeanDefinitionException
       *
       * <p>TO-DO: Once all the operations have been migrated as per the new naming scheme that is
       * not cloud provider specific, then make the 'description' and 'cloudProvider' arguments
       * mandatory for this method
       */
      if (cloudProvider == null) {
        throw e;
      }
    }

    // Operations can be versioned
    VersionedDescription versionedDescription = VersionedDescription.from(description);

    Class<? extends Annotation> providerAnnotationType = getCloudProviderAnnotation(cloudProvider);

    List converters =
        applicationContext.getBeansWithAnnotation(providerAnnotationType).values().stream()
            .filter(
                (it) -> {
                  Object converterType =
                      DefaultGroovyMethods.invokeMethod(
                          it.getClass().getAnnotation(providerAnnotationType),
                          "value",
                          new Object[0]);
                  VersionedDescription converterVersion =
                      VersionedDescription.from((String) converterType);
                  return converterVersion.descriptionName.equals(
                          versionedDescription.descriptionName)
                      && it instanceof AtomicOperationConverter;
                })
            .collect(Collectors.toList());

    converters =
        VersionedOperationHelper.findVersionMatches(versionedDescription.version, converters);

    if (converters.isEmpty()) {
      throw new AtomicOperationConverterNotFoundException(
          String.format(
              "No atomic operation converter found for description '%s' and cloud provider '%s'. It is possible that either 1) the account name used for the operation is incorrect, or 2) the account name used for the operation is unhealthy/unable to communicate with %s.",
              description, cloudProvider, cloudProvider));
    }
    if (converters.size() > 1) {
      throw new RuntimeException(
          String.format(
              "More than one (%s) atomic operation converters found for description '%s' and cloud provider '%s'",
              converters.size(), description, cloudProvider));
    }
    return (AtomicOperationConverter) converters.get(0);
  }

  @Override
  public DescriptionValidator getAtomicOperationDescriptionValidator(
      String validator, String cloudProvider) {
    // Legacy naming convention which is not generic and validator name is specific to cloud
    // provider
    try {
      DescriptionValidator descriptionValidator =
          super.getAtomicOperationDescriptionValidator(validator, cloudProvider);
      if (descriptionValidator != null) {
        return descriptionValidator;
      }
    } catch (NoSuchBeanDefinitionException e) {
    }

    if (cloudProvider == null) return null;

    Class<? extends Annotation> providerAnnotationType = getCloudProviderAnnotation(cloudProvider);

    List validators =
        applicationContext.getBeansWithAnnotation(providerAnnotationType).values().stream()
            .filter(
                (it) -> {
                  Object validatorType =
                      DefaultGroovyMethods.invokeMethod(
                          it.getClass().getAnnotation(providerAnnotationType),
                          "value",
                          new Object[0]);
                  return DescriptionValidator.getValidatorName((String) validatorType)
                          .equals(validator)
                      && it instanceof DescriptionValidator;
                })
            .collect(Collectors.toList());

    return !validators.isEmpty() ? (DescriptionValidator) validators.get(0) : null;
  }

  protected Class<? extends Annotation> getCloudProviderAnnotation(String cloudProvider)
      throws CloudProviderNotFoundException {
    List<CloudProvider> cloudProviderInstances =
        cloudProviders.stream()
            .filter(it -> it.getId().equals(cloudProvider))
            .collect(Collectors.toList());
    if (cloudProviderInstances.isEmpty()) {
      throw new CloudProviderNotFoundException(
          String.format("No cloud provider named %s found", cloudProvider));
    }
    if (cloudProviderInstances.size() > 1) {
      throw new RuntimeException(
          String.format(
              "More than one (%s) cloud providers found for the identifier '%s'",
              cloudProviderInstances.size(), cloudProvider));
    }
    return cloudProviderInstances.get(0).getOperationAnnotationType();
  }

  private static class VersionedDescription {

    private static final Splitter SPLITTER = Splitter.on("@");

    @Nonnull String descriptionName;
    @Nullable String version;

    VersionedDescription(String descriptionName, String version) {
      this.descriptionName = descriptionName;
      this.version = version;
    }

    static VersionedDescription from(String descriptionName) {
      if (descriptionName.contains("@")) {
        List<String> parts = SPLITTER.splitToList(descriptionName);
        if (parts.size() != 2) {
          throw new UserException(
              "Versioned descriptions must follow '{description}@{version}' format");
        }

        return new VersionedDescription(parts.get(0), parts.get(1));
      } else {
        return new VersionedDescription(descriptionName, null);
      }
    }
  }
}
