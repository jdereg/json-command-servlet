package com.cedarsoftware.servlet;

import com.cedarsoftware.servlet.framework.driver.ServletCtxProvider;
import com.cedarsoftware.util.IOUtilities;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessControlException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class will accept JSON REST requests, find the named Spring Bean,
 * find the method, and then invoke the method.  Once complete, it will
 * convert the return object to JSON and then send that back to the client.
 * The requests are typically made from a Javascript client, although they
 * could easily be made from Python, Java, or Objective C.  The request
 * comes in with http://yoursite.com/json/controllerName/methodName.  The
 * "json" part can be whatever name you map to this servlet in web.xml.
 * The arguments are sent as the HTTP POST body, or they can be sent via
 * query params like this:
 *
 *     http://yoursite.com/json/Controller/methodName?json=[arg1, arg2,...]
 *
 * When calling the JsonServlet, it will always return an object in the form:
 *
 *     {"value":v,"status":false|true|null}
 *
 * where the value 'v' is the return value of the Controller method called.
 * The status is 'true' if the method call properly succeeded.  Use the
 * return value 'v' when status === true.  If status === false then the
 * communications reach the server, however, there was an exception.  A Controller
 * method threw an exception, invalid JSON was passed in, the name of the
 * controller targeted was wrong, the controller targeted was not a BaseController,
 * or the method on the controller was not found.  The value 'v' when the the
 * status === false will indicate the error.  If the status === null then the
 * call() method within the browser never reached the server.  The value 'v' is
 * a message indicating a network communications issue.
 *
 * The returned JSON is gzip compressed if the caller indicates that it
 * accepts Content-Encoding of gzip AND the return message is greater than
 * 512 bytes. The return stream from methods that return large arrays and/or
 * object graphs compresses especially well.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
public class JsonCommandServlet extends HttpServlet
{
    private static final long serialVersionUID = 5008267310712043139L;
    private static final Logger _log = Logger.getLogger(JsonCommandServlet.class);
    private static final Map<String, Method> _methodMap = new ConcurrentHashMap<String, Method>();
    private static Pattern _cmdUrlPattern = Pattern.compile("^/([^/]+)/([^/]+)(.*)$");	// Allows for /controller/method/blah blah (where anything after method is ignored up to ?)
    private AppCtx _appCtx;

    public static final ThreadLocal<HttpServletRequest> servletRequest = new ThreadLocal<HttpServletRequest>()
    {
        public void set(HttpServletRequest value)
        {
            super.set(value);
        }
    };

    public static final ThreadLocal<HttpServletResponse> servletResponse = new ThreadLocal<HttpServletResponse>()
    {
        public void set(HttpServletResponse value)
        {
            super.set(value);
        }
    };

    public void init()
    {
        try
        {
            _appCtx = ServletCtxProvider.getAppCtx(getServletContext());
        }
        catch (Exception e)
        {
            _log.error("Error initializing app context", e);
        }
    }

