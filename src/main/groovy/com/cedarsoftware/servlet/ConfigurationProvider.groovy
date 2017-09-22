package com.cedarsoftware.servlet

import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.ReflectionUtils
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.io.JsonIoException
import com.cedarsoftware.util.io.JsonObject
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.MetaUtils
import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.WebApplicationContextUtils

import javax.servlet.ServletConfig
import javax.servlet.http.HttpServletRequest
import java.lang.annotation.Annotation
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Implement controller provider.  Controllers are named, targetable objects that
 * clients can invoke methods upon.
 * *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class ConfigurationProvider
{
    private final ApplicationContext springAppCtx
    private static final Map<String, Method> methodMap = new ConcurrentHashMap<>()
    private final ServletConfig servletConfig
    private static final Pattern cmdUrlPattern = ~'^/([^/]+)/([^/]+)(.*)$'	// Allows for /controller/method/blah blah (where anything after method is ignored up to ?)
    private static final Pattern cmdUrlPattern2 = ~'^/[^/]+/([^/]+)/([^/]+)(.*)$'	// Allows for /context/controller/method/blah blah (where anything after method is ignored up to ?)

    ConfigurationProvider(ServletConfig servletConfig)
    {
        this.servletConfig = servletConfig
        springAppCtx = WebApplicationContextUtils.getWebApplicationContext(servletConfig.servletContext)
    }

    ServletConfig getServletConfig()
    {
        return servletConfig
    }

    /**
     * Fetch the controller with the given name.
     * @param name String name of a Controller instance (Spring bean name).
     * @return Controller instance if successful, otherwise throw BeansException if controller
     * bean doesn't exist or IllegalArgumentException if class isn't annotated as a controller
     */
    protected Object getController(String name)
    {
        if (!springAppCtx.containsBean(name))
        {
            throw new IllegalArgumentException("Attempted to call controller with name: ${name}, but no controller with that name exists")
        }
        Object controller = springAppCtx.getBean(name)
        Class targetType = controller.class
        Annotation annotation = ReflectionUtils.getClassAnnotation(targetType, RestController.class)
        if (annotation == null)
        {
            throw new IllegalArgumentException("error: target '${controller}' is not marked as a @RestController")
        }
        return controller
    }

    /**
     * Get a regex Matcher that matches the URL String for /context/cmd/controller/method
     * @param request HttpServletRequest passed to the command servlet.
     * @param json String arguments in JSON form from HTTP request
     * @return Matcher that pattern matches the URL or
     */
    static Matcher getUrlMatcher(HttpServletRequest request)
    {
        Matcher matcher
        if (StringUtilities.hasContent(request.pathInfo))
        {
            matcher = cmdUrlPattern.matcher(request.pathInfo)
        }
        else
        {
            String path = request.requestURI - request.contextPath
            matcher = cmdUrlPattern2.matcher(path)
        }

        if (matcher.find() && matcher.groupCount() < 2)
        {
            return null
        }
        return matcher
    }

    /**
     * Read the JSON request (susceptible to Exceptions that are allowed to be thrown from here),
     * and then call the appropriate Controller method.  The controller method exceptions are
     * caught and returned carefully as JSON error String responses.  Note, this should not happen - if
     * they do, it is a case of a missing try/catch handler in a Controller method (or Advice around
     * the Controller).
     */
    Object callController(Object controller, HttpServletRequest request, String json)
    {
        final Matcher matcher = getUrlMatcher(request)
        if (matcher == null)
        {
            throw new IllegalArgumentException("error: Invalid JSON request - /controller/method not specified")
        }

        // Step 1: Fetch controller instance by name
        final String controllerName = matcher.group(1)

        // Step 2: Convert JSON arguments from URL (GET argument or POST body) to Object[]
        final String methodName = matcher.group(2)
        Object jArgs = getArguments(json, controllerName, methodName)
        final Object[] args = (Object[]) jArgs

        // Step 3: Find and invoke method
        // Wrap the call to the Controller so we can detect any methods that fail to catch exceptions and properly
        // return them as errors.  This separates the errors related to communication from errors related to the
        // Controller throwing an exception.
        final Method method = getMethod(controller, controllerName, methodName, args.length)
        Object result = method.invoke(controller, convertArgs(method, args))
        return result
    }

    /**
     * Build the argument list from the passed in json
     * @param json String argument lists
     * @param controllerName String name of controller
     * @param methodName String name of method to call on the controller
     * @return Object[] of arguments to be passed to method, or Envelope if an error occurred.
     */
    static Object getArguments(String json, String controllerName, String methodName)
    {
        Object jArgs
        try
        {
            jArgs = JsonReader.jsonToJava(json)
        }
        catch(JsonIoException e)
        {
            throw new IllegalArgumentException("error: unable to parse JSON argument list on call '${controllerName}.${methodName}', parse error: ${e.message}")
        }

        if (jArgs != null && !(jArgs instanceof Object[]))
        {
            throw new IllegalArgumentException("error: Arguments must be either null or a JSON array")
        }

        return jArgs
    }

    /**
     * Fetch the named method from the controller. First a local cache will be checked, and if not
     * found, the method will be found reflectively on the controller.  If the method is found, then
     * it will be checked for a ControllerMethod annotation, which can indicate that it is NOT allowed
     * to be called.  This permits a public controller method to be blocked from remote access.
     * @param controller Object on which the named method will be found.
     * @param controllerName String name of the controller (Spring name, n-cube name, etc.)
     * @param methodName String name of method to be located on the controller.
     * @param argCount int number of arguments.  This is used as part of the cache key to allow for
     * duplicate method names as long as the argument list length is different.
     */
    protected static Method getMethod(Object controller, String controllerName, String methodName, int argCount)
    {
        String methodKey = "${controllerName}.${methodName}.${argCount}"
        Method method = methodMap[methodKey]
        
        if (method == null)
        {
            method = getMethod(controller.class, methodName, argCount)
            if (method == null)
            {
                throw new IllegalArgumentException("error: Method not found: ${methodKey}")
            }

            Annotation a = ReflectionUtils.getMethodAnnotation(method, ControllerMethod.class)
            if (a != null)
            {
                ControllerMethod cm = (ControllerMethod)a
                if ("false".equalsIgnoreCase(cm.allow()))
                {
                    throw new IllegalArgumentException("error: Method '${methodName}' is not allowed to be called via HTTP Request")
                }
            }

            methodMap[methodKey] = method
        }
        return method
    }

    /**
     * Reflectively find the requested method on the requested class.
     * @param c Class containing the method
     * @param name String method name
     * @param argc int number of arguments
     * @return Method instance located on the passed in class.
     */
    protected static Method getMethod(Class c, String name, int argc)
    {
        Method[] methods = c.methods
        for (Method method : methods)
        {
            if (name == method.name && method.parameterTypes.length == argc)
            {
                return method
            }
        }
        return null
    }

    /**
     * Convert the passed in arguments to match the arguments of the passed in method.
     * @param method Method which contains argument types.
     * @param args Object[] of values, which need to be converted.
     * @return Object[] of converted values.  The original values from args[], will be
     * converted to match the argument types in the method.  Java-util's Converter.convert()
     * handles the primitive and simple types (date, etc.). Collections and Arrays will
     * be converted to match the respective argument type, and Maps will be converted to
     * classes (if the matching argument is not a Map).  This is done by bring the Class
     * type of the argument into the json-io JsonObject which represents the instance of
     * the class.
     */
    protected static Object[] convertArgs(Method method, Object[] args)
    {
        Object[] converted = new Object[args.length]
        Class[] types = method.parameterTypes

        for (int i=0; i < args.length; i++)
        {
            if (args[i] == null)
            {
                converted[i] = null
            }
            else if (args[i] instanceof Class)
            {   // Special handle an argument of type 'Class' because isLogicalPrimitive() is true for Class.
                converted[i] = args[i]
            }
            else if (MetaUtils.isLogicalPrimitive(args[i].class))
            {   // Marshal all primitive types, including Date, String (any combination of directions -
                // String to number, number to String, Date to String, etc.)  See Converter.convert().
                converted[i] = Converter.convert(args[i], types[i])
            }
            else if (args[i] instanceof Collection)
            {
                if (types[i].array)
                {   // hard: Collection to array type (handles any array type, String[], Object[], Custom[], etc.)
                    Collection inbound = (Collection) args[i]
                    converted[i] = arrayBuilder(types[i].componentType, inbound)
                }
                else
                {
                    converted[i] = args[i].asType(types[i])
                }
            }
            else if (args[i].class.array)
            {
                if (types[i].array)
                {   // easy: array to array
                    converted[i] = args[i]
                }
                else
                {
                    converted[i] = args[i].asType(types[i])
                }
            }
            else if (args[i] instanceof JsonObject)
            {
                JsonObject jsonObj = (JsonObject) args[i]
                Object type = jsonObj["@type"]
                if (!(type instanceof String) || !StringUtilities.hasContent((String) type))
                {
                    jsonObj["@type"] = types[i].name
                }
                CmdReader reader = new CmdReader()
                try
                {
                    converted[i] = reader.convertParsedMapsToJava(jsonObj)
                }
                catch (Exception e)
                {
                    throw new IllegalArgumentException("Unable to convert JSON object to arg type: ${types[i].name}", e)
                }
            }
            else if (args[i] instanceof Map)
            {
                Map map = (Map) args[i]
                if (Map.class.isAssignableFrom(types[i]))
                {
                    converted[i] = map
                }
                else
                {   // Map on input, being set into a non-map type.  Make sure @type gets set correctly.
                    throw new IllegalArgumentException("Unable to convert Map to arg type: ${types[i].name}")
                }
            }
            else
            {
                converted[i] = args[i]
            }
        }
        return converted
    }

    /**
     * Convert Collection to a Java (typed) array [].
     * @param classToCastTo array type (Object[], Person[], etc.)
     * @param c Collection containing items to be placed into the array.
     * @param <T> Type of the array
     * @return Array of the type (T) containing the items from collection 'c'.
     */
    static <T> T[] arrayBuilder(Class<T> classToCastTo, Collection c)
    {
        T[] array = (T[]) c.toArray((T[]) Array.newInstance(classToCastTo, c.size()))
        Iterator i = c.iterator()
        int idx = 0
        while (i.hasNext())
        {
            Array.set(array, idx++ as int, i.next())
        }
        return array
    }

    /**
     * Extend JsonReader to gain access to the convertParsedMapsToJava() API.
     */
    static class CmdReader extends JsonReader
    {
        Object convertParsedMapsToJava(JsonObject root)
        {
            return super.convertParsedMapsToJava(root)
        }
    }
}
