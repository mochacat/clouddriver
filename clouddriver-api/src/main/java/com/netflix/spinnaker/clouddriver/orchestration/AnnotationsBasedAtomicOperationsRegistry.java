package com.netflix.spinnaker.clouddriver.orchestration;

import com.google.common.base.Splitter;
import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.exceptions.CloudProviderNotFoundException;
import com.netflix.spinnaker.kork.exceptions.UserException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

// Doesn't seem like logger is being used so removing it
// @Slf4j
public class AnnotationsBasedAtomicOperationsRegistry
    extends ApplicationContextAtomicOperationsRegistry {

  @Autowired List<CloudProvider> cloudProviders;

  // definitely need tests for this method. I had to convert some groovy things to java ehh
  @Override
  public AtomicOperationConverter getAtomicOperationConverter(
      String description, String cloudProvider) {
    // Legacy naming convention which is not generic and description name is specific to cloud
    // provider
    try {
      AtomicOperationConverter converter =
          super.getAtomicOperationConverter(description, cloudProvider);
      // below is the equivalent of Groovys code below?
      //  if (converter) return converter;
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
      // below is the equivalent of Groovys code below?
      //  if (converter) return converter;
      if (cloudProvider == null) {
        throw e;
      }
    }

    // Operations can be versioned
    VersionedDescription versionedDescription = VersionedDescription.from(description);

    Class<? extends Annotation> providerAnnotationType = getCloudProviderAnnotation(cloudProvider);

    // is this equivalent to Groovy's findAll?
    List converters =
        applicationContext.getBeansWithAnnotation(providerAnnotationType).entrySet().stream()
            .filter(
                (it) -> {
                  // TODO fix .value()?
                  VersionedDescription converterVersion =
                      VersionedDescription.from(
                          it.getValue()
                              .getClass()
                              .getAnnotation(providerAnnotationType)
                              .toString());
                  return converterVersion.descriptionName == versionedDescription.descriptionName
                      && it.getValue() instanceof AtomicOperationConverter;
                })
            .collect(Collectors.toList());

    converters =
        VersionedOperationHelper.findVersionMatches(versionedDescription.version, converters);

    if (converters.isEmpty()) {
      throw new AtomicOperationConverterNotFoundException(
          "No atomic operation converter found for description '${description}' and cloud provider '${cloudProvider}'. "
              + "It is possible that either 1) the account name used for the operation is incorrect, or 2) the account name used for the operation is unhealthy/unable to communicate with ${cloudProvider}.");
    }
    if (converters.size() > 1) {
      throw new RuntimeException(
          "More than one (${converters.size()}) atomic operation converters found for description '${description}' and cloud provider "
              + "'${cloudProvider}'");
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

    // is this the Groovy equivalent of findAll?
    List validators =
        applicationContext.getBeansWithAnnotation(providerAnnotationType).entrySet().stream()
            .filter(
                it ->
                    DescriptionValidator.getValidatorName(
                                it.getValue()
                                    .getClass()
                                    .getAnnotation(providerAnnotationType)
                                    .toString())
                            == validator
                        && it.getValue() instanceof DescriptionValidator)
            .collect(Collectors.toList());

    return !validators.isEmpty() ? (DescriptionValidator) validators.get(0) : null;
  }

  protected Class<? extends Annotation> getCloudProviderAnnotation(String cloudProvider)
      throws CloudProviderNotFoundException {
    // is this findAll equivalent in Java?
    // cloudProviders.findAll { it.id == cloudProvider }
    List<CloudProvider> cloudProviderInstances =
        cloudProviders.stream()
            .filter(it -> it.getId() == cloudProvider)
            .collect(Collectors.toList());
    if (!cloudProviderInstances.isEmpty()) {
      throw new CloudProviderNotFoundException("No cloud provider named '${cloudProvider}' found");
    }
    if (cloudProviderInstances.size() > 1) {
      throw new RuntimeException(
          "More than one (${cloudProviderInstances.size()}) cloud providers found for the identifier '${cloudProvider}'");
    }
    cloudProviderInstances.get(0).getOperationAnnotationType();
    return null;
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
