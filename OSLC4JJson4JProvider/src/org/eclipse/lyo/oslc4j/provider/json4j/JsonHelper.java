/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *
 *     Russell Boykin       - initial API and implementation
 *     Alberto Giammaria    - initial API and implementation
 *     Chris Peters         - initial API and implementation
 *     Gianluca Bernardini  - initial API and implementation
 *******************************************************************************/
package org.eclipse.lyo.oslc4j.provider.json4j;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractSequentialList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespaceDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.annotation.OslcSchema;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreInvalidPropertyDefinitionException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMissingNamespaceDeclarationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMissingNamespacePrefixException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMissingSetMethodException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreRelativeURIException;
import org.eclipse.lyo.oslc4j.core.model.IResource;
import org.eclipse.lyo.oslc4j.core.model.InheritedMethodAnnotationHelper;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.TypeFactory;

final class JsonHelper
{
    private static final String JSON_PROPERTY_DELIMITER            = ":";
    private static final String JSON_PROPERTY_PREFIXES             = "prefixes";
    private static final String JSON_PROPERTY_SUFFIX_ABOUT         = "about";
    private static final String JSON_PROPERTY_SUFFIX_MEMBER        = "member";
    private static final String JSON_PROPERTY_SUFFIX_RESOURCE      = "resource";
    private static final String JSON_PROPERTY_SUFFIX_RESPONSE_INFO = "responseInfo";
    private static final String JSON_PROPERTY_SUFFIX_RESULTS       = "results";
    private static final String JSON_PROPERTY_SUFFIX_TOTAL_COUNT   = "totalCount";
    private static final String JSON_PROPERTY_SUFFIX_TYPE          = "type";

    private static final String RDF_ABOUT_URI = OslcConstants.RDF_NAMESPACE + JSON_PROPERTY_SUFFIX_ABOUT;
    private static final String RDF_TYPE_URI  = OslcConstants.RDF_NAMESPACE + JSON_PROPERTY_SUFFIX_TYPE;

    private static final String METHOD_NAME_START_GET = "get";
    private static final String METHOD_NAME_START_IS  = "is";
    private static final String METHOD_NAME_START_SET = "set";

    private static final int METHOD_NAME_START_GET_LENGTH = METHOD_NAME_START_GET.length();
    private static final int METHOD_NAME_START_IS_LENGTH  = METHOD_NAME_START_IS.length();

    private static final Logger logger = Logger.getLogger(JsonHelper.class.getName());

    private JsonHelper()
    {
        super();
    }

    public static JSONObject createJSON(final String   descriptionAbout,
                                        final String   responseInfoAbout,
                                        final Object[] objects)
           throws DatatypeConfigurationException,
                  IllegalAccessException,
                  IllegalArgumentException,
                  InvocationTargetException,
                  JSONException,
                  OslcCoreApplicationException
    {
        final JSONObject resultJSONObject = new JSONObject();

        final Map<String, String> namespaceMappings        = new TreeMap<String, String>();
        final Map<String, String> reverseNamespaceMappings = new HashMap<String, String>();

        if (descriptionAbout != null)
        {
            final JSONArray jsonArray = new JSONArray();

            for (final Object object : objects)
            {
                final JSONObject jsonObject = handleSingleResource(object,
                                                                   new JSONObject(),
                                                                   namespaceMappings,
                                                                   reverseNamespaceMappings);

                if (jsonObject != null)
                {
                    jsonArray.add(jsonObject);
                }
            }

            // Ensure we have an rdf prefix
            final String rdfPrefix = ensureNamespacePrefix(OslcConstants.RDF_NAMESPACE_PREFIX,
                                                           OslcConstants.RDF_NAMESPACE,
                                                           namespaceMappings,
                                                           reverseNamespaceMappings);

            // Ensure we have an rdfs prefix
            final String rdfsPrefix = ensureNamespacePrefix(OslcConstants.RDFS_NAMESPACE_PREFIX,
                                                            OslcConstants.RDFS_NAMESPACE,
                                                            namespaceMappings,
                                                            reverseNamespaceMappings);

            resultJSONObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_ABOUT,
                                 descriptionAbout);

            resultJSONObject.put(rdfsPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_MEMBER,
                                 jsonArray);

            if (responseInfoAbout != null)
            {
                // Ensure we have an oslc prefix
                final String oslcPrefix = ensureNamespacePrefix(OslcConstants.OSLC_CORE_NAMESPACE_PREFIX,
                                                                OslcConstants.OSLC_CORE_NAMESPACE,
                                                                namespaceMappings,
                                                                reverseNamespaceMappings);

                final JSONObject responseInfoJSONObject = new JSONObject();

                responseInfoJSONObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_ABOUT,
                                           responseInfoAbout);

                responseInfoJSONObject.put(oslcPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_TOTAL_COUNT,
                                           objects.length);

                final JSONArray responseInfoTypesJSONArray = new JSONArray();

                final JSONObject responseInfoTypeJSONObject = new JSONObject();

                responseInfoTypeJSONObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_RESOURCE,
                                               OslcConstants.TYPE_RESPONSE_INFO);

