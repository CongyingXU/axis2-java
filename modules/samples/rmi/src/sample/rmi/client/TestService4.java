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
package sample.rmi.client;

import sample.rmi.server.Service4;
import sample.rmi.server.Service3Interface;
import sample.rmi.server.Service4Interface;
import sample.rmi.server.dto.ChildClass;
import sample.rmi.server.dto.ParentClass;
import org.apache.axis2.rmi.Configurator;
import org.apache.axis2.rmi.client.RMIClient;
import org.apache.axis2.rmi.client.RMIClientProxy;

import java.util.ArrayList;
import java.util.List;


public class TestService4 {

    private Configurator configurator;

    public TestService4() {

        this.configurator = new Configurator();
        this.configurator.addPackageToNamespaceMaping("sample.rmi.server", "http://sample/service");
        this.configurator.addPackageToNamespaceMaping("sample.rmi.server.dto", "http://sample/service/types");
        this.configurator.addExtension(ParentClass.class);
        this.configurator.addExtension(ChildClass.class);
    }

    public void testMethod11() {

        try {
            Service4Interface proxy =
                    (Service4Interface) RMIClientProxy.createProxy(Service4Interface.class,
                            this.configurator,
                            "http://localhost:8080/axis2/services/Service4");
            Object result = proxy.method1(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void testMethod12() {

        try {
            Service4Interface proxy =
                    (Service4Interface) RMIClientProxy.createProxy(Service4Interface.class,
                            this.configurator,
                            "http://localhost:8080/axis2/services/Service4");
            Object result = proxy.method1("test string");
            System.out.println("Got the string " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testMethod13() {

        try {
            Service4Interface proxy =
                    (Service4Interface) RMIClientProxy.createProxy(Service4Interface.class,
                            this.configurator,
                            "http://localhost:8080/axis2/services/Service4");

            ParentClass parentClass = new ParentClass();
            parentClass.setParam1("test string");
            parentClass.setParam2(3);
            ParentClass result = (ParentClass) proxy.method1(parentClass);
            System.out.println("Param 1 ==>" + result.getParam1());
            System.out.println("Param 2 ==>" + result.getParam2());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testMethod3() {
        try {
            Service4Interface proxy =
                    (Service4Interface) RMIClientProxy.createProxy(Service4Interface.class,
                            this.configurator,
                            "http://localhost:8080/axis2/services/Service4");
            String[] result = proxy.method3("Param1","Param2","Param3");
            System.out.println("Object 1 ==>" + result[0]);
            System.out.println("Object 2 ==>" + result[1]);
            System.out.println("Object 3 ==>" + result[2]);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testMethod2() {
        try {
             Service4Interface proxy =
                    (Service4Interface) RMIClientProxy.createProxy(Service4Interface.class,
                            this.configurator,
                            "http://localhost:8080/axis2/services/Service4");

            List param1 = new ArrayList();
            param1.add(new ChildClass());
            param1.add(new Integer(2));

            List param2 = new ArrayList();
            param2.add(new ParentClass());
            param2.add(new Float(2.34f));

            List result = proxy.method2(param1,param2);
            System.out.println("Object 1 ==>" + result.get(1));
            System.out.println("Object 3 ==>" + result.get(3));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        TestService4 testService4 = new TestService4();
        testService4.testMethod11();
        testService4.testMethod12();
        testService4.testMethod13();
        testService4.testMethod2();
        testService4.testMethod3();
    }
}
