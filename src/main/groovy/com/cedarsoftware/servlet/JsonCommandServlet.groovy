package com.cedarsoftware.servlet

import com.cedarsoftware.util.AdjustableGZIPOutputStream
import com.cedarsoftware.util.ArrayUtilities
import com.cedarsoftware.util.IOUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.io.JsonIoException
import com.cedarsoftware.util.io.JsonWriter
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.lang.reflect.InvocationTargetException
import java.security.AccessControlException
import java.util.regex.Matcher
import java.util.zip.Deflater

/**
 * <p>This class will accept JSON REST requests, find the named Spring Bean,
 * find the method, and then invoke the method.  Once complete, it will
 * convert the return object to JSON and then send that back to the client.
 * The requests are typically made from a Javascript client, although they
 * could easily be made from Python, Java, or Objective C.  The request
 * comes in with http://yoursite.com/context/cmd/controllerName/methodName.
 * The "cmd" part can be whatever name you map to this servlet in web.xml.
 * The arguments are sent as the HTTP POST body, or they can be sent via
 * query params like this:
 * </p><p>
 *     http://yoursite.com/json/Controller/methodName?json=[arg1, arg2,...]
 * </p><p>
 * When calling the JsonServlet, it will always return an object in the form:
 * </p><p>
 *     {"data":v,"status":false|true|null}
 * </p><p>
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
 * </p><p>
 * The returned JSON is gzip compressed if the caller indicates that it
 * accepts Content-Encoding of gzip AND the return message is greater than
 * 512 bytes. The return stream from methods that return large arrays and/or
 * object graphs compresses especially well.
 * </p>
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
class JsonCommandServlet extends HttpServlet
{
    public static final ThreadLocal<HttpServletRequest> servletRequest = new ThreadLocal<>()
    public static final ThreadLocal<HttpServletResponse> servletResponse = new ThreadLocal<>()
    private ConfigurationProvider configProvider
    private static final Logger LOG = LoggerFactory.getLogger(JsonCommandServlet.class)

    void init()
    {
        try
        {
            configProvider = new ConfigurationProvider(servletConfig)
        }
        catch (Exception e)
        {
            LOG.error("Unable to set up SpringConfigurationProvider: ${e.message}", e)
        }
        LOG.info('JsonCommandServlet init complete')
    }

    /**
     * If using UrlRewrite from tuckey.org, then route HTTP Json commands via
     * this 'route' method and list it in the urlrewrite.xml.
     */
    void route(HttpServletRequest request, HttpServletResponse response)
    {
        if ('post'.equalsIgnoreCase(request.method))
        {
            doPost(request, response)
        }
        else if ('get'.equalsIgnoreCase(request.method))
        {
            doGet(request, response)
        }
        else if ('head'.equalsIgnoreCase(request.method))
        {
            doHead(request, response)
        }
        else if ('put'.equalsIgnoreCase(request.method))
        {
            doPut(request, response)
        }
        else if ('delete'.equalsIgnoreCase(request.method))
        {
            doDelete(request, response)
        }
        else if ('options'.equalsIgnoreCase(request.method))
        {
            doOptions(request, response)
        }
        else if ('trace'.equalsIgnoreCase(request.method))
        {
            doTrace(request, response)
        }
    }

    /**
     * Handle JSON GET style request.  In this case, 'controller', 'method', and 'json' are
     * specified as URL parameters.
     * Example: http://cedarsoftware.com/coolApp/CoolController/coolMethod?json=[args...]
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    {
        try
        {
            servletRequest.set(request)       // store on ThreadLocal
            servletResponse.set(response)     // store on ThreadLocal

            // Step 1: Ensure that the request header has Content-Length correctly specified.
            String json = request.getParameter("json")
            if (json == null || json.trim().length() < 1)
            {
                String msg = "error: HTTP-GET had empty or no 'json=' argument."
                LOG.info(msg)
                sendJsonResponse(request, response, new Envelope(msg, false))
                return
            }
            json = URLDecoder.decode(json, "UTF-8")

            if (LOG.debugEnabled)
            {
                LOG.debug("HTTP GET(${request.pathInfo}), json=${json}")
            }

            handleRequestAndResponse(request, response, json)
        }
        finally
        {
            removeThreadLocals()
        }
    }

    /**
     * Process JSON POST style where the controller, method, and arguments are passed in as the
     * POST data.
     * Example: http://cedarsoftware.com/coolApp/CoolController/coolMethod
     * Post body contains the arguments in JSON format.
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    {
        servletRequest.set(request)        // store on ThreadLocal
        servletResponse.set(response)      // store on ThreadLocal

        try
        {
            // Ensure that the request header has Content-Length correctly specified.
            if (request.contentLength < 1)
            {
                String msg = "error: Call to server had incorrect Content-Length specified."
                LOG.info(msg)
                sendJsonResponse(request, response, new Envelope(msg, false))
                return
            }

            // Transfer request body to byte[]
            byte[] jsonBytes = new byte[request.contentLength]
            IOUtilities.transfer(new BufferedInputStream(request.inputStream), jsonBytes)
            String json = new String(IOUtilities.uncompressBytes(jsonBytes), "UTF-8")

            if (LOG.debugEnabled)
            {
                LOG.debug("HTTP POST(${request.pathInfo}), body=${json}")
            }
            handleRequestAndResponse(request, response, json)
        }
        catch (Exception e)
        {
            String msg = "error: Unable to read HTTP-POST JSON content from URI: ${request.requestURI}. Message: ${e.message}"
            LOG.warn(msg)
            sendJsonResponse(request, response, new Envelope(msg, false, e))
        }
        finally
        {
            removeThreadLocals()
        }
    }

    /**
     * Remove ThreadLocals containing HttpServletRequest and response so that when thread is returned
     * to threadpool there are no lingering references to these values.
     */
    private static void removeThreadLocals()
    {
        servletRequest.remove()
        servletResponse.remove()
    }

    /**
     * @param request HttpServletRequest passed to the command servlet.
     * @return Either a JsonCommandServlet ConfigurationProvider or an Envelope instance,
     * if unable to obtain the configuration provider.  The URL must contain /controller/method,
     * and then if the controller name can be found in the n-cube provider, then the ncubeCfgProvider
     * is returned, otherwise the springCfgProvider is checked.  If the controller name can be
     * found there, then the springCfgProvider is returned.  If the controller cannot be located
     * by name from either provider, and Envelope is returned, indicating this error.
     */
    private Object getController(HttpServletRequest request)
    {
        Matcher matcher = ConfigurationProvider.getUrlMatcher(request)
        if (matcher == null)
        {
            throw new IllegalArgumentException("error: Invalid JSON request - /controller/method not specified")
        }

        final String controllerName = matcher.group(1)
        Object controller = configProvider?.getController(controllerName)
        return controller
    }

    /**
     * This is the main driver of the command servlet.  This code obtains the appropriate provider,
     * calls the appropriate method on the provider, and then optionally writes the return response,
     * if the called method did not write to the HTTP response.
     * @param request HttpServletRequest passed to the command servlet.
     * @param response HttpServletResponse passed to the command servlet.
     * @param json String arguments that were passed in JSON format.
     */
    private void handleRequestAndResponse(HttpServletRequest request, HttpServletResponse response, String json)
    {
        Envelope envelope
        Object controller
        try
        {
//            useful for debugging
//            LOG.info("JSON request: ${json}")
            controller = getController(request)
            Object result = configProvider.callController(controller, request, json)
            envelope = new Envelope(result, true)
        }
        catch (ThreadDeath d)
        {
            throw d
        }
        catch (Throwable e)
        {
            Map altMsg = null
            if (e instanceof InvocationTargetException)
            {   // Error occurred within Controller
                String[] pieces
                if (controller)
                {
                    pieces = controller.inspect().split("@")
                }
                else
                {
                    pieces = new String[1]
                    pieces[0] = JsonCommandServlet.class.name
                }

                e = e.cause
                altMsg = buildLogMessages(e, pieces[0] + '.')
                LOG.info("Controller threw an exception, request: ${request.requestURI}:\n${altMsg.log}")
            }
            else if (e instanceof IllegalArgumentException || e instanceof JsonIoException)
            {   // Error occurred within this servlet, attempting to parse args, find method, locating controller, etc.
                // Exception intentionally not passed on (REST argument errors)
                LOG.info("${e.message}\n  URI: ${request.requestURI}\n  JSON argument: ${json}")
            }
            else
            {
                LOG.warn("Unexpected exception, request: ${request.requestURI}:", e)
            }
            
            // Handle response in case of unhandled exception by controller
            String msg = altMsg?.msg ?: e.message
            if (StringUtilities.isEmpty(msg))
            {
                msg = e.class.name
            }
            envelope = new Envelope("${msg} from URI: ${request.requestURI}", false, e)

            if (e instanceof IOException)
            {
                if ("org.apache.catalina.connector.ClientAbortException" == e.class.name)
                {
                    LOG.info("Client aborted connection while processing JSON request.")
                }
                else
                {
                    sendJsonResponse(request, response, new Envelope("error: Invalid JSON request made from URI: ${request.requestURI}.", false, e))
                }
            }
            else if (e instanceof AccessControlException)
            {
                sendJsonResponse(request, response, new Envelope("error: Your session with our website appears to have ended.  Please log out and back in.", false, e))
            }
            else
            {
                sendJsonResponse(request, response, envelope)
            }
            return
        }

        // Handle response (if the method did not)
        if (!response.committed)
        {
	        // Send JSON result
	        long start = System.nanoTime()
	        sendJsonResponse(request, response, (Envelope) envelope)
	        long end = System.nanoTime()

	        if (end - start > 2000000000)
	        {    // Total time more than 2 seconds
	            if (json.length() > 256)
	            {
	                json = json.substring(0, 255)
	            }
                long time = Math.round((end - start) / 1000000.0d)
                LOG.info("[SLOW - ${time} ms] response: ${json}")
	        }
        }
    }

    /**
     * Build and send the response Envelope to the client.
     * @param request original servlet request
     * @param response original servlet response
     * @param envelope data and status to be written
     */
    private static void sendJsonResponse(HttpServletRequest request, HttpServletResponse response, Envelope envelope)
    {
        try
        {
            if (response.committed)
            {   // Cannot write, response has already been committed.
                return
            }
            response.contentType = "application/json"
            response.setHeader("Cache-Control", "private, no-cache, no-store")
            writeResponse(request, response, envelope)
        }
        catch (ThreadDeath t)
        {
            throw t
        }
        catch (Throwable t)
        {
            t = getDeepestException(t)
            String msg = t.class.name
            if (t.message != null)
            {
                msg += ' ' + t.message
            }

            if (t instanceof IOException)
            {
                if ("org.apache.catalina.connector.ClientAbortException" == t.class.name)
                {
                    LOG.info("Client aborted connection while processing JSON request.")
                }
                else
                {
                    LOG.warn("IOException - sending response: ${msg}")
                }
            }
            else if (t instanceof AccessControlException)
            {
                LOG.warn("AccessControlException - sending response: ${msg}")
            }
            else
            {
                LOG.warn("An unexpected exception occurred sending JSON response to client", t)
            }
        }
    }
    
    /**
     * Write the response Envelope to the client
     * @param request original servlet request
     * @param response original servlet response
     * @param json String response to write
     */
    private static void writeResponse(HttpServletRequest request, HttpServletResponse response, Envelope envelope)
    {
        //  Header can be null coming from other WebClients (such as .NET client)
        String header = request.getHeader('Accept-Encoding')
        OutputStream outputStream

        if (header?.contains("gzip"))
        {
            response.setHeader("Content-Encoding", "gzip")
            outputStream = new AdjustableGZIPOutputStream(new BufferedOutputStream(response.outputStream), Deflater.BEST_SPEED)
        }
        else
        {
            outputStream = new BufferedOutputStream(response.outputStream)
        }

        JsonWriter writer = new JsonWriter(outputStream)
        Map map = envelope as Map
        writer.write(map)
        writer.flush()
        writer.close()
    }

    private static String getExceptionAsJsonObjectString(Throwable t)
    {
        if (t == null)
        {
            return 'null'
        }

        try
        {
            String json = JsonWriter.objectToJson(t)
            return json
        }
        catch (Exception e)
        {
            String json = JsonWriter.objectToJson(new RuntimeException("Unable to serialize exception. Exception message: ${e.message}, type: ${t.class.name}"))
            return json
        }
    }

    /**
     * Get the deepest (original cause) of the exception chain.
     * @param e Throwable exception that occurred.
     * @return Throwable original (causal) exception
     */
    static Throwable getDeepestException(Throwable e)
    {
        while (e.cause != null)
        {
            e = e.cause
        }

        if (!(e instanceof AccessControlException || e instanceof IOException))
        {
            LOG.warn("Exception occurred: ", e)
        }
        else
        {
            String msg = e.class.name
            if (e.message != null)
            {
                msg = msg + ' ' + e.message
            }

            LOG.warn("Exception occurred: ${msg}")
        }

        return e
    }

    private static Map buildLogMessages(Throwable t, String startPattern)
    {
        // Step 1: Build full stack trace String, store it in output.log
        Map output = [log: buildStack(t, startPattern)]
        StringBuilder sb = new StringBuilder()

        // Step 2: Build 'user-friendly stack trace error message (all trace.message's), store it in output.msg
        while (t != null)
        {
            boolean isEmpty = sb.length() == 0
            String msg = t.message
            if (StringUtilities.isEmpty(msg))
            {
                msg = "${t.class.name}"
            }
            sb.append(isEmpty ? msg : "  Caused by ${msg}")
            sb.append('\n\n')
            t = t.cause
        }
        output.msg = sb.toString()
        return output
    }

    private static String buildStack(Throwable t, String startPattern)
    {
        StringBuilder s = new StringBuilder()
        int i = 0

        while (t)
        {
            String msg = t.message
            if (StringUtilities.isEmpty(msg))
            {
                msg = '(no message)'
            }
            if (i++ == 0)
            {
                s.append("${t.class.name}: ${msg}\n")
            }
            else
            {
                s.append("\n  Caused by ${t.class.name}: ${msg}\n")
            }
            
            StackTraceElement[] stack = trimStack(t, startPattern)
            StackTraceElement[] nextStack = trimStack(t.cause, startPattern)
            s.append(trace(stack, nextStack))
            t = t.cause
        }

        return s.toString()
    }

    private static String trace(StackTraceElement[] stackTrace, StackTraceElement[] nextStrackTrace)
    {
        StringBuilder s = new StringBuilder()
        int len = stackTrace.length
        for (int i=0; i < len; i++)
        {
            s.append('    ')
            StackTraceElement element = stackTrace[i]
            if (alreadyExists(element, nextStrackTrace))
            {
                s.append('...')
                return s.toString()
            }
            else
            {
                s.append(element.toString())
                s.append('\n')
            }
        }
        return s.toString()
    }

    private static boolean alreadyExists(StackTraceElement element, StackTraceElement[] stackTrace)
    {
        if (ArrayUtilities.isEmpty(stackTrace))
        {
            return false
        }

        for (StackTraceElement traceElement : stackTrace)
        {
            if (element == traceElement)
            {
                return true
            }
        }
        return false
    }

    private static StackTraceElement[] trimStack(Throwable t, String startPattern)
    {
        if (t == null)
        {
            return null
        }
        StackTraceElement[] elements = t.stackTrace
        int len = elements.length
        LinkedList<StackTraceElement> messages = []

        for (int i=0; i < len; i++)
        {
            messages.add(elements[i])
        }

        boolean found = false
        Iterator<StackTraceElement> i = messages.descendingIterator()
        LinkedList<StackTraceElement> trimmed = []

        while (i.hasNext())
        {
            StackTraceElement stackEntry = i.next()
            if (stackEntry.toString().startsWith(startPattern))
            {
                found = true
            }
            if (found)
            {
                trimmed.push(stackEntry)
            }
        }

        if (!found)
        {   // Pattern never found, use entire stack
            trimmed = messages
        }

        return trimmed.toArray(new StackTraceElement[0])
    }
}
