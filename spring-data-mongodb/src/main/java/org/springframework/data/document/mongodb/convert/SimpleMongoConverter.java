/*
 * Copyright 2010-2011 the original author or authors.
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
package org.springframework.data.document.mongodb.convert;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.CodeWScope;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.support.ConversionServiceFactory;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.document.mongodb.MongoPropertyDescriptors.MongoPropertyDescriptor;
import org.springframework.util.Assert;
import org.springframework.util.comparator.CompoundComparator;

/**
 * Basic {@link MongoConverter} implementation to convert between domain classes and {@link DBObject}s.
 * 
 * @author Mark Pollack
 * @author Thomas Risberg
 * @author Oliver Gierke
 */
public class SimpleMongoConverter implements MongoConverter {

  private static final Log LOG = LogFactory.getLog(SimpleMongoConverter.class);
  @SuppressWarnings("unchecked")
  private static final List<Class<?>> MONGO_TYPES = Arrays.asList(Number.class, Date.class, String.class, DBObject.class);
  private static final Set<String> SIMPLE_TYPES;

  static {
    Set<String> basics = new HashSet<String>();
    basics.add(boolean.class.getName());
    basics.add(long.class.getName());
    basics.add(short.class.getName());
    basics.add(int.class.getName());
    basics.add(byte.class.getName());
    basics.add(float.class.getName());
    basics.add(double.class.getName());
    basics.add(char.class.getName());
    basics.add(Boolean.class.getName());
    basics.add(Long.class.getName());
    basics.add(Short.class.getName());
    basics.add(Integer.class.getName());
    basics.add(Byte.class.getName());
    basics.add(Float.class.getName());
    basics.add(Double.class.getName());
    basics.add(Character.class.getName());
    basics.add(String.class.getName());
    basics.add(java.util.Date.class.getName());
    // basics.add(Time.class.getName());
    // basics.add(Timestamp.class.getName());
    // basics.add(java.sql.Date.class.getName());
    // basics.add(BigDecimal.class.getName());
    // basics.add(BigInteger.class.getName());
    basics.add(Locale.class.getName());
    // basics.add(Calendar.class.getName());
    // basics.add(GregorianCalendar.class.getName());
    // basics.add(java.util.Currency.class.getName());
    // basics.add(TimeZone.class.getName());
    // basics.add(Object.class.getName());
    basics.add(Class.class.getName());
    // basics.add(byte[].class.getName());
    // basics.add(Byte[].class.getName());
    // basics.add(char[].class.getName());
    // basics.add(Character[].class.getName());
    // basics.add(Blob.class.getName());
    // basics.add(Clob.class.getName());
    // basics.add(Serializable.class.getName());
    // basics.add(URI.class.getName());
    // basics.add(URL.class.getName());
    basics.add(DBRef.class.getName());
    basics.add(Pattern.class.getName());
    basics.add(CodeWScope.class.getName());
    basics.add(ObjectId.class.getName());
    // TODO check on enums..
    basics.add(Enum.class.getName());
    SIMPLE_TYPES = Collections.unmodifiableSet(basics);
  }

  private final GenericConversionService conversionService;

  /**
   * Creates a {@link SimpleMongoConverter}.
   */
  public SimpleMongoConverter() {
    this.conversionService = ConversionServiceFactory.createDefaultConversionService();
    this.conversionService.removeConvertible(Object.class, String.class);
    initializeConverters();
  }

  /**
   * Initializes additional converters that handle {@link ObjectId} conversion. Will register converters for supported
   * id types if none are registered for those conversion already. {@link GenericConversionService} is configured.
   */
  protected void initializeConverters() {

    conversionService.addConverter(ObjectIdToStringConverter.INSTANCE);
    conversionService.addConverter(StringToObjectIdConverter.INSTANCE);
    conversionService.addConverter(ObjectIdToBigIntegerConverter.INSTANCE);
    conversionService.addConverter(BigIntegerToIdConverter.INSTANCE);
  }

  /**
   * Sets custom {@link Converter} or {@link ConverterFactory} instances to be used.
   * 
   * @param converters
   */
  public void setConverters(Set<?> converters) {
    for (Object converter : converters) {
      boolean added = false;
      if (converter instanceof Converter) {
        this.conversionService.addConverter((Converter<?, ?>) converter);
        added = true;
      }
      if (converter instanceof ConverterFactory) {
        this.conversionService.addConverterFactory((ConverterFactory<?, ?>) converter);
        added = true;
      }
      if (!added) {
        throw new IllegalArgumentException("Given set contains element that is neither Converter nor ConverterFactory!");
      }
    }
  }