    /**
     * Handle JSON GET style request.  In this case, 'controller', 'method', and 'json' are
     * specified as URL parameters.
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    {
        // Step 1: Ensure that the request header has Content-Length correctly specified.
        String json = request.getParameter("json");

        if (json == null || json.trim().length() < 1)
        {
            sendJsonResponse(request, response, new Object[] {"error: HTTP-GET had empty or no 'json' parameter.", false});
            return;
        }

        if (_log.isDebugEnabled())
        {
            _log.debug("GET RESTful JSON");
        }

        processJsonRequest(request, response, json);
    }

    /**
     * Process JSON POST style where the controller, method, and arguments are passed in as the
     * POST data.
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    {
        // Ensure that the request header has Content-Length correctly specified.
        if (request.getContentLength() < 1)
        {
            sendJsonResponse(request, response, new Object[] {"error: Call to server had incorrect Content-Length specified.", false});
            return;
        }

        String json;
        try
        {
            byte[] jsonBytes = new byte[request.getContentLength()];
            IOUtilities.transfer(request.getInputStream(), jsonBytes);
            json = new String(jsonBytes, "UTF-8");

            if (_log.isDebugEnabled())
            {
                _log.debug("POST RESTful JSON");
            }
        }
        catch(Exception e)
        {
            sendJsonResponse(request, response, new Object[] {"error: Unable to read HTTP-POST JSON content.", false});
            return;
        }

        processJsonRequest(request, response, json);
    }

    private void processJsonRequest(HttpServletRequest request, HttpServletResponse response, String json)
    {
        Object[] ret;
        boolean methodHandledResponse = false;
        try
        {
            // ret[0] is controller return value
            // ret[1] true if successful, false if exception occurred.
        	// ret[2] true if the method wrote the HTTP response, false otherwise
            ret = makeJsonCall(request, response, json);
            methodHandledResponse = (Boolean) ret[2];
        }
        catch (ThreadDeath d)
        {
            throw d;
        }
        catch (Throwable e)
        {
            Throwable t = getDeepestException(e);
            String msg = t.getClass().getName();
            if (t.getMessage() != null)
            {
                msg += ' ' + t.getMessage();
            }

            if (t instanceof IOException)
            {
                if ("org.apache.catalina.connector.ClientAbortException".equals(t.getClass().getName()))
                {
                    _log.info("Client aborted connection while processing JSON request.");
                }
                else
                {
                    sendJsonResponse(request, response, new Object[]{"error: Invalid JSON request made.", false});
                }
            }
            else if (t instanceof AccessControlException)
            {
                sendJsonResponse(request, response, new Object[]{"error: Your session with our website appears to have ended.  Please log out and back in.", false});
            }
            else
            {
                sendJsonResponse(request, response, new Object[]{"error: Communications issue between your computer and our website (" + msg + ')', false});
            }
            return;
        }

        if (!methodHandledResponse)
        {
	        // Send JSON result
	        long start = System.nanoTime();
	        sendJsonResponse(request, response, new Object[] {ret[0], ret[1]});
	        long end = System.nanoTime();

	        if (end - start > 2000000000)
	        {    // Total time more than 2 seconds
	            if (json.length() > 256)
	            {
	                json = json.substring(0, 255);
	            }
	            _log.info("Slow return response: " + json + " took " + ((end - start) / 1000000) + " ms");
	        }
        }
    }

    /**
     * Read the JSON request (susceptible to Exceptions that are allowed to be thrown from here),
     * and then call the appropriate Controller method.  The controller method exceptions are
     * caught and returned carefully as JSON error String responses.  Note, this should not happen - if
     * they do, it is a case of a missing try/catch handler in a Controller method.  Troll the logs to find
     * these and fix them as they come up.
     */
    private Object[] makeJsonCall(HttpServletRequest request, HttpServletResponse response, String json) throws Exception
    {
        String pathInfo = request.getPathInfo();
        Matcher matcher = _cmdUrlPattern.matcher(pathInfo);
        matcher.find();

        if (matcher.groupCount() < 2)
        {
            String msg = "error: Invalid JSON request - /controller/method not specified: " + json;
            _log.warn(msg);
            return new Object[] {msg, false, false};
        }

        String bean = matcher.group(1);
        String methodName = matcher.group(2);
        Object jArgs;
        try
        {
        	jArgs = JsonReader.jsonToJava(json);
        }
        catch(Exception e)
        {
        	String errMsg = "error: unable to parse JSON argument list on call '" + bean + "." + methodName + "'";
        	_log.error(errMsg, e);
        	return new Object[] {errMsg, false, false};
        }

        if (jArgs != null && !(jArgs instanceof Object[]))
        {
            return new Object[] {"error: Arguments must be either null or a JSON array", false};
        }
        Object[] args = (Object[]) jArgs;
        int argCount = (args == null) ? 0 : args.length;

        if (_log.isDebugEnabled())
        {
            _log.debug("  " + bean + '.' + methodName + '(' + json.substring(1, json.length() - 1) + ')');
        }

        Object target;
        try
        {
            target = _appCtx.getBean(bean);
        }
        catch(Exception e)
        {
            _log.warn("Invalid JSON target: " + bean);
            return new Object[] {"error: Invalid target '" + bean + "'.", false, false};
        }

        Class targetType = target.getClass();
        Annotation annotation = ReflectionUtils.getClassAnnotation(targetType, ControllerClass.class);
        if (annotation == null)
        {
            return new Object[] {"error: target '" + bean + "' is not marked as a ControllerClass.", false, false};
        }

        long start = System.nanoTime();

        // Wrap the call to the Controller so we can detect any methods that fail to catch exceptions and properly
        // return them as errors.  This separates the errors related to communication from errors related to the
        // Controller throwing an exception.
        Object result;
        boolean selfHandlingResponse = false;
        boolean status = true;

        try
        {
            String methodKey = bean + '.' + methodName + '.' + argCount;
            Method method = _methodMap.get(methodKey);
            if (method == null)
            {
                method = getMethod(targetType, methodName, argCount);
                if (method == null)
                {
                    return new Object[] {"error: Method not found: " + methodKey, false, false};
                }

                Annotation a = ReflectionUtils.getMethodAnnotation(method, ControllerMethod.class);
                if (a != null)
                {
                    ControllerMethod cm = (ControllerMethod)a;
                    if ("false".equalsIgnoreCase(cm.allow()))
                    {
                        return new Object[] {"error: Method '" + methodName + "' is not allowed to be called via HTTP Request.", false, false};
                    }
                }

                _methodMap.put(methodKey, method);
            }
            Annotation a = ReflectionUtils.getMethodAnnotation(method, HttpResponseHandler.class);
            selfHandlingResponse = a != null;

            // Store Servlet Request Response objects on the current thread so that Controller's can access them
            // via JsonCommandServlet.servletRequest.get(), JsonCommandServlet.servletResponse.get()
            servletRequest.set(request);
            servletResponse.set(response);
            result = callMethod(method, target, args);

            // Remove request / response from Thread
            servletRequest.remove();
            servletResponse.remove();
        }
        catch (ThreadDeath t)
        {
            throw t;
        }
        catch (Throwable t)
        {
            t = getDeepestException(t);
            String msg = t.getClass().getName();
            if (t.getMessage() != null)
            {
                msg += ' ' + t.getMessage();
            }
            _log.warn("An exception occurred calling '" + bean + '.' + methodName + "'", t);
            result = "error: '" + methodName + "' failed with the following error: " + msg;
            status = false;
        }

        // Time the Controller call.
        long end = System.nanoTime();

        if (end - start > 2000000000)
        {
            String api = json;
            if (api.length() > 256)
            {
                api = api.substring(0, 255);
            }
            _log.info("Slow API: " + api + " took " + ((end - start) / 1000000) + " ms");
        }

        return new Object[]{result, status, selfHandlingResponse};
    }

