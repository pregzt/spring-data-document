/*
 * Copyright 2002-2010 the original author or authors.
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
package org.springframework.data.document.mongodb.repository;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;


/**
 * Unit test for {@link SimpleMongoEntityInformation}.
 *
 * @author Oliver Gierke
 */
public class SimpleMongoEntityInformationUnitTests {

  @Test
  public void findsIdField() throws Exception {

    SimpleMongoEntityInformation<Person, Long> isNewAware =
        new SimpleMongoEntityInformation<Person, Long>(Person.class);

    Person person = new Person();
    assertThat(isNewAware.isNew(person), is(true));
    person.id = 1L;
    assertThat(isNewAware.isNew(person), is(false));
  }


  @Test(expected = IllegalArgumentException.class)
  public void rejectsClassIfNoIdField() throws Exception {

    new SimpleMongoEntityInformation<InvalidPerson, Long>(InvalidPerson.class);
  }

  class Person {

    Long id;
  }

  class InvalidPerson {

    Long foo;
  }
}
