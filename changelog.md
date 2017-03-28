### Revision History
* 1.5.0
  * Enhancement: 
* 1.4.3
  * Enhancement: All POSTed data is URLDecoded to UTF-8 
* 1.4.2
  * Enhancement: Content-Type on POST requests inspected for 'x-www-form-urlencoded' in Content-Type HTTP header to determine if URI decoding should be optionally performed on post-data. 
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
