/*
 * Copyright 2004,2005 The Apache Software Foundation.
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
package org.apache.axis2.jaxws.message.impl;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.impl.llom.OMSourcedElementImpl;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.soap.SOAPFault;
import org.apache.axiom.soap.SOAPFaultCode;
import org.apache.axiom.soap.SOAPFaultDetail;
import org.apache.axiom.soap.SOAPFaultNode;
import org.apache.axiom.soap.SOAPFaultReason;
import org.apache.axiom.soap.SOAPFaultRole;
import org.apache.axiom.soap.SOAPFaultSubCode;
import org.apache.axiom.soap.SOAPFaultText;
import org.apache.axiom.soap.SOAPFaultValue;
import org.apache.axis2.jaxws.message.Block;
import org.apache.axis2.jaxws.message.MessageException;
import org.apache.axis2.jaxws.message.XMLFault;
import org.apache.axis2.jaxws.message.XMLFaultCode;
import org.apache.axis2.jaxws.message.XMLFaultReason;
import org.apache.axis2.jaxws.message.util.MessageUtils;

/**
 * Collection of utilities used by the Message implementation to 
 * process XMLFaults.
 * @see XMLFault
 */
class XMLFaultUtils {

    
    /**
     * @param envelope javax.xml.soap.SOAPEnvelope
     * @return true if the SOAPEnvelope contains a SOAPFault
     */
    static boolean isFault(javax.xml.soap.SOAPEnvelope envelope) throws SOAPException {
        javax.xml.soap.SOAPBody body = envelope.getBody();
        if (body != null) {
            return (body.getFault() != null);
        }
        return false;
    }
    
    /**
     * @param envelope org.apache.axiom.soap.SOAPEnvelope
     * @return true if the SOAPEnvelope contains a SOAPFault
     */
    static boolean isFault(SOAPEnvelope envelope) {
        SOAPBody body = envelope.getBody();
        if (body != null) {
            return (body.getFault() != null);
        }
        return false;
    }
    
    
    
	/**
	 * Create an XMLFault object from a SOAPFault and detail Blocks
	 * @param soapFault
	 * @param detailBlocks
	 * @return
	 */
	public static XMLFault createXMLFault(SOAPFault soapFault, Block[] detailBlocks) throws MessageException {
        
	    // The SOAPFault structure is modeled after SOAP 1.2.  
        // Here is a sample comprehensive SOAP 1.2 fault which will help you understand the
        // structure.
        // <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"
        //               xmlns:m="http://www.example.org/timeouts"
        //               xmlns:xml="http://www.w3.org/XML/1998/namespace">
        //   <env:Body>
        //      <env:Fault>
        //        <env:Code>
        //          <env:Value>env:Sender</env:Value>
        //          <env:Subcode>
        //            <env:Value>m:MessageTimeout</env:Value>
        //          </env:Subcode>
        //        </env:Code>
        //        <env:Reason>
        //          <env:Text xml:lang="en">Sender Timeout</env:Text>
        //          <env:Text xml:lang="de">Sender Timeout</env:Text>
        //        </env:Reason>
        //        <env:Node>http://my.example.org/Node</env:Node>
        //        <env:Role>http://my.example.org/Role</env:Role>
        //        <env:Detail>
        //          <m:MaxTime>P5M</m:MaxTime>
        //        </env:Detail>    
        //      </env:Fault>
        //   </env:Body>
        // </env:Envelope>
        
        
        // Get the code
        // TODO what if this fails ?  Log a message and treat like a RECEIVER fault ?
        SOAPFaultCode soapCode = soapFault.getCode();
        QName codeQName = soapCode.getValue().getTextAsQName();
        XMLFaultCode code = XMLFaultCode.fromQName(codeQName);
        
		// Get the primary reason text
        // TODO what if this fails
        SOAPFaultReason soapReason = soapFault.getReason();
        List soapTexts = soapReason.getAllSoapTexts();
		SOAPFaultText reasonText = (SOAPFaultText) soapTexts.get(0);
        String text = reasonText.getText();
        String lang = reasonText.getLang();
        XMLFaultReason reason = new XMLFaultReason(text, lang);

        // Construct the XMLFault from the required information (code, reason, detail blocks)
		XMLFault xmlFault = new XMLFault(code, reason, detailBlocks);
        
        
        
        // Add the secondary fault information
 
        // Get the SubCodes
        SOAPFaultSubCode soapSubCode = soapCode.getSubCode();
        if (soapSubCode != null) {
            List<QName> list = new ArrayList<QName>();
            
            // Walk the nested sub codes and collect the qnames
            while (soapSubCode != null) {
                SOAPFaultValue soapSubCodeValue = soapSubCode.getValue();
                QName qName = soapSubCodeValue.getTextAsQName();
                list.add(qName);
                soapSubCode = soapSubCode.getSubCode();
            }
            
            // Put the collected sub code qnames onto the xmlFault
            QName[] qNames = new QName[list.size()];
            xmlFault.setSubCodes(list.toArray(qNames));
        }
        
        // Get the secondary Reasons...the first reason was already saved as the primary reason
        if (soapTexts.size() > 1) {
            XMLFaultReason[] secondaryReasons = new XMLFaultReason[soapTexts.size() - 1];
            for (int i= 1; i<soapTexts.size(); i++) {
                SOAPFaultText soapReasonText = (SOAPFaultText) soapTexts.get(i);
                secondaryReasons[i-1] = new XMLFaultReason(soapReasonText.getText(), 
                                                           soapReasonText.getLang());
            }
            xmlFault.setSecondaryReasons(secondaryReasons);
        }
        
		// Get the Node
        SOAPFaultNode soapNode = soapFault.getNode();
        if (soapNode != null) {
            xmlFault.setNode(soapNode.getText());
        }
        
        // Get the Role
        SOAPFaultRole soapRole = soapFault.getRole();
        if (soapRole != null) {
            xmlFault.setRole(soapRole.getText());
        }
        return xmlFault;         
	}
    
