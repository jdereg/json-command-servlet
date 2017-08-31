### Revision History
* 1.6.5
  * Added className to exception (in addition to message)
  * Updated to java-util 1.28.2
* 1.6.4
  * Enhancement: Added support for compressed HTTP POST data
* 1.6.3
  * Code clean up
* 1.6.2
  * Enhancement: Improved log handling with nested exceptions
* 1.6.1
  * Enhancement: When logging exceptions, trim them back to the Controller entry when it is known to have made it into the controller. 
* 1.6.0
  * Enhancement: When logging exceptions thrown by the targeted controller, if it has no nested exception, only log the portion of the stack trace starting from the `JsonCommandServlet`.  Since the error occurred inside the controller, the controller name, method, and argument list were all valid (enough to make the call.)
* 1.5.5
  * Added request URI to errors where the controller / method were not being displayed.
  * Enums processed with `Converter.convert()` during argument processing (using newer java-util `Converter.convert()` which handles `enum` to `String`)  
* 1.5.4
  * Bug fix: Enums no longer processed with `Converter.convert()` during argument processing
* 1.5.3
  * Bug fix: Removed URI decoding from `doPost()`
* 1.5.2
  * Enhancement: Consumed json-io 4.9.12
  * Enhancement: Utilized Spring `@RestController` instead of having on `@ControllerClass`.
* 1.5.1
  * Enhancement: Exception handling improved. 
* 1.5.0
  * Enhancement: Return envelope expanded to contain 'exception' field (exception serialized in JSON)
  * Enhancement: No long allowing ThreadLocal request attributes to override envelope (Controller still can write entire response - streaming case).
  * Updated library dependences (json-io 4.9.11, groovy 2.4.10)
* 1.4.3
  * Enhancement: All POSTed data is URLDecoded to UTF-8 
* 1.4.2
  * Enhancement: `Content-Type` on POST requests inspected for 'x-www-form-urlencoded' in Content-Type HTTP header to determine if URI decoding should be optionally performed on post-data. 
* 1.4.1
  * Moved N-CubeConfigurationProvider from json-command-servlet to the n-cube project.
* 1.4.0
  * Updated to handle URI / URL encoded URL arguments
* 1.3.3
  * GMavenPlus used to compile
  * javadoc.io hosted docs (javadoc hosted there, link available on github readme)
  * updated to json-io 4.7.0
* 1.3.2
  * Documentation updates
  * travis-ci support
  * updated to json-io 4.6.0
* 1.3.1
  * Updated to json-io 4.3.0
* 1.3.0
  * JsonCommandServlet can now be routed to via UrlRewrite (tuckey.org).  This elminates the need to configure web.xml for setting up JsonCommandServlet.  See docs above for the UrlRewrite.xml configuration example.
  * Updated dependent library versions
* 1.2.5
  * Updated dependent library versions
* 1.2.4
  * Updated dependent library versions
* 1.2.3
  * Allow for N-Cube or Spring (or both) to be ApplicationContext sources.
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
  * Added ability for the REST called method to set the return status directly along with a text message.  This is done by using the request's attributes.
* 1.0.0
  * Official release of JsonCommandServlet
