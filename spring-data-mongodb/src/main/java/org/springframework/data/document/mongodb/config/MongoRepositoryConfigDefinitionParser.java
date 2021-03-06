/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.document.mongodb.config;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.data.document.mongodb.config.SimpleMongoRepositoryConfiguration.MongoRepositoryConfiguration;
import org.springframework.data.mapping.model.MappingContext;
import org.springframework.data.repository.config.AbstractRepositoryConfigDefinitionParser;
import org.w3c.dom.Element;


/**
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser} to create
 * Mongo DB repositories from classpath scanning or manual definition.
 *
 * @author Oliver Gierke
 */
public class MongoRepositoryConfigDefinitionParser
    extends
    AbstractRepositoryConfigDefinitionParser<SimpleMongoRepositoryConfiguration, MongoRepositoryConfiguration> {
  
  private static final String MAPPING_CONTEXT_DEFAULT = MongoMappingConverterParser.MAPPING_CONTEXT;
  
  /*
  * (non-Javadoc)
  *
  * @see org.springframework.data.repository.config.
  * AbstractRepositoryConfigDefinitionParser
  * #getGlobalRepositoryConfigInformation(org.w3c.dom.Element)
  */
  @Override
  protected SimpleMongoRepositoryConfiguration getGlobalRepositoryConfigInformation(
      Element element) {

    return new SimpleMongoRepositoryConfiguration(element);
  }


  /*
   * (non-Javadoc)
   * @see org.springframework.data.repository.config.AbstractRepositoryConfigDefinitionParser#postProcessBeanDefinition(org.springframework.data.repository.config.SingleRepositoryConfigInformation, org.springframework.beans.factory.support.BeanDefinitionBuilder, org.springframework.beans.factory.support.BeanDefinitionRegistry, java.lang.Object)
   */
  @Override
  protected void postProcessBeanDefinition(
      MongoRepositoryConfiguration context,
      BeanDefinitionBuilder builder, BeanDefinitionRegistry registry, Object beanSource) {

    builder.addPropertyReference("template", context.getMongoTemplateRef());
    
    String mappingContextRef = getMappingContextReference(context, registry);
    if (mappingContextRef != null) {
      builder.addPropertyReference("mappingContext", mappingContextRef);
    }
  }

  /**
   * Returns the bean name of a {@link MappingContext} to be wired. Will inspect the namespace attribute first and if no
   * config is found in that place it will try to lookup the default one. Will return {@literal null} if neither one is
   * available.
   * 
   * @param config
   * @param registry
   * @return
   */
  private String getMappingContextReference(MongoRepositoryConfiguration config, BeanDefinitionRegistry registry) {
    
    String contextRef = config.getMappingContextRef();
    
    if (contextRef != null) {
      return contextRef;
    }
    
    try {
      registry.getBeanDefinition(MAPPING_CONTEXT_DEFAULT);
      return MAPPING_CONTEXT_DEFAULT;
    } catch(NoSuchBeanDefinitionException e) {
      return null;
    }
  }
}
