/*
 * Copyright 2003,2004 The Apache Software Foundation.
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
package org.apache.axis.encoding;

import org.apache.axis.AbstractTestCase;
import org.apache.axis.engine.AxisFault;
import org.apache.axis.impl.providers.SimpleJavaProvider;
import org.apache.axis.om.OMElement;
import org.apache.axis.om.OMFactory;
import org.apache.axis.om.OMNamespace;

import java.lang.reflect.Method;


public class EncodingTest extends AbstractTestCase {

    /**
     * @param testName
     */
    public EncodingTest(String testName) {
        super(testName);
    }
    
    
    public void testDeserializingInt() throws SecurityException, NoSuchMethodException, AxisFault{
        Method method = Echo.class.getMethod("echoInt",new Class[]{int.class});
        OMNamespace omNs = OMFactory.newInstance().createOMNamespace("http://host/my","my");
        OMFactory omfac = OMFactory.newInstance();
        OMElement omel = omfac.createOMElement("value",omNs);
        omel.addChild(omfac.createText("1234"));
        
        SimpleJavaProvider sjp = new SimpleJavaProvider();
        sjp.deserializeParameters(omel.getPullParser(false),method);
    }


    public void testDeserializingString() throws SecurityException, NoSuchMethodException, AxisFault{
        Method method = Echo.class.getMethod("echoInt",new Class[]{int.class});
        OMNamespace omNs = OMFactory.newInstance().createOMNamespace("http://host/my","my");
        OMFactory omfac = OMFactory.newInstance();
        OMElement omel = omfac.createOMElement("value",omNs);
        omel.addChild(omfac.createText("1234"));
        
        SimpleJavaProvider sjp = new SimpleJavaProvider();
        sjp.deserializeParameters(omel.getPullParser(false),method);
    }
    
    public class Echo{
        public int echoInt(int intVal){
            return intVal;
        }
    }

}