    /**
     * Create a SOAPFault representing the XMLFault and attach it to body.
     * If there are 1 or more detail Blocks on the XMLFault, a SOAPFaultDetail is attached.
     * If ignoreDetailBlocks=false, then OMElements are added to the SOAPFaultDetail.  
     * If ignoreDetailBlocks=true, then the Detail Blocks are ignored (this is necessary
     * for XMLSpine processing)
     * @param xmlFault
     * @param body - Assumes that the body is empty
     * @param ignoreDetailBlocks true or fals
     * @return SOAPFault (which is attached to body)
     */
    public static SOAPFault createSOAPFault(XMLFault xmlFault, 
                SOAPBody body, 
                boolean ignoreDetailBlocks) throws MessageException {
        
        // Get the factory and create the soapFault
        SOAPFactory factory = MessageUtils.getSOAPFactory(body);
        SOAPFault soapFault = factory.createSOAPFault(body);
        OMNamespace ns = body.getNamespace();
        
        // The SOAPFault structure is modeled after SOAP 1.2.  
        // Here is a sample comprehensive SOAP 1.2 fault which will help you understand the
        // structure.
        // <env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"
        //               xmlns:m="http://www.example.org/timeouts"
        //               xmlns:xml="http://www.w3.org/XML/1998/namespace">
        //   <env:Body>
        //      <env:Fault>
        //        <env:Code>
        //          <env:Value>env:Sender</env:Value>
        //          <env:Subcode>
        //            <env:Value>m:MessageTimeout</env:Value>
        //          </env:Subcode>
        //        </env:Code>
        //        <env:Reason>
        //          <env:Text xml:lang="en">Sender Timeout</env:Text>
        //          <env:Text xml:lang="de">Sender Timeout</env:Text>
        //        </env:Reason>
        //        <env:Node>http://my.example.org/Node</env:Node>
        //        <env:Role>http://my.example.org/Role</env:Role>
        //        <env:Detail>
        //          <m:MaxTime>P5M</m:MaxTime>
        //        </env:Detail>    
        //      </env:Fault>
        //   </env:Body>
        // </env:Envelope>
        
        // Set the primary Code Value
        SOAPFaultCode soapCode = factory.createSOAPFaultCode(soapFault);
        SOAPFaultValue soapValue = factory.createSOAPFaultValue(soapCode);
        QName soapValueQName = xmlFault.getCode().toQName(ns.getNamespaceURI());
        soapValue.setText(soapValueQName);
        
        
        // Set the primary Reason Text
        SOAPFaultReason soapReason = factory.createSOAPFaultReason(soapFault);
        SOAPFaultText soapText = factory.createSOAPFaultText(soapReason);
        soapText.setText(xmlFault.getReason().getText());
        soapText.setLang(xmlFault.getReason().getLang());
        
        // Set the Detail and contents of Detail
        Block[] blocks = xmlFault.getDetailBlocks();
        if (blocks != null && blocks.length > 0) {
            SOAPFaultDetail detail = factory.createSOAPFaultDetail(soapFault);
            if (!ignoreDetailBlocks) {
                for (int i=0; i<blocks.length; i++) {
                    // A Block implements OMDataSource.  So create OMSourcedElements
                    // for each of the Blocks.
                    OMSourcedElementImpl element = 
                        new OMSourcedElementImpl(blocks[i].getQName(), factory, blocks[i]);
                    detail.addChild(element);
                }
            }
        }
        
        // Now set all of the secondary fault information
        // Set the SubCodes
        QName[] subCodes = xmlFault.getSubCodes();
        if (subCodes != null && subCodes.length > 0) {
           OMElement curr = soapCode;
           for (int i=0; i<subCodes.length; i++) {
               SOAPFaultSubCode subCode = (i==0) ?
                       factory.createSOAPFaultSubCode((SOAPFaultCode)    curr) :
                       factory.createSOAPFaultSubCode((SOAPFaultSubCode) curr);
               SOAPFaultValue soapSubCodeValue = factory.createSOAPFaultValue(subCode);
               soapSubCodeValue.setText(subCodes[i]);
               curr = subCode;
           }
        }
        
        // Set the secondary reasons and languages
        XMLFaultReason reasons[] = xmlFault.getSecondaryReasons();
        if (reasons != null && reasons.length > 0) {
            for (int i=0; i<reasons.length; i++) {
                SOAPFaultText soapReasonText = factory.createSOAPFaultText(soapReason);
                soapReasonText.setText(reasons[i].getText());
                soapReasonText.setLang(reasons[i].getLang());
            }
        }
        
        // Set the Role
        if (xmlFault.getRole() != null) {
            SOAPFaultRole soapRole = factory.createSOAPFaultRole();
            soapRole.setText(xmlFault.getRole());
        }
        
        // Set the Node
        if (xmlFault.getNode() != null) {
            SOAPFaultNode soapNode = factory.createSOAPFaultNode();
            soapNode.setText(xmlFault.getNode());
        }
           
        return soapFault;
            
    }
}