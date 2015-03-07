package com.cedarsoftware.servlet

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeManager
import groovy.transform.CompileStatic
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import javax.servlet.ServletConfig

/**
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
class NCubeConfigurationProvider extends ConfigurationProvider
{
    private static final Logger LOG = LogManager.getLogger(NCubeConfigurationProvider.class)
    private final ApplicationID appId
    private static final Set allowedMethods = [
            'containsCell',
            'containsCellById',
            'getApplicationID',
            'getAxes',
            'getAxis',
            'getAxisFromColumnId',
            'getCell',
            'getCellById',
            'getCellByIdNoExecute',
            'getCellMap',
            'getDefaultCellValue',
            'getDeltaDescription',
            'getMap',
            'getMetaProperties',
            'getMetaProperty',
            'getName',
            'getNumCells',
            'getNumDimensions',
            'getOptionalScope',
            'getReferencedCubeNames',
            'getRequiredScope',
            'getRuleInfo',
            'sha1',
            'toFormattedJson',
            'toHtml',
            'validateCubeName'] as HashSet

    NCubeConfigurationProvider(ServletConfig servletConfig)
    {
        super(servletConfig)
        String tenant = getServletConfig().getInitParameter("tenant")
        ApplicationID.validateTenant(tenant)
        String app = getServletConfig().getInitParameter("app")
        ApplicationID.validateApp(app)
        appId = NCubeManager.getApplicationID(tenant, app, new HashMap())
    }

    /**
     * @param cubeName String name of an NCube.
     * @return NCube if successful, otherwise an error Envelope instance is returned with the
     * appropriate error message already filled in, and the status set to false.
     */
    protected Object getController(String cubeName)
    {
        try
        {
            NCube ncube = NCubeManager.getCube(appId, cubeName)
            if (ncube == null)
            {
                return new Envelope("error: Invalid target '" + cubeName + "'.", false)
            }
            return ncube
        }
        catch(Exception e)
        {
            LOG.warn("Invalid controller target (not found) : " + cubeName)
            return new Envelope("error: Invalid target '" + cubeName + "'.", false)
        }
    }

    /**
     * Only read-only methods can be called.  They are checked against a list to ensure.
     * @param methodName String method name to check.
     * @return true if the method can be called, false otherwise.
     */
    protected boolean isMethodAllowed(String methodName)
    {
        return allowedMethods.contains(methodName)
    }

    /**
     * @return String 'ncube' to indicate that an N-Cube controller was used.
     */
    protected String getLogPrefix()
    {
        return 'ncube'
    }
}