  /*
    * (non-Javadoc)
    *
    * @see org.springframework.data.document.mongodb.MongoWriter#write(java.lang.Object, com.mongodb.DBObject)
    */
  @SuppressWarnings("rawtypes")
  public void write(Object obj, DBObject dbo) {

    MongoBeanWrapper beanWrapper = createWrapper(obj, false);
    for (MongoPropertyDescriptor descriptor : beanWrapper.getDescriptors()) {
      if (descriptor.isMappable()) {
        Object value = beanWrapper.getValue(descriptor);

        if (value == null) {
          continue;
        }

        String keyToUse = descriptor.getKeyToMap();
        // TODO validate Enums...
        if (descriptor.isEnum()) {
          writeValue(dbo, keyToUse, ((Enum) value).name());
        } else if (descriptor.isIdProperty() && descriptor.isOfIdType()) {
          if (value instanceof String && ObjectId.isValid((String) value)) {
            try {
              writeValue(dbo, keyToUse, conversionService.convert(value, ObjectId.class));
            } catch (ConversionFailedException iae) {
              LOG.warn("Unable to convert the String " + value + " to an ObjectId");
              writeValue(dbo, keyToUse, value);
            }
          } else {
            // we can't convert this id - use as is
            writeValue(dbo, keyToUse, value);
          }
        } else {
          writeValue(dbo, keyToUse, value);
        }
      } else {
        if (!"class".equals(descriptor.getName())) {
          LOG.debug("Skipping property " + descriptor.getName() + " as it's not a mappable one.");
        }
      }
    }
  }

  /**
   * Writes the given value to the given {@link DBObject}. Will skip {@literal null} values.
   * 
   * @param dbo
   * @param keyToUse
   * @param value
   */
  private void writeValue(DBObject dbo, String keyToUse, Object value) {

    if (!isSimpleType(value.getClass())) {
      writeCompoundValue(dbo, keyToUse, value);
    } else {
      dbo.put(keyToUse, value);
    }
  }

  /**
   * Writes the given {@link CompoundComparator} value to the given {@link DBObject}.
   *
   * @param dbo
   * @param keyToUse
   * @param value
   */
  @SuppressWarnings("unchecked")
  private void writeCompoundValue(DBObject dbo, String keyToUse, Object value) {
    if (value instanceof Map) {
      writeMap(dbo, keyToUse, (Map<String, Object>) value);
      return;
    }
    if (value instanceof Collection) {
      // Should write a collection!
      writeArray(dbo, keyToUse, ((Collection<Object>) value).toArray());
      return;
    }
    if (value instanceof Object[]) {
      // Should write an array!
      writeArray(dbo, keyToUse, (Object[]) value);
      return;
    }
    
    Class<?> customTargetType = getCustomTargetType(value);
    if (customTargetType != null) {
      dbo.put(keyToUse, conversionService.convert(value, customTargetType));
      return;
    }
    
    DBObject nestedDbo = new BasicDBObject();
    write(value, nestedDbo);
    dbo.put(keyToUse, nestedDbo);
  }
  
  /**
   * Returns whether the {@link ConversionService} has a custom {@link Converter} registered that can convert the given
   * object into one of the types supported by MongoDB.
   * 
   * @param obj
   * @return
   */
  private Class<?> getCustomTargetType(Object obj) {
    
    for (Class<?> mongoType : MONGO_TYPES) {
      if (conversionService.canConvert(obj.getClass(), mongoType)) {
        return mongoType;
      }
    }
    return null;
  }

  /**
   * Writes the given {@link Map} to the given {@link DBObject}.
   * 
   * @param dbo
   * @param mapKey
   * @param map
   */
  protected void writeMap(DBObject dbo, String mapKey, Map<String, Object> map) {
    // TODO support non-string based keys as long as there is a Spring Converter obj->string and (optionally)
    // string->obj
    DBObject dboToPopulate = null;

    // TODO - Does that make sense? If we create a new object here it's content will never make it out of this
    // method
    if (mapKey != null) {
      dboToPopulate = new BasicDBObject();
    } else {
      dboToPopulate = dbo;
    }
    if (map != null) {
      for (Entry<String, Object> entry : map.entrySet()) {

        Object entryValue = entry.getValue();
        String entryKey = entry.getKey();

        if (!isSimpleType(entryValue.getClass())) {
          writeCompoundValue(dboToPopulate, entryKey, entryValue);
        } else {
          dboToPopulate.put(entryKey, entryValue);
        }
      }
      dbo.put(mapKey, dboToPopulate);
    }
  }

