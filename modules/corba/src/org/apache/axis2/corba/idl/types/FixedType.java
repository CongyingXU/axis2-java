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

package org.apache.axis2.corba.idl.types;

import org.omg.CORBA.ORB;
import org.omg.CORBA.TypeCode;

public class FixedType extends DataType {

    private short digits;
    private short scale;

    public FixedType(short digits, short scale) {
        this.digits = digits;
        this.scale = scale;
    }

    protected TypeCode generateTypeCode() {
        return ORB.init().create_fixed_tc(digits, scale);
    }

    public short getDigits() {
        return digits;
    }

    public void setDigits(short digits) {
        this.digits = digits;
    }

    public short getScale() {
        return scale;
    }

    public void setScale(short scale) {
        this.scale = scale;
    }
}