                responseInfoTypesJSONArray.add(responseInfoTypeJSONObject);

                responseInfoJSONObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_TYPE,
                                           responseInfoTypesJSONArray);

                resultJSONObject.put(oslcPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_RESPONSE_INFO,
                                     responseInfoJSONObject);
            }
        }
        else if (objects.length == 1)
        {
            handleSingleResource(objects[0],
                                 resultJSONObject,
                                 namespaceMappings,
                                 reverseNamespaceMappings);
        }

        // Set the namespace prefixes
        final JSONObject namespaces = new JSONObject();
        for (final Map.Entry<String, String> namespaceMapping : namespaceMappings.entrySet())
        {
            namespaces.put(namespaceMapping.getKey(),
                           namespaceMapping.getValue());
        }

        if (namespaces.size() > 0)
        {
            resultJSONObject.put(JSON_PROPERTY_PREFIXES,
                                 namespaces);
        }

        return resultJSONObject;
    }

    public static Object[] fromJSON(final JSONObject jsonObject,
                                    final Class<?>   beanClass)
           throws DatatypeConfigurationException,
                  IllegalAccessException,
                  IllegalArgumentException,
                  InstantiationException,
                  InvocationTargetException,
                  OslcCoreApplicationException,
                  URISyntaxException
    {
        final List<Object>        beans                    = new ArrayList<Object>();
        final Map<String, String> namespaceMappings        = new HashMap<String, String>();
        final Map<String, String> reverseNamespaceMappings = new HashMap<String, String>();

        // First read the prefixes and set up maps so we can create full property definition values later
        final Object prefixes = jsonObject.opt(JSON_PROPERTY_PREFIXES);

        if (prefixes instanceof JSONObject)
        {
            final JSONObject prefixesJSONObject = (JSONObject) prefixes;

            @SuppressWarnings({"unchecked", "cast"})
            final Set<Map.Entry<String, Object>> prefixesEntrySet = (Set<Map.Entry<String, Object>>)  prefixesJSONObject.entrySet();
            for (final Map.Entry<String, Object> prefixEntry : prefixesEntrySet)
            {
                final String prefix    = prefixEntry.getKey();
                final Object namespace = prefixEntry.getValue();

                if (namespace instanceof String)
                {
                    namespaceMappings.put(prefix,
                                          namespace.toString());

                    reverseNamespaceMappings.put(namespace.toString(),
                                                 prefix.toString());
                }
            }
        }

        // We have to know the reverse mapping for the rdf namespace
        final String rdfPrefix = reverseNamespaceMappings.get(OslcConstants.RDF_NAMESPACE);

        if (rdfPrefix == null)
        {
            throw new OslcCoreMissingNamespaceDeclarationException(OslcConstants.RDF_NAMESPACE);
        }

        final Map<Class<?>, Map<String, Method>> classPropertyDefinitionsToSetMethods = new HashMap<Class<?>, Map<String, Method>>();

        JSONArray jsonArray = null;

        // Look for rdfs:member
        final String rdfsPrefix = reverseNamespaceMappings.get(OslcConstants.RDFS_NAMESPACE);

        if (rdfsPrefix != null)
        {
            final Object members = jsonObject.opt(rdfsPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_MEMBER);

            if (members instanceof JSONArray)
            {
                jsonArray = (JSONArray) members;
            }
        }

        if (jsonArray == null)
        {
            // Look for oslc:results.  Seen in ChangeManagement.
            final String oslcPrefix = reverseNamespaceMappings.get(OslcConstants.OSLC_CORE_NAMESPACE);

            if (oslcPrefix != null)
            {
                final Object results = jsonObject.opt(oslcPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_RESULTS);

                if (results instanceof JSONArray)
                {
                    jsonArray = (JSONArray) results;
                }
            }
        }

        if (jsonArray != null)
        {
            for (final Object object : jsonArray)
            {
                if (object instanceof JSONObject)
                {
                    final JSONObject resourceJSONObject = (JSONObject) object;

                    final Object bean = beanClass.newInstance();

                    fromJSON(rdfPrefix,
                             namespaceMappings,
                             classPropertyDefinitionsToSetMethods,
                             resourceJSONObject,
                             beanClass,
                             bean);

                    beans.add(bean);
                }
            }
        }
        else
        {
            final Object bean = beanClass.newInstance();

            fromJSON(rdfPrefix,
                     namespaceMappings,
                     classPropertyDefinitionsToSetMethods,
                     jsonObject,
                     beanClass,
                     bean);

            beans.add(bean);
        }

        return beans.toArray((Object[]) Array.newInstance(beanClass,
                                                          beans.size()));
    }

    private static void buildAttributeResource(final Map<String, String>    namespaceMappings,
                                               final Map<String, String>    reverseNamespaceMappings,
                                               final Class<?>               resourceClass,
                                               final Method                 method,
                                               final OslcPropertyDefinition propertyDefinitionAnnotation,
                                               final JSONObject             jsonObject,
                                               final Object                 value)
            throws DatatypeConfigurationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InvocationTargetException,
                   JSONException,
                   OslcCoreApplicationException
    {
        final String propertyDefinition = propertyDefinitionAnnotation.value();

        String name;
        final OslcName nameAnnotation = InheritedMethodAnnotationHelper.getAnnotation(method,
                                                                                      OslcName.class);

        if (nameAnnotation != null)
        {
            name = nameAnnotation.value();
        }
        else
        {
            name = getDefaultPropertyName(method);
        }

        if (!propertyDefinition.endsWith(name))
        {
            throw new OslcCoreInvalidPropertyDefinitionException(resourceClass,
                                                                 method,
                                                                 propertyDefinitionAnnotation);
        }

        final Object localResourceValue;

        final Class<?> returnType = method.getReturnType();

        if (returnType.isArray())
        {
            final JSONArray jsonArray = new JSONArray();

            // We cannot cast to Object[] in case this is an array of primitives.  We will use Array reflection instead.
            // Strange case about primitive arrays:  they cannot be cast to Object[], but retrieving their individual elements
            // does not return primitives, but the primitive object wrapping counterparts like Integer, Byte, Double, etc.
            final int length = Array.getLength(value);
            for (int index = 0;
                 index < length;
                 index++)
            {
                final Object object = Array.get(value,
                                                index);

                final Object localResource = handleLocalResource(namespaceMappings,
                                                                 reverseNamespaceMappings,
                                                                 resourceClass,
                                                                 method,
                                                                 object);
                if (localResource != null)
                {
                    jsonArray.add(localResource);
                }
            }

            if (jsonArray.size() > 0)
            {
                localResourceValue = jsonArray;
            }
            else
            {
                localResourceValue = null;
            }
        }
        else if (Collection.class.isAssignableFrom(returnType))
        {
            final JSONArray jsonArray = new JSONArray();

            @SuppressWarnings("unchecked")
            final Collection<Object> collection = (Collection<Object>) value;

            for (final Object object : collection)
            {
                final Object localResource = handleLocalResource(namespaceMappings,
                                                                 reverseNamespaceMappings,
                                                                 resourceClass,
                                                                 method,
                                                                 object);
                if (localResource != null)
                {
                    jsonArray.add(localResource);
                }
            }

            if (jsonArray.size() > 0)
            {
                localResourceValue = jsonArray;
            }
            else
            {
                localResourceValue = null;
            }
        }
        else
        {
            localResourceValue = handleLocalResource(namespaceMappings,
                                                     reverseNamespaceMappings,
                                                     resourceClass,
                                                     method,
                                                     value);
        }

        if (localResourceValue != null)
        {
            final String namespace = propertyDefinition.substring(0,
                                                                  propertyDefinition.length() - name.length());

            final String prefix = reverseNamespaceMappings.get(namespace);

            if (prefix == null)
            {
                throw new OslcCoreMissingNamespaceDeclarationException(namespace);
            }

            jsonObject.put(prefix + JSON_PROPERTY_DELIMITER + name,
                           localResourceValue);
        }
    }

    private static void buildResource(final Map<String, String> namespaceMappings,
                                      final Map<String, String> reverseNamespaceMappings,
                                      final Object              object,
                                      final Class<?>            objectClass,
                                      final JSONObject          jsonObject)
            throws DatatypeConfigurationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InvocationTargetException,
                   JSONException,
                   OslcCoreApplicationException
    {
        for (final Method method : objectClass.getMethods())
        {
            if (method.getParameterTypes().length == 0)
            {
                final String methodName = method.getName();
                if (((methodName.startsWith(METHOD_NAME_START_GET)) &&
                     (methodName.length() > METHOD_NAME_START_GET_LENGTH)) ||
                    ((methodName.startsWith(METHOD_NAME_START_IS)) &&
                     (methodName.length() > METHOD_NAME_START_IS_LENGTH)))
                {
                    final OslcPropertyDefinition oslcPropertyDefinitionAnnotation = InheritedMethodAnnotationHelper.getAnnotation(method,
                                                                                                                                  OslcPropertyDefinition.class);

                    if (oslcPropertyDefinitionAnnotation != null)
                    {
                        final Object value = method.invoke(object);

                        if (value != null)
                        {
                            buildAttributeResource(namespaceMappings,
                                                   reverseNamespaceMappings,
                                                   objectClass,
                                                   method,
                                                   oslcPropertyDefinitionAnnotation,
                                                   jsonObject,
                                                   value);
                        }
                    }
                }
            }
        }

        // For JSON, we have to save array of rdf:type

        // Ensure we have an rdf prefix
        final String rdfPrefix = ensureNamespacePrefix(OslcConstants.RDF_NAMESPACE_PREFIX,
                                                       OslcConstants.RDF_NAMESPACE,
                                                       namespaceMappings,
                                                       reverseNamespaceMappings);

        if (rdfPrefix != null)
        {
            final String qualifiedName = TypeFactory.getQualifiedName(objectClass);

            final JSONArray rdfTypesJSONArray = new JSONArray();

            final JSONObject rdfTypeJSONObject = new JSONObject();

            rdfTypeJSONObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_RESOURCE,
                                  qualifiedName);

            rdfTypesJSONArray.add(rdfTypeJSONObject);

            jsonObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_TYPE,
                           rdfTypesJSONArray);
        }
    }

    private static String getDefaultPropertyName(final Method method)
    {
        final String methodName    = method.getName();
        final int    startingIndex = methodName.startsWith(METHOD_NAME_START_GET) ? METHOD_NAME_START_GET_LENGTH : METHOD_NAME_START_IS_LENGTH;
        final int    endingIndex   = startingIndex + 1;

        // We want the name to start with a lower-case letter
        final String lowercasedFirstCharacter = methodName.substring(startingIndex,
                                                                     endingIndex).toLowerCase();

        if (methodName.length() == endingIndex)
        {
            return lowercasedFirstCharacter;
        }

        return lowercasedFirstCharacter +
               methodName.substring(endingIndex);
    }

    private static Object handleLocalResource(final Map<String, String> namespaceMappings,
                                              final Map<String, String> reverseNamespaceMappings,
                                              final Class<?>            resourceClass,
                                              final Method              method,
                                              final Object              object)
            throws DatatypeConfigurationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InvocationTargetException,
                   JSONException,
                   OslcCoreApplicationException
    {
        if ((object instanceof String)  ||
            (object instanceof Boolean) ||
            (object instanceof Number))
        {
            return object;
        }
        else if (object instanceof URI)
        {
            final URI uri = (URI) object;

            if (!uri.isAbsolute())
            {
                throw new OslcCoreRelativeURIException(resourceClass,
                                                       method.getName(),
                                                       uri);
            }

            // Special nested JSONObject for URI
            final JSONObject jsonObject = new JSONObject();

            // Ensure we have an rdf prefix
            final String rdfPrefix = ensureNamespacePrefix(OslcConstants.RDF_NAMESPACE_PREFIX,
                                                           OslcConstants.RDF_NAMESPACE,
                                                           namespaceMappings,
                                                           reverseNamespaceMappings);

            jsonObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_RESOURCE,
                           object.toString());

            return jsonObject;
        }
        else if (object instanceof Date)
        {
            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime((Date) object);

            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar).toString();
        }

        return handleSingleResource(object,
                                    new JSONObject(),
                                    namespaceMappings,
                                    reverseNamespaceMappings);
    }

    private static JSONObject handleSingleResource(final Object              object,
                                                   final JSONObject          jsonObject,
                                                   final Map<String, String> namespaceMappings,
                                                   final Map<String, String> reverseNamespaceMappings)
            throws DatatypeConfigurationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InvocationTargetException,
                   JSONException,
                   OslcCoreApplicationException
    {
        final Class<? extends Object> objectClass = object.getClass();

        if (objectClass.getAnnotation(OslcResourceShape.class) != null)
        {
            // Collect the namespace prefix -> namespace mappings
            recursivelyCollectNamespaceMappings(namespaceMappings,
                                                reverseNamespaceMappings,
                                                objectClass);

            if (object instanceof IResource)
            {
                final URI aboutURI = ((IResource) object).getAbout();

                if (aboutURI != null)
                {
                    if (!aboutURI.isAbsolute())
                    {
                        throw new OslcCoreRelativeURIException(objectClass,
                                                               "getAbout",
                                                               aboutURI);
                    }

                    // Ensure we have an rdf prefix
                    final String rdfPrefix = ensureNamespacePrefix(OslcConstants.RDF_NAMESPACE_PREFIX,
                                                                   OslcConstants.RDF_NAMESPACE,
                                                                   namespaceMappings,
                                                                   reverseNamespaceMappings);

                    jsonObject.put(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_ABOUT,
                                   aboutURI.toString());
                }
            }

            buildResource(namespaceMappings,
                          reverseNamespaceMappings,
                          object,
                          objectClass,
                          jsonObject);

            return jsonObject;
        }

        return null;
    }

    private static String ensureNamespacePrefix(final String              prefix,
                                                final String              namespace,
                                                final Map<String, String> namespaceMappings,
                                                final Map<String, String> reverseNamespaceMappings)
    {
        final String existingPrefix = reverseNamespaceMappings.get(namespace);

        if (existingPrefix != null)
        {
            return existingPrefix;
        }

        final String existingNamespace = namespaceMappings.get(prefix);

        if (existingNamespace == null)
        {
            namespaceMappings.put(prefix,
                                  namespace);

            reverseNamespaceMappings.put(namespace,
                                         prefix);

            return prefix;
        }

        // There is already a namespace for this prefix.  We need to generate a new unique prefix.
        int index = 1;

        while (true)
        {
            final String newPrefix = prefix +
                                     index;

            if (!namespaceMappings.containsKey(newPrefix))
            {
                namespaceMappings.put(newPrefix,
                                      namespace);

                reverseNamespaceMappings.put(namespace,
                                             newPrefix);

                return newPrefix;
            }

            index++;
        }
    }

    private static void recursivelyCollectNamespaceMappings(final Map<String, String>     namespaceMappings,
                                                            final Map<String, String>     reverseNamespaceMappings,
                                                            final Class<? extends Object> objectClass)
    {
        final OslcSchema oslcSchemaAnnotation = objectClass.getPackage().getAnnotation(OslcSchema.class);
        if (oslcSchemaAnnotation != null)
        {
            final OslcNamespaceDefinition[] oslcNamespaceDefinitionAnnotations = oslcSchemaAnnotation.value();
            for (final OslcNamespaceDefinition oslcNamespaceDefinitionAnnotation : oslcNamespaceDefinitionAnnotations)
            {
                final String prefix       = oslcNamespaceDefinitionAnnotation.prefix();
                final String namespaceURI = oslcNamespaceDefinitionAnnotation.namespaceURI();

                namespaceMappings.put(prefix,
                                      namespaceURI);

                reverseNamespaceMappings.put(namespaceURI,
                                             prefix);
            }
        }

        final Class<?> superClass = objectClass.getSuperclass();
        if (superClass != null)
        {
            recursivelyCollectNamespaceMappings(namespaceMappings,
                                                reverseNamespaceMappings,
                                                superClass);
        }

        final Class<?>[] interfaces = objectClass.getInterfaces();
        if (interfaces != null)
        {
            for (final Class<?> interfac : interfaces)
            {
                recursivelyCollectNamespaceMappings(namespaceMappings,
                                                    reverseNamespaceMappings,
                                                    interfac);
            }
        }
    }

    private static void fromJSON(final String                             rdfPrefix,
                                 final Map<String, String>                jsonNamespaceMappings,
                                 final Map<Class<?>, Map<String, Method>> classPropertyDefinitionsToSetMethods,
                                 final JSONObject                         jsonObject,
                                 final Class<?>                           beanClass,
                                 final Object                             bean)
            throws DatatypeConfigurationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InstantiationException,
                   InvocationTargetException,
                   OslcCoreApplicationException,
                   URISyntaxException
    {
        Map<String, Method> setMethodMap = classPropertyDefinitionsToSetMethods.get(beanClass);
        if (setMethodMap == null)
        {
            setMethodMap = createPropertyDefinitionToSetMethods(beanClass);

            classPropertyDefinitionsToSetMethods.put(beanClass,
                                                     setMethodMap);
        }

        if (bean instanceof IResource)
        {
            final Object aboutURIObject = jsonObject.opt(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_ABOUT);

            if (aboutURIObject instanceof String)
            {
                final URI aboutURI = new URI(aboutURIObject.toString());

                if (!aboutURI.isAbsolute())
                {
                    throw new OslcCoreRelativeURIException(beanClass,
                                                           "setAbout",
                                                           aboutURI);
                }

                ((IResource) bean).setAbout(aboutURI);
            }
        }

        @SuppressWarnings("unchecked")
        final Set<Map.Entry<String, Object>> entrySet = jsonObject.entrySet();

        for (final Map.Entry<String, Object> entry : entrySet)
        {
            final String prefixedName = entry.getKey();
            final Object jsonValue    = entry.getValue();

            final String[] split = prefixedName.split(JSON_PROPERTY_DELIMITER);

            if (split.length != 2)
            {
                if (!JSON_PROPERTY_PREFIXES.equals(prefixedName))
                {
                    logger.warning("Ignored JSON property '" +
                                   prefixedName +
                                   "'.");
                }
            }
            else
            {
                final String namespacePrefix = split[0];
                final String name            = split[1];

                final String namespace = jsonNamespaceMappings.get(namespacePrefix);

                if (namespace == null)
                {
                    throw new OslcCoreMissingNamespacePrefixException(namespacePrefix);
                }

                final String propertyDefinition = namespace +
                                                  name;

                final Method setMethod = setMethodMap.get(propertyDefinition);
                if (setMethod == null)
                {
                    if ((RDF_ABOUT_URI.equals(propertyDefinition)) ||
                        (RDF_TYPE_URI.equals(propertyDefinition)))
                    {
                        // Ignore missing property definitions for rdf:about and rdf:types.
                    }
                    else
                    {
                        logger.warning("Set method not found for object type:  " +
                                       beanClass.getName() +
                                       ", propertyDefinition:  " +
                                       propertyDefinition);
                    }
                }
                else
                {
                    final Class<?> setMethodParameterClass = setMethod.getParameterTypes()[0];
                    Class<?> setMethodComponentParameterClass = setMethodParameterClass;

                    if (setMethodComponentParameterClass.isArray())
                    {
                        setMethodComponentParameterClass = setMethodComponentParameterClass.getComponentType();
                    }
                    else if (Collection.class.isAssignableFrom(setMethodComponentParameterClass))
                    {
                        final Type genericParameterType = setMethod.getGenericParameterTypes()[0];

                        if (genericParameterType instanceof ParameterizedType)
                        {
                            final ParameterizedType parameterizedType = (ParameterizedType) genericParameterType;
                            final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                            if (actualTypeArguments.length == 1)
                            {
                                final Type actualTypeArgument = actualTypeArguments[0];
                                if (actualTypeArgument instanceof Class)
                                {
                                    setMethodComponentParameterClass = (Class<?>) actualTypeArgument;
                                }
                            }
                        }
                    }

                    final Object parameter = fromJSONValue(rdfPrefix,
                                                           jsonNamespaceMappings,
                                                           classPropertyDefinitionsToSetMethods,
                                                           beanClass,
                                                           setMethod,
                                                           setMethodParameterClass,
                                                           setMethodComponentParameterClass,
                                                           jsonValue);

                    if (parameter != null)
                    {
                        setMethod.invoke(bean,
                                         new Object[] {parameter});
                    }
                }
            }
        }
    }

    private static Object fromJSONValue(final String                             rdfPrefix,
                                        final Map<String, String>                jsonNamespaceMappings,
                                        final Map<Class<?>, Map<String, Method>> classPropertyDefinitionsToSetMethods,
                                        final Class<?>                           beanClass,
                                        final Method                             setMethod,
                                        final Class<?>                           setMethodParameterClass,
                                        final Class<?>                           setMethodComponentParameterClass,
                                        final Object                             jsonValue)
            throws DatatypeConfigurationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InstantiationException,
                   InvocationTargetException,
                   OslcCoreApplicationException,
                   URISyntaxException
    {
        if (jsonValue instanceof JSONObject)
        {
            final JSONObject nestedJSONObject = (JSONObject) jsonValue;

            // If this is the special case for an rdf:resource?
            final Object uriObject = nestedJSONObject.opt(rdfPrefix + JSON_PROPERTY_DELIMITER + JSON_PROPERTY_SUFFIX_RESOURCE);

            if (uriObject instanceof String)
            {
                final URI uri = new URI(uriObject.toString());

                if (!uri.isAbsolute())
                {
                    throw new OslcCoreRelativeURIException(beanClass,
                                                           setMethod.getName(),
                                                           uri);
                }

                return uri;
            }

            final Object nestedBean = setMethodComponentParameterClass.newInstance();

            fromJSON(rdfPrefix,
                     jsonNamespaceMappings,
                     classPropertyDefinitionsToSetMethods,
                     nestedJSONObject,
                     setMethodComponentParameterClass,
                     nestedBean);

            return nestedBean;
        }
        else if (jsonValue instanceof JSONArray)
        {
            final JSONArray jsonArray = (JSONArray) jsonValue;

            final ArrayList<Object> tempList = new ArrayList<Object>();

            for (final Object jsonArrayEntryObject : jsonArray)
            {
                final Object parameterArrayObject = fromJSONValue(rdfPrefix,
                                                                  jsonNamespaceMappings,
                                                                  classPropertyDefinitionsToSetMethods,
                                                                  beanClass,
                                                                  setMethod,
                                                                  setMethodComponentParameterClass,
                                                                  setMethodComponentParameterClass,
                                                                  jsonArrayEntryObject);

                tempList.add(parameterArrayObject);
            }

            if (setMethodParameterClass.isArray())
            {
                // To support primitive arrays, we have to use Array reflection to set individual elements.  We cannot use Collection.toArray.
                // Array.set will unwrap objects to their corresponding primitives.
                final Object array = Array.newInstance(setMethodComponentParameterClass,
                                                       jsonArray.size());

                int index = 0;

                for (final Object parameterArrayObject : tempList)
                {
                    Array.set(array,
                              index,
                              parameterArrayObject);

                    index++;
                }

                return array;
            }

            // This has to be a Collection

            final Collection<Object> collection;

            // Handle the Collection, List, Deque, Queue interfaces.
            // Handle the AbstractCollection, AbstractList, AbstractSequentialList classes
            if ((Collection.class             == setMethodParameterClass) ||
                (List.class                   == setMethodParameterClass) ||
                (Deque.class                  == setMethodParameterClass) ||
                (Queue.class                  == setMethodParameterClass) ||
                (AbstractCollection.class     == setMethodParameterClass) ||
                (AbstractList.class           == setMethodParameterClass) ||
                (AbstractSequentialList.class == setMethodParameterClass))
            {
                collection = new LinkedList<Object>();
            }
            // Handle the Set interface
            // Handle the AbstractSet class
            else if ((Set.class          == setMethodParameterClass) ||
                     (AbstractSet.class  == setMethodParameterClass))
            {
                collection = new HashSet<Object>();
            }
            // Handle the SortedSet and NavigableSet interfaces
            else if ((SortedSet.class    == setMethodParameterClass) ||
                     (NavigableSet.class == setMethodParameterClass))
            {
                collection = new TreeSet<Object>();
            }
            // Not handled above. Let's try newInstance with possible failure.
            else
            {
                @SuppressWarnings("unchecked")
                final Collection<Object> tempCollection = ((Collection<Object>) setMethodParameterClass.newInstance());
                collection = tempCollection;
            }

            collection.addAll(tempList);

            return collection;
        }
        else
        {
            final String stringValue = jsonValue.toString();

            if (String.class == setMethodComponentParameterClass)
            {
                return stringValue;
            }
            else if ((Boolean.class == setMethodComponentParameterClass) || (Boolean.TYPE == setMethodComponentParameterClass))
            {
                // Cannot use Boolean.parseBoolean since it supports case-insensitive TRUE.
                if (Boolean.TRUE.toString().equals(stringValue))
                {
                    return Boolean.TRUE;
                }
                else if (Boolean.FALSE.toString().equals(stringValue))
                {
                    return Boolean.FALSE;
                }
                else
                {
                    throw new IllegalArgumentException("'" + stringValue + "' has wrong format for Boolean.");
                }
            }
            else if ((Byte.class == setMethodComponentParameterClass) || (Byte.TYPE == setMethodComponentParameterClass))
            {
                return Byte.valueOf(stringValue);
            }
            else if ((Short.class == setMethodComponentParameterClass) || (Short.TYPE == setMethodComponentParameterClass))
            {
                return Short.valueOf(stringValue);
            }
            else if ((Integer.class == setMethodComponentParameterClass) || (Integer.TYPE == setMethodComponentParameterClass))
            {
                return Integer.valueOf(stringValue);
            }
            else if ((Long.class == setMethodComponentParameterClass) || (Long.TYPE == setMethodComponentParameterClass))
            {
                return Long.valueOf(stringValue);
            }
            else if (BigInteger.class == setMethodComponentParameterClass)
            {
                return new BigInteger(stringValue);
            }
            else if ((Float.class == setMethodComponentParameterClass) || (Float.TYPE == setMethodComponentParameterClass))
            {
                return Float.valueOf(stringValue);
            }
            else if ((Double.class == setMethodComponentParameterClass) || (Double.TYPE == setMethodComponentParameterClass))
            {
                return Double.valueOf(stringValue);
            }
            else if (Date.class == setMethodComponentParameterClass)
            {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar(stringValue).toGregorianCalendar().getTime();
            }
        }

        return null;
    }

    private static Map<String, Method> createPropertyDefinitionToSetMethods(final Class<?> beanClass)
            throws OslcCoreApplicationException
    {
        final Map<String, Method> result = new HashMap<String, Method>();
        final Method[] methods = beanClass.getMethods();
        for (final Method method : methods)
        {
            if (method.getParameterTypes().length == 0)
            {
                final String getMethodName = method.getName();
                if (((getMethodName.startsWith(METHOD_NAME_START_GET)) &&
                     (getMethodName.length() > METHOD_NAME_START_GET_LENGTH)) ||
                    ((getMethodName.startsWith(METHOD_NAME_START_IS)) &&
                     (getMethodName.length() > METHOD_NAME_START_IS_LENGTH)))
                {
                    final OslcPropertyDefinition oslcPropertyDefinitionAnnotation = InheritedMethodAnnotationHelper.getAnnotation(method,
                                                                                                                                  OslcPropertyDefinition.class);

                    if (oslcPropertyDefinitionAnnotation != null)
                    {
                        // We need to find the set companion setMethod
                        final String setMethodName;
                        if (getMethodName.startsWith(METHOD_NAME_START_GET))
                        {
                            setMethodName = METHOD_NAME_START_SET +
                                            getMethodName.substring(METHOD_NAME_START_GET_LENGTH);
                        }
                        else
                        {
                            setMethodName = METHOD_NAME_START_SET +
                                            getMethodName.substring(METHOD_NAME_START_IS_LENGTH);
                        }

                        final Class<?> getMethodReturnType = method.getReturnType();
                        try
                        {
                            final Method setMethod = beanClass.getMethod(setMethodName,
                                                                         getMethodReturnType);

                            result.put(oslcPropertyDefinitionAnnotation.value(),
                                       setMethod);
                        }
                        catch (final NoSuchMethodException exception)
                        {
                            throw new OslcCoreMissingSetMethodException(beanClass,
                                                                        method,
                                                                        exception);
                        }
                    }
                }
            }
        }

        return result;
    }
}