  /**
   * Writes the given array to the given {@link DBObject}.
   *
   * @param dbo
   * @param keyToUse
   * @param array
   */
  protected void writeArray(DBObject dbo, String keyToUse, Object[] array) {
    // TODO
    Object[] dboValues;
    if (array != null) {
      dboValues = new Object[array.length];
      int i = 0;
      for (Object o : array) {
        if (!isSimpleType(o.getClass())) {
          DBObject dboValue = new BasicDBObject();
          write(o, dboValue);
          dboValues[i] = dboValue;
        } else {
          dboValues[i] = o;
        }
        i++;
      }
      dbo.put(keyToUse, dboValues);
    }
  }

  /*
    * (non-Javadoc)
    *
    * @see org.springframework.data.document.mongodb.MongoReader#read(java.lang.Class, com.mongodb.DBObject)
    */
  public <S> S read(Class<S> clazz, DBObject source) {

    if (source == null) {
      return null;
    }

    Assert.notNull(clazz, "Mapped class was not specified");
    S target = BeanUtils.instantiateClass(clazz);
    MongoBeanWrapper bw = new MongoBeanWrapper(target, conversionService, true);

    for (MongoPropertyDescriptor descriptor : bw.getDescriptors()) {
      String keyToUse = descriptor.getKeyToMap();
      if (source.containsField(keyToUse)) {
        if (descriptor.isMappable()) {
          Object value = source.get(keyToUse);
          if (!isSimpleType(value.getClass())) {
            if (value instanceof Object[]) {
              bw.setValue(descriptor, readCollection(descriptor, Arrays.asList((Object[]) value))
                  .toArray());
            } else if (value instanceof BasicDBList) {
              bw.setValue(descriptor, readCollection(descriptor, (BasicDBList) value));
            } else if (value instanceof DBObject) {
              bw.setValue(descriptor, readCompoundValue(descriptor, (DBObject) value));
            } else {
              LOG.warn("Unable to map compound DBObject field " + keyToUse + " to property "
                  + descriptor.getName()
                  + ".  The field value should have been a 'DBObject.class' but was "
                  + value.getClass().getName());
            }
          } else {
            bw.setValue(descriptor, value);
          }
        } else {
          LOG.warn("Unable to map DBObject field " + keyToUse + " to property " + descriptor.getName()
              + ".  Skipping.");
        }
      }
    }

    return target;
  }

  /**
   * Reads the given collection values (that are {@link DBObject}s potentially) into a {@link Collection} of domain
   * objects.
   *
   * @param descriptor
   * @param values
   * @return
   */
  private Collection<Object> readCollection(MongoPropertyDescriptor descriptor, Collection<?> values) {

    Class<?> targetCollectionType = descriptor.getPropertyType();
    boolean targetIsArray = targetCollectionType.isArray();

    @SuppressWarnings("unchecked")
    Collection<Object> result = targetIsArray ? new ArrayList<Object>(values.size()) : CollectionFactory
        .createCollection(targetCollectionType, values.size());

    for (Object o : values) {
      if (o instanceof DBObject) {
        Class<?> type;
        if (targetIsArray) {
          type = targetCollectionType.getComponentType();
        } else {
          type = getGenericParameters(descriptor.getTypeToSet()).get(0);
        }
        result.add(read(type, (DBObject) o));
      } else {
        result.add(o);
      }
    }

    return result;
  }

  /**
   * Reads a compound value from the given {@link DBObject} for the given property.
   *
   * @param pd
   * @param dbo
   * @return
   */
  private Object readCompoundValue(MongoPropertyDescriptor pd, DBObject dbo) {

    Assert.isTrue(!pd.isCollection(), "Collections not supported!");

    if (pd.isMap()) {
      return readMap(pd, dbo, getGenericParameters(pd.getTypeToSet()).get(1));
    } else {
      return read(pd.getPropertyType(), dbo);
    }
  }

  /**
   * Create a {@link Map} instance. Will return a {@link HashMap} by default. Subclasses might want to override this
   * method to use a custom {@link Map} implementation.
   *
   * @return
   */
  protected Map<String, Object> createMap() {
    return new HashMap<String, Object>();
  }