    private static void sendJsonResponse(HttpServletRequest request, HttpServletResponse response, Object[] o)
    {
        try
        {
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "private, no-cache, no-store");

            // Temporarily wrap return type in Object[] to shrink the return type in JSON format
            String retVal = JsonWriter.objectToJson(new Object[]{o[0]});
            StringBuilder s = new StringBuilder("{\"data\":");

            // Now pull off the Object[] wrapper.
            if ("[]".equals(retVal))
            {
                s.append("null");
            }
            else
            {
                s.append(retVal.substring(1, retVal.length() - 1));
            }

            retVal = null;  // clear reference for GC friendliness
            s.append(",\"status\":");
            s.append(o[1]);
            s.append('}');

            ByteArrayOutputStream jsonBytes = new ByteArrayOutputStream();
            jsonBytes.write(s.toString().getBytes("UTF-8"));
            s.setLength(0);
            s = null;

            // For debugging
            if (_log.isDebugEnabled())
            {
                _log.debug("  return " + new String(jsonBytes.toByteArray(), "UTF-8"));
            }

            if (jsonBytes.size() > 512 && request.getHeader("Accept-Encoding").contains("gzip"))
            {   // Only compress if the output is longer than 512 bytes.
                ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream(jsonBytes.size());
                IOUtilities.compressBytes(jsonBytes, compressedBytes);

                if (compressedBytes.size() < jsonBytes.size())
                {   // Only write compressed if it is smaller than original JSON String
                    response.setHeader("Content-Encoding", "gzip");
                    jsonBytes = compressedBytes;
                }
            }

            response.setContentLength(jsonBytes.size());
            OutputStream output = new BufferedOutputStream(response.getOutputStream());
            jsonBytes.writeTo(output);
            output.flush();
        }
        catch (ThreadDeath t)
        {
            throw t;
        }
        catch (Throwable t)
        {
            t = getDeepestException(t);
            String msg = t.getClass().getName();
            if (t.getMessage() != null)
            {
                msg += ' ' + t.getMessage();
            }

            if (t instanceof IOException)
            {
                if ("org.apache.catalina.connector.ClientAbortException".equals(t.getClass().getName()))
                {
                    _log.info("Client aborted connection while processing JSON request.");
                }
                else
                {
                    _log.warn("IOException - sending response: " + msg);
                }
            }
            else if (t instanceof AccessControlException)
            {
                _log.warn("AccessControlException - sending response: " + msg);
            }
            else
            {
                _log.warn("An unexpected exception occurred sending JSON response to client", t);
            }
        }
    }

    private static Throwable getDeepestException(Throwable e)
    {
        while (e.getCause() != null)
        {
            e = e.getCause();
        }

        if (!(e instanceof AccessControlException || e instanceof IOException))
        {
            _log.warn("unexpected exception occurred: ", e);
        }
        else
        {
            String msg = e.getClass().getName();
            if (e.getMessage() != null)
            {
                msg = msg + ' ' + e.getMessage();
            }

            _log.warn("exception occurred: " + msg);
        }

        return e;
    }

    private static Method getMethod(Class c, String name, int argc)
    {
        Method[] methods = c.getMethods();
        for (Method method : methods)
        {
            if (name.equals(method.getName()) && method.getParameterTypes().length == argc)
            {
                return method;
            }
        }
        return null;
    }

    private static Object callMethod(Method method, Object target, Object[] args)
    {
        try
        {
            return method.invoke(target, args);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException(e.getTargetException());
        }
    }
}
