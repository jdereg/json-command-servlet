json-command-servlet
====================
[![Build Status](https://travis-ci.org/jdereg/json-command-servlet.svg?branch=master)](https://travis-ci.org/jdereg/json-command-servlet)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/json-command-servlet/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.cedarsoftware/json-command-servlet)
[![Javadocs](http://www.javadoc.io/badge/com.cedarsoftware/json-command-servlet.svg?color=brightgreen)](http://www.javadoc.io/doc/com.cedarsoftware/json-command-servlet)

Java servlet that processes REST requests and returns JSON responses.

To include in your project:
```
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-command-servlet</artifactId>
  <version>1.9.0</version>
</dependency>
```
### Sponsors
[![Alt text](https://www.yourkit.com/images/yklogo.png "YourKit")](https://www.yourkit.com/.net/profiler/index.jsp)

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.

<a href="https://www.jetbrains.com/idea/"><img alt="Intellij IDEA from JetBrains" src="https://s-media-cache-ak0.pinimg.com/236x/bd/f4/90/bdf49052dd79aa1e1fc2270a02ba783c.jpg" data-canonical-src="https://s-media-cache-ak0.pinimg.com/236x/bd/f4/90/bdf49052dd79aa1e1fc2270a02ba783c.jpg" width="100" height="100" /></a>
**Intellij IDEA**
___
REST calls can be sent as HTTP GET or POST requests.  To make a POST request, it should be formatted like this:

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
message is the "result" and the status is false, and there will be an `exception` field in the response envelope.  See 
*json-io* for a Java-script `call()` method that makes the REST request for you with a very simple notation.  Use 
`call("FooController.barMethod", [])`

In order for a `Controller` class (That is the standard name of classes that are callable externally) to be called,
it must have the Spring annotation `@RestController`.  This can be added directly to the class, or to an interface
that the class inherits from.

By default, marking a class as `@RestController`, all public methods are available to be called externally (through
this servlet).  If you have a public method that you do not want called, place the Java annotation `@ControllerMethod`
on the method with `allow=false` `[@ControllerMethod(allow = false)]`.  The `JsonCommandServlet` will honor this annotation
and return a JSON error message indicating that a method cannot be accessed remotely via this command servlet.

No special annotations have to be added to your methods to be called externally.  Simply add the `@RestController`
annotation to the Controller or its interface, and the public methods will be accessible.

This effectively makes all Controller method's like mini-servlets. If you need access to the `HttpServletRequest` or
`HttpServletResponse` objects within your Controller method, you can access `JsonCommandServlet.servletRequest.get()` or
`JsonCommandServlet.servletResponse.get()`.  The command servlet places the request and response objects on a `ThreadLocal`
and makes it available for the lifespace of your method call.

If you need to stream data back (instead of having a normal return value from a controller method), you can use 
the `JsonCommandServlet.servletResponse.get()` to obtain the `HttpServletResponse` object, and then use the response 
object set HTTP Response headers and to get the `OutputStream` to write to.  You may be wondering, how do I prevent 
the `JsonCommandServlet` from writing a JSON standard response object instead of the streamed data?  `JsonCommandServlet` 
will detect that the output stream has been written to, and will not write the standard return (JSON) envelope.

Setup
=====
Configure `JsonCommandServlet` using either `web.xml` or `UrlRewrite.xml` (tuckey.org).  Example `urlrewrite.xml` configuration:
    <?xml version="1.0" encoding="utf-8"?>
    <!DOCTYPE urlrewrite PUBLIC "-//tuckey.org//DTD UrlRewrite 4.0//EN"
            "http://www.tuckey.org/res/dtds/urlrewrite4.0.dtd">
    
    <urlrewrite>
        <rule match-type="regex">
            <note>
                Redirect inbound requests using n-cube's sys.classpath
            </note>
            <from>^/sys/[^/]+/.*$</from>
            <set name="sys.classpath.prefix">sys</set>
            <run class="com.cedarsoftware.util.ProxyRouter" method="route" />
            <to>null</to>
        </rule>
    
        <!-- Route all HTTP GET/PUT JSON commands to the JsonCommandServlet -->
        <rule match-type="regex">
            <note>
                Redirect inbound requests using n-cube's sys.classpath
            </note>
            <from>^/cmd/[^/]+/.*$</from>
            <run class="com.cedarsoftware.servlet.JsonCommandServlet" method="route"/>
            <to>null</to>
        </rule>
    
    </urlrewrite>


See the [jsonUtil.js](https://github.com/jdereg/json-io/blob/master/jsonUtil.js) file that ships with **json-io** for an easy way to make REST calls
from Javascript.

See [changelog.md](/changelog.md) for revision history.

By: John DeRegnaucourt
