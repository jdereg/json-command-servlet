package com.cedarsoftware.servlet

import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.ReflectionUtils
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.io.JsonObject
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.MetaUtils
import groovy.transform.CompileStatic
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import javax.servlet.ServletConfig
import javax.servlet.http.HttpServletRequest
import java.lang.annotation.Annotation
import java.lang.reflect.Array
import java.lang.reflect.InvocationTargetException
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
abstract class ConfigurationProvider
{
    private static final Logger LOG = LogManager.getLogger(ConfigurationProvider.class)
    private static final Map<String, Method> methodMap = new ConcurrentHashMap<>()
    private final ServletConfig servletConfig
    private static final Pattern cmdUrlPattern = ~'^/([^/]+)/([^/]+)(.*)$'	// Allows for /controller/method/blah blah (where anything after method is ignored up to ?)
    private static final Pattern cmdUrlPattern2 = ~'^/[^/]+/([^/]+)/([^/]+)(.*)$'	// Allows for /context/controller/method/blah blah (where anything after method is ignored up to ?)

    ConfigurationProvider(ServletConfig servletConfig)
    {
        this.servletConfig = servletConfig
    }

    ServletConfig getServletConfig()
    {
        return servletConfig
    }

    /**
     * Fetch the controller with the given name.
     * @name String name of controller to fetch
     * @return Object controller instance registered with the given name.  The
     * controller could be registered as a Spring Bean or an n-cube.
     */
    protected abstract Object getController(String name)

    /**
     * Verify that the passed in method is allowed to be called remotely.
     * @param methodName String method name to check
     * @return boolean true if ok, false otherwise.
     */
    protected abstract boolean isMethodAllowed(String methodName)

    /**
     * @return String prefix to append to log so that we can tell what controller
     * type was used (spring:, ncube:, etc.)
     */
    protected abstract String getLogPrefix()

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
    Envelope callController(HttpServletRequest request, String json)
    {
        final Matcher matcher = getUrlMatcher(request)
        if (matcher == null)
        {
            String msg = "error: Invalid JSON request - /controller/method not specified: ${json}"
            LOG.warn(msg)
            return new Envelope(msg, false)
        }

        // Step 1: Fetch controller instance by name
        final String controllerName = matcher.group(1)
        final Object controller = getController(controllerName)
        if (controller instanceof Envelope)
        {
            return (Envelope) controller
        }

        // Step 2: Convert JSON arguments from URL (GET argument or POST body) to Object[]
        final String methodName = matcher.group(2)
        Object jArgs = getArguments(json, controllerName, methodName)
        if (jArgs instanceof Envelope)
        {
            return (Envelope) jArgs
        }
        final Object[] args = (Object[]) jArgs

        if (!isMethodAllowed(methodName))
        {
            String msg = "Method '${methodName}' is not allowed to be called remotely on controller '${controllerName}'"
            LOG.warn(msg)
            return new Envelope(msg, false)
        }

        // Step 3: Find and invoke method
        // Wrap the call to the Controller so we can detect any methods that fail to catch exceptions and properly
        // return them as errors.  This separates the errors related to communication from errors related to the
        // Controller throwing an exception.
        Object result
        Throwable exception = null

        try
        {
            final Object method = getMethod(controller, controllerName, methodName, args.length)
            if (method instanceof Envelope)
            {
                return (Envelope) method
            }
            result = callMethod((Method) method, controller, args)
        }
        catch (ThreadDeath t)
        {
            throw t
        }
        catch (Throwable t)
        {
            exception = JsonCommandServlet.getDeepestException(t)
            String msg = exception.class.name
            if (exception.message != null)
            {
                msg += ' ' + exception.message
            }
            LOG.warn("An exception occurred calling '${controllerName}.${methodName}'", exception)
            result = "error: '${methodName}' failed with the following message: ${msg}"
        }

        return new Envelope(result, exception == null, exception)
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
        catch(Exception e)
        {
            String errMsg = "error: unable to parse JSON argument list on call '${controllerName}.${methodName}'"
            LOG.error(errMsg, e)
            return new Envelope(errMsg, false, e)
        }

        if (jArgs != null && !(jArgs instanceof Object[]))
        {
            return new Envelope("error: Arguments must be either null or a JSON array", false)
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
    private static Object getMethod(Object controller, String controllerName, String methodName, int argCount)
    {
        String methodKey = "${controllerName}.${methodName}.${argCount}"
        Method method = methodMap[methodKey]
        
        if (method == null)
        {
            method = getMethod(controller.class, methodName, argCount)
            if (method == null)
            {
                return new Envelope("error: Method not found: " + methodKey, false)
            }

            Annotation a = ReflectionUtils.getMethodAnnotation(method, ControllerMethod.class)
            if (a != null)
            {
                ControllerMethod cm = (ControllerMethod)a
                if ("false".equalsIgnoreCase(cm.allow()))
                {
                    return new Envelope("error: Method '${methodName}' is not allowed to be called via HTTP Request.", false)
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
    private static Method getMethod(Class c, String name, int argc)
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
     * Invoke the passed in method, on the passed in target, with the passed in arguments.
     * @param method Method to be invoked
     * @param target instance which contains the method
     * @param args Object[] of values which line up to the method arguments.
     * @return Object the value return from the invoked method.
     */
    private static Object callMethod(Method method, Object target, Object[] args)
    {
        try
        {
            return method.invoke(target, convertArgs(method, args))
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e)
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e.targetException)
        }
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
    private static Object[] convertArgs(Method method, Object[] args)
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
                if (Collection.class.isAssignableFrom(types[i]))
                {   // easy: Collection to Collection Type
                    converted[i] = args[i]
                }
                else if (types[i].array)
                {   // hard: Collection to array type (handles any array type, String[], Object[], Custom[], etc.)
                    Collection inbound = (Collection)args[i]
                    converted[i] = arrayBuilder(types[i].componentType, inbound)
                }
                else
                {
                    throw new IllegalArgumentException("Cannot pass Collection into an argument type that is not a Collection or Array[], arg type: ${types[i].name}")
                }
            }
            else if (args[i].class.array)
            {
                if (types[i].array)
                {   // easy: array to array
                    converted[i] = args[i]
                }
                else if (Collection.class.isAssignableFrom(types[i]))
                {   // harder: array to collection
                    try
                    {
                        Collection col = (Collection) JsonReader.newInstance(types[i])
                        Collections.addAll(col, args)
                        converted[i] = col
                    }
                    catch (Exception e)
                    {
                        throw new IllegalArgumentException("Could not create Collection instance for type: ${types[i].name}", e)
                    }
                }
                else
                {
                    throw new IllegalArgumentException("Cannot pass Array[] into an argument type that is not a Collection or Array[], arg type: ${types[i].name}")
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
