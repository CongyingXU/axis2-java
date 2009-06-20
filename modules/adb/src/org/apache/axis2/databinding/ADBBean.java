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

package org.apache.axis2.databinding;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.databinding.utils.writer.MTOMAwareXMLStreamWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.Serializable;

/*
 * ADBBean - Axis Data Binding Bean. This will be implemented by all the beans that are being generated by
 * Axis2 data binding framework
 */

public interface ADBBean extends Serializable {
    /**
     * Serializes an ADBBean. Gets the pull parser and fetches the XML pull events to represent the
     * bean.
     *
     * @param adbBeanQName the name of the element to be generated for this ADBBean.
     * @return Returns a pull parser for this ADBBean.
     */
    public XMLStreamReader getPullParser(QName adbBeanQName) throws XMLStreamException;

    public OMElement getOMElement(QName parentQName, OMFactory factory) throws ADBException;

    public void serialize(final QName parentQName,
                          final OMFactory factory,
                          MTOMAwareXMLStreamWriter xmlWriter)
            throws XMLStreamException, ADBException;

    public void serialize(final QName parentQName,
                          final OMFactory factory,
                          MTOMAwareXMLStreamWriter xmlWriter,
                          boolean serializeType)
            throws XMLStreamException, ADBException;

    /**
     * There will be a self factory in every generated data bound class XXX:
     * public static XXX read (XMLStreamReader);
     */
}
