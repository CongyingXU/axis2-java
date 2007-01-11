/*
 * Copyright 2004,2005 The Apache Software Foundation.
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

package org.apache.axis2.engine;

import junit.framework.TestCase;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.context.ServiceGroupContext;
import org.apache.axis2.description.AxisMessage;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.AxisServiceGroup;
import org.apache.axis2.description.HandlerDescription;
import org.apache.axis2.description.InOutAxisOperation;
import org.apache.axis2.description.TransportInDescription;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.handlers.AbstractHandler;
import org.apache.axis2.receivers.RawXMLINOnlyMessageReceiver;
import org.apache.axis2.receivers.RawXMLINOutMessageReceiver;
import org.apache.axis2.transport.http.CommonsHTTPTransportSender;
import org.apache.axis2.transport.http.SimpleHTTPServer;
import org.apache.axis2.util.ObjectStateUtils;
import org.apache.axis2.util.UUIDGenerator;
import org.apache.axis2.wsdl.WSDLConstants;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;

public class MessageContextChangeTest extends TestCase 
{
    private String testArg = null;

    private static String mc_package = "org.apache.axis2.context.MessageContext";

    private FieldDescription[] knownList = {
        new FieldDescription("org.apache.commons.logging.Log", "log"),
        new FieldDescription("java.lang.String", "myClassName"),
        new FieldDescription("long", "serialVersionUID"),
        new FieldDescription("int", "REVISION_1"),
        new FieldDescription("int", "revisionID"),
        new FieldDescription("java.lang.ThreadLocal", "currentMessageContext"),
        new FieldDescription("org.apache.axis2.client.Options", "options"),
        new FieldDescription("int", "IN_FLOW"),
        new FieldDescription("int", "IN_FAULT_FLOW"),
        new FieldDescription("int", "OUT_FLOW"),
        new FieldDescription("int", "OUT_FAULT_FLOW"),
        new FieldDescription("java.lang.String", "REMOTE_ADDR"),
        new FieldDescription("java.lang.String", "TRANSPORT_HEADERS"),
        new FieldDescription("org.apache.axiom.attachments.Attachments", "attachments"),
        new FieldDescription("java.lang.String", "TRANSPORT_OUT"),
        new FieldDescription("java.lang.String", "TRANSPORT_IN"),
        new FieldDescription("java.lang.String", "CHARACTER_SET_ENCODING"),
        new FieldDescription("java.lang.String", "UTF_8"),
        new FieldDescription("java.lang.String", "UTF_16"),
        new FieldDescription("java.lang.String", "TRANSPORT_SUCCEED"),
        new FieldDescription("java.lang.String", "DEFAULT_CHAR_SET_ENCODING"),
        new FieldDescription("int", "FLOW"),
        new FieldDescription("java.lang.String", "TRANSPORT_NON_BLOCKING"),
        new FieldDescription("java.lang.String", "DISABLE_ASYNC_CALLBACK_ON_TRANSPORT_ERROR"),
        new FieldDescription("boolean", "processingFault"),
        new FieldDescription("boolean", "paused"),
        new FieldDescription("boolean", "outputWritten"),
        new FieldDescription("boolean", "newThreadRequired"),
        new FieldDescription("boolean", "isSOAP11"),
        new FieldDescription("java.util.ArrayList", "executionChain"),
        new FieldDescription("java.util.LinkedList", "inboundExecutedPhases"),
        new FieldDescription("java.util.LinkedList", "outboundExecutedPhases"),
        new FieldDescription("boolean", "doingREST"),
        new FieldDescription("boolean", "doingMTOM"),
        new FieldDescription("boolean", "doingSwA"),
        new FieldDescription("org.apache.axis2.description.AxisMessage", "axisMessage"),
        new FieldDescription("org.apache.axis2.description.AxisOperation", "axisOperation"),
        new FieldDescription("org.apache.axis2.description.AxisService", "axisService"),
        new FieldDescription("org.apache.axis2.description.AxisServiceGroup", "axisServiceGroup"),
        new FieldDescription("org.apache.axis2.context.ConfigurationContext", "configurationContext"),
        new FieldDescription("int", "currentHandlerIndex"),
        new FieldDescription("int", "currentPhaseIndex"),
        new FieldDescription("org.apache.axiom.soap.SOAPEnvelope", "envelope"),
        new FieldDescription("org.apache.axis2.context.OperationContext", "operationContext"),
        new FieldDescription("boolean", "responseWritten"),
        new FieldDescription("boolean", "serverSide"),
        new FieldDescription("org.apache.axis2.context.ServiceContext", "serviceContext"),
        new FieldDescription("java.lang.String", "serviceContextID"),
        new FieldDescription("org.apache.axis2.context.ServiceGroupContext", "serviceGroupContext"),
        new FieldDescription("java.lang.String", "serviceGroupContextId"),
        new FieldDescription("org.apache.axis2.context.SessionContext", "sessionContext"),
        new FieldDescription("org.apache.axis2.description.TransportOutDescription", "transportOut"),
        new FieldDescription("org.apache.axis2.description.TransportInDescription", "transportIn"),
        new FieldDescription("java.lang.String", "incomingTransportName"),
        new FieldDescription("java.util.LinkedHashMap", "selfManagedDataMap"),
        new FieldDescription("boolean", "needsToBeReconciled"),
        new FieldDescription("int", "selfManagedDataHandlerCount"),
        new FieldDescription("java.util.ArrayList", "selfManagedDataListHolder"),
        new FieldDescription("java.util.ArrayList", "metaExecutionChain"),
        new FieldDescription("java.util.LinkedList", "metaInboundExecuted"),
        new FieldDescription("java.util.LinkedList", "metaOutboundExecuted"),
        new FieldDescription("int", "metaHandlerIndex"),
        new FieldDescription("int", "metaPhaseIndex"),
        new FieldDescription("org.apache.axis2.util.MetaDataEntry", "metaAxisOperation"),
        new FieldDescription("org.apache.axis2.util.MetaDataEntry", "metaAxisService"),
        new FieldDescription("org.apache.axis2.util.MetaDataEntry", "metaAxisServiceGroup"),
        new FieldDescription("org.apache.axis2.context.OperationContext", "metaOperationContext"),
        new FieldDescription("org.apache.axis2.context.ServiceContext", "metaServiceContext"),
        new FieldDescription("org.apache.axis2.context.ServiceGroupContext", "metaServiceGroupContext"),
        new FieldDescription("org.apache.axis2.util.MetaDataEntry", "metaTransportOut"),
        new FieldDescription("org.apache.axis2.util.MetaDataEntry", "metaTransportIn"),
        new FieldDescription("org.apache.axis2.util.MetaDataEntry", "metaAxisMessage"),
        new FieldDescription("boolean", "reconcileAxisMessage"),
        new FieldDescription("boolean", "inboundReset"),
        new FieldDescription("boolean", "outboundReset"),
        new FieldDescription("java.lang.String", "selfManagedDataDelimiter"),
        new FieldDescription("java.lang.Class", "class$org$apache$axis2$context$MessageContext"),
        new FieldDescription("java.lang.Class", "class$org$apache$axis2$context$SelfManagedDataManager")
    };


    public MessageContextChangeTest(String arg0)
    {
        super(arg0);
        testArg = new String(arg0);
    }


    public void testChange() throws Exception 
    {
        boolean noChange = true;

        MessageContext mc = new MessageContext();

        Class mcClass = mc.getClass();

        Field [] fields = mcClass.getDeclaredFields();
        int numberFields = fields.length;

        int numberKnownFields = knownList.length;

        if (numberKnownFields != numberFields)
        {
            System.out.println("ERROR: number of actual fields ["+numberFields+"] in MessageContext does not match the expected number ["+numberKnownFields+"]");
            noChange = false;
        }

        // first check the expected fields with the actual fields

        for (int i=0; i<numberKnownFields; i++)
        {
            // see if this entry is in the actual list
            String name = knownList[i].getName();

            Field actualField = findField(fields, name);

            if (actualField == null)
            {
                System.out.println("ERROR:  MessageContext is missing field ["+name+"]");
                noChange = false;
            }
            else
            {
                String knownType = knownList[i].getType();
                String actualType = actualField.getType().getName();

                if (!knownType.equals(actualType))
                {
                    System.out.println("ERROR:  MessageContext field ["+name+"] expected type ["+knownType+"] does not match actual type ["+actualType+"]");
                    noChange = false;
                }
            }
        }


        // next, check the actual fields with the predefined, known fields

        for (int j=0; j<numberFields; j++)
        {
            String description = fields[j].toString();

            // see if this entry is in the predefined list
            String name = fields[j].getName();

            FieldDescription fd = findFieldDescription(name);

            if (fd == null)
            {
                System.out.println("ERROR:  MessageContext has new field ["+description+"] that needs to be assessed for message context save/restore functions");
                noChange = false;
            }
            else
            {
                String knownType = fd.getType();
                String actualType = fields[j].getType().getName();

                if (!knownType.equals(actualType))
                {
                    System.out.println("ERROR:  MessageContext field ["+name+"] expected type ["+knownType+"] does not match actual type ["+actualType+"]");
                    noChange = false;
                }
            }

        }

        assertTrue(noChange);

    }

    private Field findField(Field[] fields, String name)
    {

        //System.out.println("findField:  looking for ["+name+"]");

        for (int k=0; k<fields.length; k++)
        {
            String fieldName = fields[k].getName();
            //System.out.println("fieldName["+k+"] =  ["+fieldName+"]");

            if (fieldName.equals(name))
            {
                return fields[k];
            }
        }
        return null;
    }

    private FieldDescription findFieldDescription(String name)
    {
        for (int k=0; k<knownList.length; k++)
        {
            String fieldName = knownList[k].getName();
            if (fieldName.equals(name))
            {
                return knownList[k];
            }
        }
        return null;
    }



    private class FieldDescription
    {
        String type = null;
        String name = null;

        // constructor
        public FieldDescription()
        {
        }

        // constructor
        public FieldDescription(String t, String n)
        {
            type = t;
            name = n;
        }

        public String getType()
        {
            return type;
        }

        public String getName()
        {
            return name;
        }

        public void setType(String t)
        {
            type = t;
        }

        public void setName(String n)
        {
            name = n;
        }
    }

}
