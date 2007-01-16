/*
 * Copyright 2006 The Apache Software Foundation.
 * Copyright 2006 International Business Machines Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.axis2.jaxws.core;

import javax.xml.ws.Binding;

/**
 * The InvocationContextFactory is used to create instances of an
 * InvocationContext.  
 */
public class InvocationContextFactory {
    
    /**
     * Creates an instance of an InvocationContext based on the Binding
     * that was passed in.  
     */
    public static InvocationContext createInvocationContext(Binding binding) {
        InvocationContextImpl ic = new InvocationContextImpl();
        if (binding != null) {
            ic.setHandlers(binding.getHandlerChain());        
        }
        
        return ic;
    }
}
