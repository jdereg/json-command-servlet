json-command-servlet
====================
Java servlet that processes Ajax / XHR requests and returns JSON responses.

To include in your project:
```
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-command-servlet</artifactId>
  <version>1.1.1</version>
</dependency>
```
<a class="coinbase-button" data-code="085e7852d6f8c97474d5a8d74307a49f" data-button-style="custom_large" data-custom="json-command-servlet" href="https://coinbase.com/checkouts/085e7852d6f8c97474d5a8d74307a49f">Feed hungry developers...</a><script src="https://coinbase.com/assets/button.js" type="text/javascript"></script>

XHR / Ajax calls can be sent as HTTP GET or POST requests.  To make a request, it should be formatted like this:

HTTP://mycompany.com/Context/controller/method
[HTTP HEADERS]
CR LF CR LF
[arguments]

where:

'controller' is the bean name of a controller that exposes methods that can be publicly called.
'method' is the name of the method to be called on the controller.
[arguments] is a JSON array of arguments matching the arguments required by the method.

The method will be called, and then the return value from the method will be written in JSON format, like this:

{"status":true,"result": [result value in JSON]}   # Result not in array brackets unless result is array

where 'status' is true if the method called returned without exception, otherwise 'status' is false.  The 'result'
is the JSON string-ified version of the method's return value.  If the method throws an exception, the exception
message is the "result" and the status is false.  See json-io for a Java-script 'call()' method that makes
the Ajax request for you with a very simple notation.  Use call("FooController.barMethod", []);

In order for a 'Controller' class (That is the standard name of classes that are callable externally) to be called,
it must have the Java annotation @ControllerClass.  This can be added directly to the class, or to an interface
that the class inherits from.

By default, marking a class as @ControllerClass, all public methods are available to be called externally (through
this servlet).  If you have a public method that you do not want called, place the Java annotation @ControllerMethod
on the method with allow=false [@ControllerMethod(allow = false)].  The JsonCommandServlet will honor this annotation
and return a JSON error message indicating that a method could not be accessed.

No special annotations have to be added to your methods to be called externally.  Simply add the @ControllerClass
annotation to the Controller or its interface, and the public methods will be accessible.

This effectively makes all Controller method's like mini-servlets. If you need access to the HttpServletRequest or
HttpServletResponse objects within your Controller method, you can access JsonCommandServlet.servletRequest.get() or
JsonCommandServlet.servletResponse.get().  The command servlet places the request and response objects on a ThreadLocal
and makes it available for the lifespace of your method call.

If you need to stream data back, you can use the JsonCommandServlet.servletResponse.get() to obtain the HttpServletResponse
object, and then use the response object set HTTP Response headers and to get the output stream to write to.  You may be
wondering, how do I prevent the JsonCommandServlet from writing a JSON standard response object instead
of the streamed data?

For controller methods that need to stream data back, place the @HttpResponseHandler handler annotation on your method
and the JsonCommandServlet will not communicate back the HTTP response, but instead your method is expected to.  Use
the HttpServletResponse object obtained from the JsonCommandServlet.servletResponse.get() call to set HTTP Response
headers and to obtain the output stream to write to.

Version History
* 1.1.1
 * Updated to use latest versions of json-io and java-util.
* 1.1.0
 * Added ability for the ajax-called method to set the return status directly along with a text message.  This is done by using the request's attributes.
* 1.0.0
 * Official release of JsonCommandServlet

By: John DeRegnaucourt
