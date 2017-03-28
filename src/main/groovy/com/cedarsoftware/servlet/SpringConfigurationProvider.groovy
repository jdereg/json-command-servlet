package com.cedarsoftware.servlet

import com.cedarsoftware.util.ReflectionUtils
import groovy.transform.CompileStatic
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.context.ApplicationContext
import org.springframework.web.context.support.WebApplicationContextUtils

import javax.servlet.ServletConfig
import java.lang.annotation.Annotation

/**
 * Spring configuration provider for the JsonCommandServlet.  This provider will
 * find controllers using AppCtx.getBean(), locating controller by name.  It will
 * then invoke the request method with the requested arguments on the controller.
 *
 * All appropriate error handling is performed.
 *
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
class SpringConfigurationProvider extends ConfigurationProvider
{
    private static final Logger LOG = LogManager.getLogger(SpringConfigurationProvider.class)
    private final ApplicationContext springAppCtx

    SpringConfigurationProvider(ServletConfig servletConfig)
    {
        super(servletConfig)
        springAppCtx = WebApplicationContextUtils.getWebApplicationContext(servletConfig.servletContext)
    }

    /**
     * All controller methods are allowed by default.  They can be turned off by making them
     * either not public -or- using the annotation @ControllerMethod(allow = false)}
     * @param methodName String method name to check.
     * @return true if the method can be called, false otherwise.
     */
    protected boolean isMethodAllowed(String methodName)
    {
        return true
    }

    /**
     * @return String 'spring' to indicate that a Spring controller was used.
     */
    protected String getLogPrefix()
    {
        return 'spring'
    }

    /**
     * @param name String name of a Controller instance (Spring bean name, n-cube name, etc).
     * @return Controller instance if successful, otherwise an error Envelope instance is
     * returned with the appropriate error message already filled in, and the status set to false.
     */
    protected Object getController(String name)
    {
        try
        {
            Object controller = springAppCtx.getBean(name)
            Class targetType = controller.class
            Annotation annotation = ReflectionUtils.getClassAnnotation(targetType, ControllerClass.class)
            if (annotation == null)
            {
                return new Envelope("error: target '${controller}' is not marked as a ControllerClass.", false)
            }
            return controller
        }
        catch(Exception e)
        {
            LOG.warn("Invalid controller target (not found): ${name}", e)
            return new Envelope("Could not locate controller named '${name}'. Error: ${e.message}", false, e)
        }
    }
}
