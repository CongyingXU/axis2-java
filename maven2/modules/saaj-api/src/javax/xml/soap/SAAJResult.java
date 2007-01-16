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

package javax.xml.soap;

import javax.xml.transform.dom.DOMResult;

import org.w3c.dom.*;

public class SAAJResult extends DOMResult {

    public SAAJResult()
            throws SOAPException {
        this(MessageFactory.newInstance().createMessage());
    }

    public SAAJResult(String s)
            throws SOAPException {
        this(MessageFactory.newInstance(s).createMessage());
    }

    public SAAJResult(SOAPMessage soapmessage) {
        super(soapmessage.getSOAPPart());
    }

    public SAAJResult(SOAPElement soapelement) {
        super(soapelement);
    }

    public javax.xml.soap.Node getResult() {
        org.w3c.dom.Node node = super.getNode();
        if(node instanceof SOAPPart){
            try {
                return ((SOAPPart)node).getEnvelope();
            } catch (SOAPException e) {
                throw new RuntimeException(e);
            }
        }
        return (javax.xml.soap.Node) node.getFirstChild();
    }
}
