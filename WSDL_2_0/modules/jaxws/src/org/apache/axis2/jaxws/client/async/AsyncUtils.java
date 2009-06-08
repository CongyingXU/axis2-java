/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.axis2.jaxws.client.async;

import org.apache.axis2.client.async.AsyncResult;
import org.apache.axis2.jaxws.ExceptionFactory;
import org.apache.axis2.jaxws.core.MessageContext;
import org.apache.axis2.jaxws.message.MessageException;
import org.apache.axis2.jaxws.util.Constants;
import org.apache.axis2.util.ThreadContextMigratorUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class AsyncUtils {

    private static final Log log = LogFactory.getLog(AsyncUtils.class);
    
    public static MessageContext createMessageContext(AsyncResult result) throws MessageException {
        boolean debug = log.isDebugEnabled();
        MessageContext response = null;
        
        if (debug) {
            log.debug("Creating response MessageContext");
        }
        
        // Create the JAX-WS response MessageContext from the Axis2 response
        org.apache.axis2.context.MessageContext axisResponse = result.getResponseMessageContext();
        response = new MessageContext(axisResponse);
        
        // REVIEW: Are we on the final thread of execution here or does this get handed off to the executor?
        // TODO: Remove workaround for WS-Addressing running in thin client (non-server) environment
        try {
            ThreadContextMigratorUtil.performMigrationToThread(Constants.THREAD_CONTEXT_MIGRATOR_LIST_ID, axisResponse);
        }
        catch (Throwable t) {
            if (debug) {
                log.debug("An error occurred in the ThreadContextMigratorUtil " + t);
                log.debug("...caused by " + t.getCause());
            }
            ExceptionFactory.makeWebServiceException(t);
        }
        
        return response;
    }
}