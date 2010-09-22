/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.axis2.misc;

import org.apache.axis2.AbstractTestCase;
import org.apache.axis2.AxisFault;
import org.apache.axis2.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

public class MiscTest extends AbstractTestCase {

    /**
     * @param testName
     */
    public MiscTest(String testName) {
        super(testName);
    }


    public void testAxisFault() {
        Exception e = new InvocationTargetException(new Exception());
        assertNotSame(AxisFault.makeFault(e), e);

        e = new AxisFault("");
    }

    public void testStringWriterConversion() throws Exception {
        String input = " Some text \u00df with \u00fc special \u00f6 chars \u00e4. \n";
        
        String charset = "utf-8";
        byte[] bytes = input.getBytes(charset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(bytes);
        baos.flush();
        StringWriter sw = Utils.String2StringWriter(Utils.BAOS2String(baos, charset));
        assertTrue(input.equals(sw.toString()));
        
        charset = "utf-16";
        bytes = input.getBytes(charset);
        baos = new ByteArrayOutputStream();
        baos.write(bytes);
        baos.flush();
        sw = Utils.String2StringWriter(Utils.BAOS2String(baos, charset));
        assertTrue(input.equals(sw.toString()));
       
    }
}
