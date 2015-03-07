json-command-servlet
====================
Java servlet that processes Ajax / XHR requests and returns JSON responses.

To include in your project:
```
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-command-servlet</artifactId>
  <version>1.2.2</version>
</dependency>
```

[Donations welcome](https://coinbase.com/jdereg)

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

{"status":true,"data": [result value in JSON]}   # Result not in array brackets unless result is array

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

If you need to stream data back, you can use the JsonCommandServlet.servletResponse.get() to obtain the
HttpServletResponse object, and then use the response object set HTTP Response headers and to get the output stream to
write to.  You may be wondering, how do I prevent the JsonCommandServlet from writing a JSON standard response object
instead of the streamed data?  The JsonCommandServlet will detect that the output stream has been written to, and
will not write the standard return (JSON) envelope.

N-Cube
======
N-Cubes can be used as Controllers. To do so, make sure you set the 'tenant' value and 'app' value as init-params within
the web.xml configuration:

    <servlet>
        <description>JSON Servlet</description>
        <display-name>jsonServlet</display-name>
        <servlet-name>jsonServlet</servlet-name>
        <servlet-class>com.cedarsoftware.servlet.JsonCommandServlet</servlet-class>
        <init-param>
            <param-name>tenant</param-name>
            <param-value>Quasar</param-value>
        </init-param>
        <init-param>
            <param-name>app</param-name>
            <param-value>Pricing</param-value>
        </init-param>
    </servlet>

    <servlet-mapping>
        <servlet-name>jsonServlet</servlet-name>
        <url-pattern>/cmd/*</url-pattern>
    </servlet-mapping>

In the example above, the tenant is set to 'Quasar' and app is set to 'Pricing'.  The sys.bootstrap n-cube for this
tenant and app (at version 0.0.0, SNAPSHOT, HEAD)  will set the version, status, and branch.  This is done by returning
`new ApplicationID('Quasar', 'Pricing', '1.7.0', 'SNAPSHOT', 'HEAD')` from the sys.bootstrap cube.

When calling an n-cube controller from Javascript, the call looks like this:

    var result = call("apollo.getCell", [{method:'calcPrice', state:'OH'}]);

In this example, the n-cube `apollo` is located and method `getCell([method:'calcPrice', state:'OH'])` is invoked.
N-Cubes are called this way to allow you to have as many rules execute (and scoping) as desired.  You can call into a
lookup table (decision table), a decision tree (a cube that looks into other cubes, and so on), a rules cube, and a
template cube (mail-merge with replaceable parts).

Any read-only n-cube method can be called in this manner:
    `containsCell,
    containsCellById,
    getApplicationID,
    getAxes,
    getAxis,
    getAxisFromColumnId,
    getCell,
    getCellById,
    getCellByIdNoExecute,
    getCellMap,
    getDefaultCellValue,
    getDeltaDescription,
    getMap,
    getMetaProperties,
    getMetaProperty,
    getName,
    getNumCells,
    getNumDimensions,
    getOptionalScope,
    getReferencedCubeNames,
    getRequiredScope,
    getRuleInfo,
    sha1,
    toFormattedJson,
    toHtml,
    validateCubeName.`

See the n-cube documentation for what arguments are required to be passed into these methods.  The most common API to
call is `getCell(input, output)`, where input and output are Maps.

Constructing the n-cube controller
----------------------------------
One common technique for more functional controllers, is to create an n-cube with a String DISCRETE axis named `method`.
Each column (String) is a method name.  The associated cell is a GroovyExpression that will execute. You can have
additional scoping axes (as many as you want).  In order to call this one, the Javascript code would look like:
`call("apollo.getCell", [{method:'calcPrice',state:'OH'}]);` This will find the n-cube apollo, locate the 'method' axis
and select the `calcPrice` column, then locate the `state` axis and `OH` column (or `Default` if `OH` was not there),
then execute the cell at this location.  If the cell contains a simple value, it will be returned. If the cell is a
GroovyExpression, it will be executed.  The cell can have a URL to your groovy code allowing you to edit your code in your
favorite IDE (as well as single step debug it too), or the code could be 'inline' within the cell.

The image below is an example of what the n-cube controller looks like:
![Alt text](https://raw.githubusercontent.com/jdereg/json-command-servlet/master/ncubeScreenShot.png "n-cube Controller")

Note that you can have a 'Default' column on the method axis.  In this case, it acts like a 'Method Missing' (Groovy) or
a 'doesNotUnderstand' (Smalltalk).  This allows you to trap unknown method calls and do something in response.  Similarly,
note that there are only 3 state columns.  These do special pricing calculations (perhaps because of tax).  Yet the other
states are picked up automatically by the 'Default' column on the state axis.

As you get more familiar with this, you will see that n-cube Controllers are more powerful than traditional controllers,
with the added benefits of scoping your method calls (and rules), dynamic reloading (the code can be refreshed without
restarting web server), and the code is kept outside the ".war" file (in the case of Java web apps).

See the jsonUtils.js file that ships with [json-io](http://github.com/jdereg/json-io) for an easy way to make Ajax calls
from Javascript.

Version History
* 1.2.2
 * Added support for using n-cubes as controllers.  This allows dynamic programming (reload-able service side code) without restarting web container.
 * The JsonCommandServlet now automatically recognizes if a controller writes to the HTTP response output stream.  If so, it does not write the standard JSON envelope.
 * Filled out all Javadoc.
* 1.2.1
 * Added significant improvement in argument marshalling.  Maps are converted to the appropriate class if they match a non-Map argument.  All primitive arguments are set using java-util's Converter.convert() API.
 * Updated to use java-util 1.18.0.
 * Updated to use json-io 2.9.4.
* 1.2.0
 * Changed logging to Log4J2
 * Now using Spring 4.1.x
* 1.1.2
 * Updated to use latest versions of json-io and java-util.
 * Had issue with sonatype on 1.1.1
* 1.1.0
 * Added ability for the ajax-called method to set the return status directly along with a text message.  This is done by using the request's attributes.
* 1.0.0
 * Official release of JsonCommandServlet

By: John DeRegnaucourt