  /**
   * Reads every key/value pair from the {@link DBObject} into a {@link Map} instance.
   *
   * @param pd
   * @param dbo
   * @param targetType
   * @return
   */
  protected Map<?, ?> readMap(MongoPropertyDescriptor pd, DBObject dbo, Class<?> targetType) {
    Map<String, Object> map = createMap();
    for (String key : dbo.keySet()) {
      Object value = dbo.get(key);
      if (!isSimpleType(value.getClass())) {
        map.put(key, read(targetType, (DBObject) value));
        // Can do some reflection tricks here -
        // throw new RuntimeException("User types not supported yet as values for Maps");
      } else {
        map.put(key, conversionService.convert(value, targetType));
      }
    }
    return map;
  }

  protected static boolean isSimpleType(Class<?> propertyType) {
    if (propertyType == null) {
      return false;
    }
    if (propertyType.isArray()) {
      return isSimpleType(propertyType.getComponentType());
    }
    return SIMPLE_TYPES.contains(propertyType.getName());
  }

  /**
   * Callback to allow customizing creation of a {@link MongoBeanWrapper}.
   *
   * @param target      the target object to wrap
   * @param fieldAccess whether to use field access or property access
   * @return
   */
  protected MongoBeanWrapper createWrapper(Object target, boolean fieldAccess) {

    return new MongoBeanWrapper(target, conversionService, fieldAccess);
  }

  public List<Class<?>> getGenericParameters(Type genericParameterType) {

    List<Class<?>> actualGenericParameterTypes = new ArrayList<Class<?>>();

    if (genericParameterType instanceof ParameterizedType) {
      ParameterizedType aType = (ParameterizedType) genericParameterType;
      Type[] parameterArgTypes = aType.getActualTypeArguments();
      for (Type parameterArgType : parameterArgTypes) {
        if (parameterArgType instanceof GenericArrayType) {
          Class<?> arrayType = (Class<?>) ((GenericArrayType) parameterArgType).getGenericComponentType();
          actualGenericParameterTypes.add(Array.newInstance(arrayType, 0).getClass());
        } else {
          if (parameterArgType instanceof ParameterizedType) {
            ParameterizedType paramTypeArgs = (ParameterizedType) parameterArgType;
            actualGenericParameterTypes.add((Class<?>) paramTypeArgs.getRawType());
          } else {
            if (parameterArgType instanceof TypeVariable) {
              throw new RuntimeException("Can not map " + ((TypeVariable<?>) parameterArgType).getName());
            } else {
              if (parameterArgType instanceof Class) {
                actualGenericParameterTypes.add((Class<?>) parameterArgType);
              } else {
                throw new RuntimeException("Can not map " + parameterArgType);
              }
            }
          }
        }
      }
    }

    return actualGenericParameterTypes;
  }

  /* (non-Javadoc)
    * @see org.springframework.data.document.mongodb.convert.MongoConverter#convertObjectId(org.bson.types.ObjectId, java.lang.Class)
    */
  public <T> T convertObjectId(ObjectId id, Class<T> targetType) {
    return conversionService.convert(id, targetType);
  }

  /* (non-Javadoc)
    * @see org.springframework.data.document.mongodb.convert.MongoConverter#convertObjectId(java.lang.Object)
    */
  public ObjectId convertObjectId(Object id) {
    return conversionService.convert(id, ObjectId.class);
  }

  /**
   * Simple singleton to convert {@link ObjectId}s to their {@link String} representation.
   * 
   * @author Oliver Gierke
   */
  public static enum ObjectIdToStringConverter implements Converter<ObjectId, String> {
    INSTANCE;

    public String convert(ObjectId id) {
      return id.toString();
    }
  }

  /**
   * Simple singleton to convert {@link String}s to their {@link ObjectId} representation.
   *
   * @author Oliver Gierke
   */
  public static enum StringToObjectIdConverter implements Converter<String, ObjectId> {
    INSTANCE;

    public ObjectId convert(String source) {
      return new ObjectId(source);
    }
  }

  /**
   * Simple singleton to convert {@link ObjectId}s to their {@link BigInteger} representation.
   *
   * @author Oliver Gierke
   */
  public static enum ObjectIdToBigIntegerConverter implements Converter<ObjectId, BigInteger> {
    INSTANCE;

    public BigInteger convert(ObjectId source) {
      return new BigInteger(source.toString(), 16);
    }
  }

  /**
   * Simple singleton to convert {@link BigInteger}s to their {@link ObjectId} representation.
   *
   * @author Oliver Gierke
   */
  public static enum BigIntegerToIdConverter implements Converter<BigInteger, ObjectId> {
    INSTANCE;

    public ObjectId convert(BigInteger source) {
      return new ObjectId(source.toString(16));
    }
  }
}
