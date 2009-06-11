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

package org.apache.axis2.jaxws.description.impl;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants.Configuration;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.OutInAxisOperation;
import org.apache.axis2.description.OutOnlyAxisOperation;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.WSDL11ToAllAxisServicesBuilder;
import org.apache.axis2.description.WSDL11ToAxisServiceBuilder;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.java.security.AccessController;
import org.apache.axis2.jaxws.ExceptionFactory;
import org.apache.axis2.jaxws.description.EndpointDescription;
import org.apache.axis2.jaxws.description.EndpointDescriptionJava;
import org.apache.axis2.jaxws.description.EndpointDescriptionWSDL;
import org.apache.axis2.jaxws.description.EndpointInterfaceDescription;
import org.apache.axis2.jaxws.description.ServiceDescription;
import org.apache.axis2.jaxws.description.ServiceDescriptionWSDL;
import org.apache.axis2.jaxws.description.builder.CustomAnnotationInstance;
import org.apache.axis2.jaxws.description.builder.CustomAnnotationProcessor;
import org.apache.axis2.jaxws.description.builder.DescriptionBuilderComposite;
import org.apache.axis2.jaxws.description.builder.MDQConstants;
import org.apache.axis2.jaxws.description.builder.WsdlComposite;
import org.apache.axis2.jaxws.description.xml.handler.HandlerChainsType;
import org.apache.axis2.jaxws.feature.ServerConfigurator;
import org.apache.axis2.jaxws.feature.ServerFramework;
import org.apache.axis2.jaxws.i18n.Messages;
import org.apache.axis2.jaxws.registry.ServerConfiguratorRegistry;
import org.apache.axis2.jaxws.util.WSDL4JWrapper;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.axis2.wsdl.util.WSDLDefinitionWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.wsdl.Binding;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.http.HTTPBinding;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.extensions.soap12.SOAP12Binding;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingType;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.handler.PortInfo;
import javax.xml.ws.soap.MTOM;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPBinding;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** @see ../EndpointDescription */
/*
 * TODO: EndpointDescription should be created via AxisService objects and not directly from WSDL
 * IMPORTANT NOTE: Axis2 currently only supports 1 service and 1 port under that service.  When that is
 * fixed, that will probably have an impact on this class.  In particular, I think this should be created 
 * somehow from an AxisService/AxisPort combination, and not directly from the WSDL.
 */
class EndpointDescriptionImpl
        implements EndpointDescription, EndpointDescriptionJava, EndpointDescriptionWSDL {
    private ServiceDescriptionImpl parentServiceDescription;
    private AxisService axisService;

    private QName portQName;
    private QName serviceQName;

    // Corresponds to a port that was added dynamically via addPort and is not declared (either in WSDL or annotations)
    private boolean isDynamicPort;

    // If the WSDL is fully specified, we could build the AxisService from the WSDL
    private boolean isAxisServiceBuiltFromWSDL;

    private String serviceImplName;    //class name of the service impl or SEI

    // Note that an EndpointInterfaceDescription will ONLY be set for an Endpoint-based implementation;
    // it will NOT be set for a Provider-based implementation
    private EndpointInterfaceDescription endpointInterfaceDescription;

    //On Client side, there should be One ServiceClient instance per AxisSerivce
    private ServiceClient serviceClient = null;

    //This is the base WebService or WebServiceProvider that we are processing
    DescriptionBuilderComposite composite = null;

    // Set of packages that are needed to marshal/unmashal data (used to set JAXBContext)
    TreeSet<String> packages = null;

    // The JAX-WS Handler port information corresponding to this endpoint
    private PortInfo portInfo;

    private String clientBindingID;
    // The effective endpoint address.  It could be set by the client or come from the WSDL SOAP address
    private String endpointAddress;
    // The endpoint address from the WSDL soap:address extensibility element if present.
    private String wsdlSOAPAddress;

    private static final Log log = LogFactory.getLog(EndpointDescriptionImpl.class);

    // ===========================================
    // ANNOTATION related information
    // ===========================================

    // ANNOTATION: @WebService and @WebServiceProvider
    // Only one of these two annotations will be set; they are mutually exclusive
    private WebService webServiceAnnotation;
    private WebServiceProvider webServiceProviderAnnotation;

    //ANNOTATION: @HandlerChain
    private HandlerChain handlerChainAnnotation;
    private HandlerChainsType handlerChainsType;

    // Information common to both WebService and WebServiceProvider annotations
    private String annotation_WsdlLocation;
    private String annotation_ServiceName;
    private String annotation_PortName;
    private String annotation_TargetNamespace;

    // Information only set on WebService annotation
    // ANNOTATION: @WebService
    private String webService_EndpointInterface;
    private String webService_Name;

    // ANNOTATION: @ServiceMode
    // Note this is only valid on a Provider-based endpoint
    private ServiceMode serviceModeAnnotation;
    private Service.Mode serviceModeValue;
    // Default ServiceMode.value per JAXWS Spec 7.1 "javax.xml.ServiceMode" pg 79
    public static final javax.xml.ws.Service.Mode ServiceMode_DEFAULT =
            javax.xml.ws.Service.Mode.PAYLOAD;

    // ANNOTATION: @BindingType
    private BindingType bindingTypeAnnotation;
    private String bindingTypeValue;
    // Default BindingType.value per JAXWS Spec Sec 7.8 "javax.xml.ws.BindingType" pg 83 
    // and Sec 1.4 "SOAP Transport and Transfer Bindings" pg 119
    public static final String BindingType_DEFAULT =
            javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING;
    
    // ANNOTATION: @RespectBinding
    private Boolean respectBinding = false;
    
    private List<CustomAnnotationInstance> customAnnotations;
    
    private Map<String, CustomAnnotationProcessor> customAnnotationProcessors;

    // Supports WebServiceFeatureAnnotations
    private ServerFramework framework = new ServerFramework();
    
    private Map<String, Object> properties;
    
    // Remembers if this endpoint description is MTOMEnabled
    private Boolean isMTOMEnabledCache = null;

    
    /**
     * Create a service-requester side EndpointDescription based on the WSDL port.  
     * Note that per the JAX-WS Spec (Final
     * Release, 4/19/2006 Section 4.2.3 Proxies, page 55)the "namespace component of the port is the
     * target namespace of the WSDL definition document". Note this is currently only used on the
     * client-side (this may change).
     *
     * @param theClass The SEI or Impl class.  This will be NULL for Dispatch clients since they
     *                 don't use an SEI
     */
    EndpointDescriptionImpl(Class theClass, QName portName, ServiceDescriptionImpl parent) {
        this(theClass, portName, parent, null, null);
    }
    EndpointDescriptionImpl(Class theClass, QName portName, ServiceDescriptionImpl parent, 
                            DescriptionBuilderComposite dbc, Object compositeKey ) {
        this(theClass, portName, false, parent, dbc, compositeKey);
    }
    EndpointDescriptionImpl(Class theClass, QName portName, boolean dynamicPort,
                            ServiceDescriptionImpl parent) {
        this(theClass, portName, dynamicPort, parent, null, null);
    }

    EndpointDescriptionImpl(Class theClass, QName portName, boolean dynamicPort,
                            ServiceDescriptionImpl parent, 
                            DescriptionBuilderComposite sparseComposite,
                            Object sparseCompositeKey) {

        // TODO: This and the other constructor will (eventually) take the same args, so the logic needs to be combined
        // TODO: If there is WSDL, could compare the namespace of the defn against the portQName.namespace
        this.parentServiceDescription = parent;
        composite = new DescriptionBuilderComposite();
        composite.setSparseComposite(sparseCompositeKey, sparseComposite);
        composite.setCorrespondingClass(theClass);
        ClassLoader loader = (ClassLoader) AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        return this.getClass().getClassLoader();
                    }
                }
        );
        composite.setClassLoader(loader);
        composite.setIsServiceProvider(false);

        webServiceAnnotation = composite.getWebServiceAnnot();
        
        this.isDynamicPort = dynamicPort;
        if (DescriptionUtils.isEmpty(portName)) {
            // If the port name is null, then per JAX-WS 2.0 spec p. 55, the runtime is responsible for selecting the port.
            this.portQName = selectPortToUse();
        } else {
            this.portQName = portName;
        }
        // At this point, there must be a port QName set, either as passed in, or determined from the WSDL and/or annotations.
        // If not, that is an error.
        if (this.portQName == null) {
            String msg = Messages.getMessage("endpointDescriptionErr1",theClass.getName(),parent.getClass().getName());
            throw ExceptionFactory.makeWebServiceException(msg);
        }

        // TODO: Refactor this with the consideration of no WSDL/Generic Service/Annotated SEI
        setupAxisService();
        addToAxisService();

        buildDescriptionHierachy();
        addAnonymousAxisOperations();

        // This will set the serviceClient field after adding the AxisService to the AxisConfig
        getServiceClient();
        // Give the configuration builder a chance to finalize configuration for this service
        try {
            getServiceDescriptionImpl().getClientConfigurationFactory()
                    .completeAxis2Configuration(axisService);
        } catch (Exception e) {
            String msg = Messages.getMessage("endpointDescriptionErr2",e.getClass().getName(),parent.getClass().getName());
            throw ExceptionFactory.makeWebServiceException(msg, e);
        }
    }
    
    EndpointDescriptionImpl(ServiceDescriptionImpl parent, String serviceImplName) {
        this(parent, serviceImplName, null);
    }

    /**
     * Create a service-provider side EndpointDescription based on the DescriptionBuilderComposite. 
     * Note that per the
     * JAX-WS Spec (Final Release, 4/19/2006 Section 4.2.3 Proxies, page 55)the "namespace component
     * of the port is the target namespace of the WSDL definition document".
     *
     * @param theClass The SEI or Impl class.  This will be NULL for Dispatch clients since they
     *                 don't use an SEI
     */
    EndpointDescriptionImpl(ServiceDescriptionImpl parent, String serviceImplName, Map<String, Object>
        properties) {
        
        // initialize CustomAnnotationIntance list and CustomAnnotationProcessor map
        customAnnotations = new ArrayList<CustomAnnotationInstance>();
        customAnnotationProcessors = new HashMap<String, CustomAnnotationProcessor>();
        
        // set properties map
        this.properties = properties;
        

        // TODO: This and the other constructor will (eventually) take the same args, so the logic needs to be combined
        // TODO: If there is WSDL, could compare the namespace of the defn against the portQName.namespace
        this.parentServiceDescription = parent;
        this.serviceImplName = serviceImplName;

        composite = getServiceDescriptionImpl().getDescriptionBuilderComposite();
        if (composite == null) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("endpointDescriptionErr3"));
        }
        
        if(composite.getHandlerChainAnnot() != null && composite.getHandlerChainsType() != null) {
        	throw ExceptionFactory.makeWebServiceException(
            Messages.getMessage("handlerSourceFail", composite.getClassName()));
        }
        
        handlerChainsType = composite.getHandlerChainsType();

        //Set the base level of annotation that we are processing...currently
        // a 'WebService' or a 'WebServiceProvider'
        if (composite.getWebServiceAnnot() != null)
            webServiceAnnotation = composite.getWebServiceAnnot();
        else
            webServiceProviderAnnotation = composite.getWebServiceProviderAnnot();

        
        // now get the custom annotation and process information from the DBC
        customAnnotations.addAll(composite.getCustomAnnotationInstances());
        customAnnotationProcessors.putAll(composite.getCustomAnnotationProcessors());
        
        // REVIEW: Maybe this should be an error if the name has already been set and it doesn't match
        // Note that on the client side, the service QN should be set; on the server side it will not be.
        if (DescriptionUtils.isEmpty(getServiceDescription().getServiceQName())) {
            getServiceDescriptionImpl().setServiceQName(getServiceQName());
        }
        //Call the getter to insure the qualified port name is set. 
        getPortQName();

        // TODO: Refactor this with the consideration of no WSDL/Generic Service/Annotated SEI
        setupAxisServiceFromDBL();
        addToAxisService();    //Add a reference to this EndpointDescription to the AxisService

        //TODO: Need to remove operations from AxisService that have 'exclude = true
        //      then call 'validateOperations' to verify that WSDL and AxisService match up,
        //      Remember that this will only happen when we generate an AxisService from existing
        //		WSDL and then need to perform further processing because we have annotations as well
        //		If there is no WSDL, we would never process the Method to begin with.

        buildDescriptionHierachy();

        WsdlComposite wsdlComposite = null;
        
        String bindingType = getBindingType();

        boolean isSOAP11 =
                (bindingType.equals(javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING) || 
                        bindingType.equals(javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_MTOM_BINDING))
                        ? true : false;


        // Determine if we need to generate WSDL
        // First, make sure that this is only a SOAP 1.1 based binding, per JAXWS spec. we cannot 
        // generate WSDL if the binding type is not SOAP 1.1 based.
        // Then, assuming the composite does not contain a 
        // Wsdl Definition, go ahead and generate it
        // REVIEW: I think this should this be isSOAP11 so the generators are only called for 
        //         SOAP11; i.e. NOT for SOAP12 or XML/HTTP bindings.
        if (isSOAP11){
            if (
                    (isEndpointBased() &&
                            DescriptionUtils.isEmpty(getAnnoWebServiceEndpointInterface()))
                            ||
                            (!isEndpointBased())
                    ) {
                //This is either an implicit SEI, or a WebService Provider
    
                wsdlComposite = generateWSDL(composite);
    
            } else if (isEndpointBased()) {
                //This impl class specifies an SEI...this is a special case. There is a bug
                //in the tooling that allows for the wsdllocation to be specifed on either the
                //impl. class, or the SEI, or both. So, we need to look for the wsdl as follows:
                //			1. If the Wsdl exists on the SEI, then check for it on the impl.
                //			2. If it is not found in either location, in that order, then generate
    
                DescriptionBuilderComposite seic =
                        getServiceDescriptionImpl().getDBCMap()
                                .get(composite.getWebServiceAnnot().endpointInterface());
    
                //Only generate WSDL if a definition doesn't already exist
                if (seic.getWsdlDefinition() == null)
                    wsdlComposite = generateWSDL(composite);
            }

        } else if (composite.getWsdlDefinition() == null) {
            //This is a SOAP12 binding that does not contain a WSDL definition, log a WARNING
            log.warn("This implementation does not contain a WSDL definition and is not a SOAP 1.1 based binding. "
                    + "Per JAXWS spec. - a WSDL definition cannot be generated for this implementation. Name: "
                    + composite.getClassName());
        }

        if (isSOAP11){
    
            //Save the WSDL Location and the WsdlDefinition, value depends on whether wsdl was generated
            Parameter wsdlLocationParameter = new Parameter();
            wsdlLocationParameter.setName(MDQConstants.WSDL_LOCATION);
    
            Parameter wsdlDefParameter = new Parameter();
            wsdlDefParameter.setName(MDQConstants.WSDL_DEFINITION);
    
            Parameter wsdlCompositeParameter = new Parameter();
            wsdlCompositeParameter.setName(MDQConstants.WSDL_COMPOSITE);
    
            if (wsdlComposite != null) {
    
                //We have a wsdl composite, so set these values for the generated wsdl
                wsdlCompositeParameter.setValue(wsdlComposite);
                wsdlLocationParameter.setValue(wsdlComposite.getWsdlFileName());

                Definition def =
                        getServiceDescriptionImpl().getGeneratedWsdlWrapper().getDefinition();
                URL wsdlUrl = getServiceDescriptionImpl().getGeneratedWsdlWrapper().getWSDLLocation();
                if (def instanceof WSDLDefinitionWrapper) {
                    wsdlDefParameter.setValue(def);
                } else {
                    // Create WSDLDefinitionWrapper
                    WSDLDefinitionWrapper wrap = null;
                    ConfigurationContext cc = composite.getConfigurationContext();
                    if (cc != null && cc.getAxisConfiguration() != null) {
                        wrap = new WSDLDefinitionWrapper(def, wsdlUrl, 
                                                         cc.getAxisConfiguration());
                    } else {
                        // Probably shouldn't get here.  But if we do, use
                        // a memory sensitve wsdl wrapper
                        wrap = new WSDLDefinitionWrapper(def, wsdlUrl, true, 2);
                    }
                    wsdlDefParameter.setValue(wrap);
                }

            } else if (getServiceDescriptionImpl().getWSDLWrapper() != null) {
                //No wsdl composite because wsdl already exists

                wsdlLocationParameter.setValue(getAnnoWebServiceWSDLLocation());

                Definition def = getServiceDescriptionImpl().getWSDLWrapper().getDefinition();
                URL wsdlUrl = getServiceDescriptionImpl().getWSDLWrapper().getWSDLLocation();
                if (def instanceof WSDLDefinitionWrapper) {
                    wsdlDefParameter.setValue(def);
                } else {
                    // Create WSDLDefinitionWrapper
                    WSDLDefinitionWrapper wrap = null;
                    ConfigurationContext cc = composite.getConfigurationContext();
                    if (cc != null && cc.getAxisConfiguration() != null) {
                        wrap = new WSDLDefinitionWrapper(def, wsdlUrl, 
                                                         cc.getAxisConfiguration());
                    } else {
                        // Probably shouldn't get here.  But if we do, use
                        // a memory sensitve wsdl wrapper
                        wrap = new WSDLDefinitionWrapper(def, wsdlUrl, true, 2);
                    }
                    wsdlDefParameter.setValue(wrap);
                }

            } else {
                //There is no wsdl composite and there is NOT a wsdl definition
                wsdlLocationParameter.setValue(null);
                wsdlDefParameter.setValue(null);
    
            }
    
            try {
                if (wsdlComposite != null) {
                    axisService.addParameter(wsdlCompositeParameter);
                }
                axisService.addParameter(wsdlDefParameter);
                axisService.addParameter(wsdlLocationParameter);
            } catch (Exception e) {
                throw ExceptionFactory.makeWebServiceException(Messages.getMessage("endpointDescriptionErr4"));
            }
        }
        else {
            // Need to account for SOAP 1.2 WSDL when supplied with application
            Parameter wsdlDefParameter = new Parameter();
            wsdlDefParameter.setName(MDQConstants.WSDL_DEFINITION);
            Parameter wsdlLocationParameter = new Parameter();
            wsdlLocationParameter.setName(MDQConstants.WSDL_LOCATION);
            if (getServiceDescriptionImpl().getWSDLWrapper() != null) {
                wsdlLocationParameter.setValue(getAnnoWebServiceWSDLLocation());

                Definition def = getServiceDescriptionImpl().getWSDLWrapper().getDefinition();
                URL wsdlUrl = getServiceDescriptionImpl().getWSDLWrapper().getWSDLLocation();
                if (def instanceof WSDLDefinitionWrapper) {
                    wsdlDefParameter.setValue(def);
                } else {
                    // Create WSDLDefinitionWrapper
                    WSDLDefinitionWrapper wrap = null;
                    ConfigurationContext cc = composite.getConfigurationContext();
                    if (cc != null && cc.getAxisConfiguration() != null) {
                        wrap = new WSDLDefinitionWrapper(def, wsdlUrl, 
                                                         cc.getAxisConfiguration());
                    } else {
                        // Probably shouldn't get here.  But if we do, use
                        // a memory sensitve wsdl wrapper
                        wrap = new WSDLDefinitionWrapper(def, wsdlUrl, true, 2);
                    }
                    wsdlDefParameter.setValue(wrap);
                }
            }
            // No WSDL supplied and we do not generate for non-SOAP 1.1/HTTP
            // endpoints
            else {
                wsdlLocationParameter.setValue(null);
                wsdlDefParameter.setValue(null);
            }
            try {
                axisService.addParameter(wsdlDefParameter);
                axisService.addParameter(wsdlLocationParameter);

            } catch (Exception e) {
                throw ExceptionFactory
                    .makeWebServiceException(Messages.getMessage("endpointDescriptionErr4"),e);
            }
        }
        
        // Before we leave we need to drive the CustomAnnotationProcessors if 
        // there were any CustomAnnotationInstance objects registered
        Iterator<CustomAnnotationInstance> annotationIter = customAnnotations.iterator();
        while(annotationIter.hasNext()) {
            CustomAnnotationInstance annotation = annotationIter.next();
            if(log.isDebugEnabled()) {
                log.debug("Checking for CustomAnnotationProcessor for CustomAnnotationInstance " +
                                "class: " + annotation.getClass().getName());
            }
            CustomAnnotationProcessor processor = customAnnotationProcessors.get(annotation.getClass().getName());
            if(processor != null) {
                if(log.isDebugEnabled()) {
                    log.debug("Found CustomAnnotationProcessor: " + processor.getClass().getName() + 
                              " for CustomAnnotationInstance: " + annotation.getClass().getName());
                }
                processor.processTypeLevelAnnotation(this, annotation);
            }
        }
        
        // Configure any available WebServiceFeatures on the endpoint.
        configureWebServiceFeatures();
    }

    /**
     * Create a service-provider side EndpointDescription from an annotated implementation class. Note this is
     * currently used only on the server-side (this probably won't change).
     * 
     * @param theClass An implemntation or SEI class
     * @param portName May be null; if so the annotation is used
     * @param parent
     * 
     * @deprecated
     */
    EndpointDescriptionImpl(Class theClass, QName portName, AxisService axisService,
                            ServiceDescriptionImpl parent) {
        this.parentServiceDescription = parent;
        composite = new DescriptionBuilderComposite();
        composite.setIsDeprecatedServiceProviderConstruction(true);
        composite.setIsServiceProvider(true);
        this.portQName = portName;
        composite.setCorrespondingClass(theClass);
        ClassLoader loader = (ClassLoader) AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        return this.getClass().getClassLoader();
                    }
                }
        );
        composite.setClassLoader(loader);
        this.axisService = axisService;

        addToAxisService();

        buildEndpointDescriptionFromAnnotations();
        
        configureWebServiceFeatures();

        // The anonymous AxisOperations are currently NOT added here.  The reason 
        // is that (for now) this is a SERVER-SIDE code path, and the anonymous operations
        // are only needed on the client side.
    }

    private void addToAxisService() {
        // Add a reference to this EndpointDescription object to the AxisService
        if (axisService != null) {
            Parameter parameter = new Parameter();
            parameter.setName(EndpointDescription.AXIS_SERVICE_PARAMETER);
            parameter.setValue(this);
            // TODO: What to do if AxisFault
            try {
                axisService.addParameter(parameter);
            } catch (AxisFault e) {
            	throw ExceptionFactory.makeWebServiceException(Messages.getMessage("endpointDescriptionErr5"),e);
            }
        }
    }

    private void buildEndpointDescriptionFromAnnotations() {
        // TODO: The comments below are not quite correct; this method is used on BOTH the 
        //       client and server.  On the client the class is always an SEI.  On the server it 
        //		 is always a service impl which may be a provider or endpoint based;
        //		 endpoint based may reference an SEI class

        // The Service Implementation class could be either Provider-based or Endpoint-based.  The 
        // annotations that are present are similar but different.  Conformance requirements 
        // per JAX-WS
        // - A Provider based implementation MUST carry the @WebServiceProvider annotation
        //   per section 5.1 javax.xml.ws.Provider on page 63
        // - An Endpoint based implementation MUST carry the @WebService annotation per JSR-181 
        //   (reference TBD) and JAX-WS (reference TBD)
        // - An Endpoint based implementation @WebService annotation MAY reference an endpoint
        //   interface 
        // - The @WebService and @WebServiceProvider annotations can not appear in the same class per 
        //   JAX-WS section 7.7 on page 82.

        // Verify that one (and only one) of the required annotations is present.
        // TODO: Add tests to verify this error checking

        if (composite.isDeprecatedServiceProviderConstruction()) {

            webServiceAnnotation = composite.getWebServiceAnnot();
            webServiceProviderAnnotation = composite.getWebServiceProviderAnnot();

            if (webServiceAnnotation == null && webServiceProviderAnnotation == null)
                throw ExceptionFactory.makeWebServiceException(Messages.getMessage("endpointDescriptionErr6",composite.getClassName()));
            else if (webServiceAnnotation != null && webServiceProviderAnnotation != null)
                throw ExceptionFactory.makeWebServiceException(Messages.getMessage("endpointDescriptionErr7",composite.getClassName()));
        }
        // If portName was specified, set it.  Otherwise, we will get it from the appropriate
        // annotation when the getter is called.
        // TODO: If the portName is specified, should we verify it against the annotation?
        // TODO: Add tests: null portName, !null portName, portName != annotation value
        // TODO: Get portName from annotation if it is null.

        // If this is an Endpoint-based service implementation (i.e. not a 
        // Provider-based one), then create the EndpointInterfaceDescription to contain
        // the operations on the endpoint.  Provider-based endpoints don't have operations
        // associated with them, so they don't have an EndpointInterfaceDescription.
        if (webServiceAnnotation != null) {
            // If this impl class references an SEI, then use that SEI to create the EndpointInterfaceDesc.
            // TODO: Add support for service impl endpoints that don't reference an SEI; remember
            //       that this is also called with just an SEI interface from svcDesc.updateWithSEI()
            String seiClassName = getAnnoWebServiceEndpointInterface();

            if (composite.isDeprecatedServiceProviderConstruction()
                    || !composite.isServiceProvider()) {
//            if (!getServiceDescriptionImpl().isDBCMap()) {
                Class seiClass = null;
                if (DescriptionUtils.isEmpty(seiClassName)) {
                    // This is the client code path; the @WebServce will not have an endpointInterface member
                    // For now, just build the EndpointInterfaceDesc based on the class itself.
                    // TODO: The EID ctor doesn't correctly handle anything but an SEI at this
                    //       point; e.g. it doesn't publish the correct methods of just an impl.
                    seiClass = composite.getCorrespondingClass();
                } else {
                    //  This is the deprecated server-side introspection code for an impl that references an SEI
                    try {
                        // TODO: Using Class forName() is probably not the best long-term way to get the SEI class from the annotation
                        seiClass = forName(seiClassName, false,
                                                            getContextClassLoader(this.axisService != null ? this.axisService.getClassLoader() : null));
                        // Catch Throwable as ClassLoader can throw an NoClassDefFoundError that
                        // does not extend Exception, so lets catch everything that extends Throwable
                        // rather than just Exception.
                    } catch (Throwable e) {
                    	throw ExceptionFactory.makeWebServiceException(Messages.getMessage("endpointDescriptionErr8"),e);

                    }
                }
                endpointInterfaceDescription = new EndpointInterfaceDescriptionImpl(seiClass, this);
            } else {
                //TODO: Determine if we need logic here to determine implied SEI or not. This logic
                //		may be handled by EndpointInterfaceDescription

                if (DescriptionUtils.isEmpty(getAnnoWebServiceEndpointInterface())) {

                    //TODO: Build the EndpointInterfaceDesc based on the class itself
                    endpointInterfaceDescription =
                            new EndpointInterfaceDescriptionImpl(composite, true, this);

                } else {
                    //Otherwise, build the EID based on the SEI composite
                    endpointInterfaceDescription = new EndpointInterfaceDescriptionImpl(
                            getServiceDescriptionImpl().getDBCMap().get(seiClassName),
                            false,
                            this);
                    
                    // after this is constructed, we need to update the @WebService.name 
                    // attribute on the axisService instance
                    if(axisService != null) {
                        updateWebServiceNameParameter(((EndpointInterfaceDescriptionImpl) 
                                endpointInterfaceDescription).getAnnoWebServiceName(), axisService); 
                    }
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("WebServiceProvider without WSDL encountered");
            }
            String bindingType = getBindingType();
            if (javax.xml.ws.http.HTTPBinding.HTTP_BINDING.equals(bindingType)||
                    SOAPBinding.SOAP11HTTP_BINDING.equals(bindingType)||
                    SOAPBinding.SOAP12HTTP_BINDING.equals(bindingType)) {
                endpointInterfaceDescription = new EndpointInterfaceDescriptionImpl(composite, this);
            }
        }
    }

    public QName getPortQName() {
        // REVIEW: Implement WSDL/Annotation merge? May be OK as is; not sure how would know WHICH port Qname to get out of the WSDL if 
        //       we didn't use annotations.
        if (portQName == null) {
            // The name was not set by the constructors, so get it from the
            // appropriate annotation.
            String name = getAnnoWebServicePortName();
            String tns = getAnnoWebServiceTargetNamespace();

            // TODO: Check for name &/| tns null or empty string and add tests for same
            portQName = new QName(tns, name);
        }
        return portQName;
    }

    public QName getServiceQName() {
        if (serviceQName == null) {
            // If the service name has been set on the Service, use that.  Otherwise
            // get the name off the annotations
            QName serviceDescQName = getServiceDescription().getServiceQName();
            if (!DescriptionUtils.isEmpty(serviceDescQName)) {
                serviceQName = serviceDescQName;
            } else {
                String localPart = getAnnoWebServiceServiceName();
                String tns = getAnnoWebServiceTargetNamespace();
                serviceQName = new QName(tns, localPart);
            }
        }
        return serviceQName;
    }

    public ServiceDescription getServiceDescription() {
        return parentServiceDescription;
    }

    ServiceDescriptionImpl getServiceDescriptionImpl() {
        return (ServiceDescriptionImpl)parentServiceDescription;
    }

    public EndpointInterfaceDescription getEndpointInterfaceDescription() {
        return endpointInterfaceDescription;
    }

    public AxisService getAxisService() {
        return axisService;
    }

    boolean isDynamicPort() {
        return isDynamicPort;
    }

    void updateWithSEI(Class sei, DescriptionBuilderComposite sparseComposite, Object sparseCompositeKey) {
        // Updating with an SEI is only valid for declared ports; it is not valid for dynamic ports.
        if (isDynamicPort()) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("updateWithSEIErr1",portQName.toString()));
        }
        if (sei == null) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("updateWithSEIErr2",portQName.toString()));
        }
        
        composite.setSparseComposite(sparseCompositeKey, sparseComposite);

        if (endpointInterfaceDescription != null) {
            // The EndpointInterfaceDescription was created previously based on the port declaration (i.e. WSDL)
            // so update that with information from the SEI annotations
            ((EndpointInterfaceDescriptionImpl)endpointInterfaceDescription).updateWithSEI(sei);
        } else {
            // An EndpointInterfaceDescription does not exist yet.  This currently happens in the case where there is 
            // NO WSDL provided and a Dispatch client is created for prior to a getPort being done for that port.
            // There was no WSDL to create the EndpointInterfaceDescription from and there was no annotated SEI to
            // use at that time.  Now we have an annotated SEI, so create the EndpointInterfaceDescription now.
            endpointInterfaceDescription = new EndpointInterfaceDescriptionImpl(sei, this);
        }
        return;
    }

    private void setupAxisService() {
        // TODO: Need to use MetaDataQuery validator to merge WSDL (if any) and annotations (if any)
        // Build up the AxisService.  Note that if this is a dynamic port, then we don't use the
        // WSDL to build up the AxisService since the port added to the Service by the client is not
        // one that will be present in the WSDL.  A null class passed in as the SEI indicates this 
        // is a dispatch client.
        if (!isDynamicPort && getServiceDescriptionImpl().getWSDLWrapper() != null) {
            isAxisServiceBuiltFromWSDL = buildAxisServiceFromWSDL();
        } else {
            buildAxisServiceFromAnnotations();
        }

        if (axisService == null) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("setupAxisServiceErr1",createAxisServiceName()));
        }

        // Save the Service QName as a parameter.
        Parameter serviceNameParameter = new Parameter();
        serviceNameParameter.setName(WSDL11ToAllAxisServicesBuilder.WSDL_SERVICE_QNAME);
        serviceNameParameter.setValue(getServiceDescription().getServiceQName());

        // Save the Port name.  Note: Axis does not expect a QName since the namespace for the port is the ns from the WSDL definition 
        Parameter portParameter = new Parameter();
        portParameter.setName(WSDL11ToAllAxisServicesBuilder.WSDL_PORT);
        portParameter.setValue(portQName.getLocalPart());
        
        // Store the service class fully qualified name
        Parameter serviceClassNameParam = new Parameter();
        serviceClassNameParam.setName(MDQConstants.CLIENT_SERVICE_CLASS);
        String serviceClassName = this.getServiceDescriptionImpl().getServiceClassName();
        if(log.isDebugEnabled()) {
            log.debug("Setting service class name parameter to: " + serviceClassName + 
                      " on AxisService: " + axisService + "@" + axisService.hashCode());
        }
        serviceClassNameParam.setValue(serviceClassName);
        
        // Store the sei class fully qualified name, if it is available
        Parameter seiClassNameParam = new Parameter();
        seiClassNameParam.setName(MDQConstants.CLIENT_SEI_CLASS);
        String seiClassName = composite.getClassName();
        if(log.isDebugEnabled()) {
            log.debug("Setting sei class name parameter to: " + seiClassName + 
                      " on AxisService: " + axisService + "@" + axisService.hashCode());
        }
        seiClassNameParam.setValue(seiClassName);

        try {
            axisService.addParameter(serviceNameParameter);
            axisService.addParameter(portParameter);
            axisService.addParameter(serviceClassNameParam);
            axisService.addParameter(seiClassNameParam);
        }
        catch (AxisFault e) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("setupAxisServiceErr2"),e);
        }
    }

    /*
     * This setups and builds the AxisService using only the DescriptionBuilderCompositeList
     * 
     */
    private void setupAxisServiceFromDBL() {
        // TODO: Need to use MetaDataQuery validator to merge WSDL (if any) and annotations (if any)
        // Build up the AxisService.  Note that if this is a dispatch client, then we don't use the
        // WSDL to build up the AxisService since the port added to the Service by the client is not
        // one that will be present in the WSDL.  A null class passed in as the SEI indicates this 
        // is a dispatch client.

        // If WSDL is present, it may be full or only partial.  If we can create the AxisService from 
        // the WSDL, that WSDL is fully specified.  Otherwise, it is "partial WSDL".  In that case
        // we use annotaions to build the AxisService
        isAxisServiceBuiltFromWSDL = false;
        if (getServiceDescriptionImpl().getWSDLWrapper() != null) {
            isAxisServiceBuiltFromWSDL = buildAxisServiceFromWSDL();

        }

        if (!isAxisServiceBuiltFromWSDL) {
            //generateWSDL(composite);
            buildAxisServiceFromAnnotations();
        }

        if (axisService == null) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("setupAxisServiceErr1",createAxisServiceName()));
        }
        
        //Save the Port Type name
        Parameter portTypeNameParameter = new Parameter();
        portTypeNameParameter.setName(MDQConstants.WSDL_PORTTYPE_NAME);
        portTypeNameParameter.setValue(getName());

        // Save the Service QName as a parameter.
        Parameter serviceNameParameter = new Parameter();
        serviceNameParameter.setName(MDQConstants.WSDL_SERVICE_QNAME);
        serviceNameParameter.setValue(getServiceDescription().getServiceQName());

        // Save the Port name.  Note: Axis does not expect a QName since the namespace
        //   for the port is the ns from the WSDL definition 
        Parameter portParameter = new Parameter();
        portParameter.setName(MDQConstants.WSDL_PORT);
        portParameter.setValue(getPortQName().getLocalPart());

        //Save the fully qualified class name for the serviceImpl
        Parameter serviceClassNameParameter = new Parameter();
        serviceClassNameParameter.setName(MDQConstants.SERVICE_CLASS);
        serviceClassNameParameter
                .setValue(DescriptionUtils.javifyClassName(composite.getClassName()));

        try {
            axisService.addParameter(portTypeNameParameter);
            axisService.addParameter(serviceNameParameter);
            axisService.addParameter(portParameter);
            axisService.addParameter(serviceClassNameParameter);
        }
        catch (AxisFault e) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("setupAxisServiceErr2"),e);
        }
    }

    private boolean buildAxisServiceFromWSDL() {
        boolean isBuiltFromWSDL = false;
        try {

            // TODO: Change this to use WSDLToAxisServiceBuilder superclass
            // Note that the axis service builder takes only the localpart of the port qname.
            // TODO:: This should check that the namespace of the definition matches the namespace of the portQName per JAXRPC spec

            
            // Use getDefinition() so that we have the advantages of the memory features.
            Definition def = getServiceDescriptionImpl().getWSDLWrapper().getDefinition();

            WSDL11ToAxisServiceBuilder serviceBuilder =
                    new WSDL11ToAxisServiceBuilder(def,
                            getServiceDescription().getServiceQName(),
                            getPortQName().getLocalPart());

            if (log.isDebugEnabled()) {
                log.debug("Building AxisService from wsdl: " + getServiceDescriptionImpl().getWSDLWrapper().getWSDLLocation());    
            }
            
            if (composite.isServiceProvider()) {
//            if (getServiceDescriptionImpl().isDBCMap()) {
                //this.class.getClass().getClassLoader();
                URIResolverImpl uriResolver =
                        new URIResolverImpl(composite.getClassLoader());
                serviceBuilder.setCustomResolver(uriResolver);
            } else {
                ClassLoader classLoader = (ClassLoader)AccessController.doPrivileged(new
                        PrivilegedAction() {
                            public Object run() {
                                return Thread.currentThread().getContextClassLoader();
                            }
                        });
                URIResolverImpl uriResolver = new URIResolverImpl(classLoader);
                serviceBuilder.setCustomResolver(uriResolver);
            }

            // TODO: Currently this only builds the client-side AxisService;
            // it needs to do client and server somehow.
            // Patterned after AxisService.createClientSideAxisService
            if (getServiceDescriptionImpl().isServerSide())
                serviceBuilder.setServerSide(true);
            else
                serviceBuilder.setServerSide(false);

            // Associate the AxisConfiguration with the ServiceBuilder if it
            // is available.  This is done so that the serviceBuilder can
            // use the appropriate WSDL wrapper memory parameters.
            AxisConfiguration ac = null;
            if (composite.getConfigurationContext() != null) {
                ac = composite.getConfigurationContext().getAxisConfiguration();
                if (ac != null) {
                    serviceBuilder.useAxisConfiguration(ac);
                }
            }
            // Create and populate the AxisService
            axisService = serviceBuilder.populateService();
            
            // If an AxisConfiguration was not available,
            // default to using a memory efficient wrapper
            if (ac == null) {
                Parameter wsdlWrapperParam = 
                    axisService.getParameter(WSDLConstants.WSDL_4_J_DEFINITION);
                if (wsdlWrapperParam != null &&
                    wsdlWrapperParam.getValue() instanceof WSDLDefinitionWrapper) {
                    
                    WSDLDefinitionWrapper wrapper = 
                        (WSDLDefinitionWrapper)  wsdlWrapperParam.getValue();
                    
                    // If only the basic wrapper is being used, upgrade to the
                    // RELOAD wrapper
                    if (wrapper.getMemoryLimitType() == 0) {
                        Definition wsdlDef = wrapper.getUnwrappedDefinition();

                        WSDLDefinitionWrapper wrapper2 = 
                            new WSDLDefinitionWrapper(wsdlDef, true, 2);

                        wsdlWrapperParam.setValue(wrapper2);
                    }
                }
            }
            axisService.setName(createAxisServiceName());
            isBuiltFromWSDL = true;

        } catch (AxisFault e) {
            // REVIEW: If we couldn't use the WSDL, should we fail instead of continuing to process using annotations?
            //         Note that if we choose to fail, we need to distinguish the partial WSDL case (which can not fail)
            String wsdlLocation = (getServiceDescriptionImpl().getWSDLLocation() != null) ?
                    getServiceDescriptionImpl().getWSDLLocation().toString() : null;
            String implClassName = composite.getClassName();
            log.warn(Messages.getMessage("bldAxisSrvcFromWSDLErr",implClassName,wsdlLocation),e);

            isBuiltFromWSDL = false;
            return isBuiltFromWSDL;
        }
        return isBuiltFromWSDL;
    }

    private void buildAxisServiceFromAnnotations() {
        // TODO: Refactor this to create from annotations.
        String serviceName = null;
        if (portQName != null) {
            serviceName = createAxisServiceName();
        } else {
            // REVIEW: Can the portQName ever be null?
            // Make this service name unique.  The Axis2 engine assumes that a service it can not find is a client-side service.
            serviceName = ServiceClient.ANON_SERVICE + this.hashCode() + System.currentTimeMillis();
        }
        axisService = new AxisService(serviceName);

        //TODO: Set other things on AxisService here, this function may have to be
        //      moved to after we create all the AxisOperations
    }

    private void buildDescriptionHierachy() {
        // Build up the Description Hierachy.  Note that if this is a dynamic port, then we don't use the
        // WSDL to build up the hierachy since the port added to the Service by the client is not
        // one that will be present in the WSDL.

        if (composite.isServiceProvider()) {
            if (!isDynamicPort && isWSDLFullySpecified())
                buildEndpointDescriptionFromWSDL();
            else
                buildEndpointDescriptionFromAnnotations();
        } else {
            //Still processing annotations from the class
            // This path was not updated
            if (!isDynamicPort && isWSDLFullySpecified()) {
                buildEndpointDescriptionFromWSDL();
            } else if (composite.getCorrespondingClass() != null) {
                // Create the rest of the description hierachy from annotations on the class.
                // If there is no SEI class, then this is a Distpach case, and we currently
                // don't create the rest of the description hierachy (since it is not an SEI and thus
                // not operation-based client.
                buildEndpointDescriptionFromAnnotations();
            }
        }
    }

    private void buildEndpointDescriptionFromWSDL() {
        Definition wsdlDefinition = getServiceDescriptionImpl().getWSDLWrapper().getDefinition();
        javax.wsdl.Service wsdlService =
                wsdlDefinition.getService(getServiceDescription().getServiceQName());
        if (wsdlService == null) {
            throw ExceptionFactory.makeWebServiceException(
                    Messages.getMessage("serviceDescErr2", createAxisServiceName()));
        }

        Map wsdlPorts = wsdlService.getPorts();
        boolean wsdlPortFound = false;
        if (wsdlPorts != null && wsdlPorts.size() > 0) {
            Iterator wsdlPortIterator = wsdlPorts.values().iterator();

            while (wsdlPortIterator.hasNext() && !wsdlPortFound) {
                Port wsdlPort = (Port)wsdlPortIterator.next();
                // Note the namespace is not included on the WSDL Port.
                if (wsdlPort.getName().equals(portQName.getLocalPart())) {

                    // Build the EndpointInterface based on the specified SEI if there is one
                    // or on the service impl class (i.e. an implicit SEI).
                    if (composite.isServiceProvider()) {
                        String seiClassName = getAnnoWebServiceEndpointInterface();
                        if (DescriptionUtils.isEmpty(seiClassName)) {
                            // No SEI specified, so use the service impl as an implicit SEI
                            endpointInterfaceDescription =
                                    new EndpointInterfaceDescriptionImpl(composite, true, this);
                        } else {
                            // Otherwise, build the EID based on the SEI composite
                            endpointInterfaceDescription = new EndpointInterfaceDescriptionImpl(
                                    getServiceDescriptionImpl().getDBCMap().get(seiClassName),
                                    false,
                                    this);
                            
                            // after this is constructed, we need to update the @WebService.name 
                            // attribute on the axisService instance
                            if(axisService != null) {
                                updateWebServiceNameParameter(((EndpointInterfaceDescriptionImpl) 
                                        endpointInterfaceDescription).getAnnoWebServiceName(), axisService); 
                            }
                        }

                    } else {
                        // Create the Endpoint Interface Description based on the WSDL.
                        endpointInterfaceDescription = new EndpointInterfaceDescriptionImpl(this);

                        // Update the EndpointInterfaceDescription created with WSDL with information from the
                        // annotations in the SEI
                        ((EndpointInterfaceDescriptionImpl)endpointInterfaceDescription)
                                .updateWithSEI(composite.getCorrespondingClass());
                    }
                    wsdlPortFound = true;
                }
            }
        }

        if (!wsdlPortFound) {
            throw ExceptionFactory.makeWebServiceException(Messages.getMessage("serviceDescErr3",portQName.getLocalPart()));
        }
    }

    /**
     * Adds the anonymous axis operations to the AxisService.  Note that this is only needed on the
     * client side, and they are currently used in two cases (1) For Dispatch clients (which don't
     * use SEIs and thus don't use operations) (2) TEMPORARLIY for Services created without WSDL
     * (and thus which have no AxisOperations created) See the AxisInvocationController invoke
     * methods for more details.
     * <p/>
     * Based on ServiceClient.createAnonymouService
     */
    private void addAnonymousAxisOperations() {
        if (axisService != null) {
            OutOnlyAxisOperation outOnlyOperation =
                    new OutOnlyAxisOperation(ServiceClient.ANON_OUT_ONLY_OP);
            axisService.addOperation(outOnlyOperation);
            outOnlyOperation.setSoapAction(null);

            OutInAxisOperation outInOperation =
                    new OutInAxisOperation(ServiceClient.ANON_OUT_IN_OP);
            axisService.addOperation(outInOperation);
            outInOperation.setSoapAction(null);
        }
    }

    public ServiceClient getServiceClient() {
        try {
            if (serviceClient == null) {
                ConfigurationContext configCtx = getServiceDescription().getAxisConfigContext();
                AxisService axisSvc = getAxisService();
                AxisConfiguration axisCfg = configCtx.getAxisConfiguration();
                if (axisCfg.getService(axisSvc.getName()) != null) {
                    axisSvc.setName(axisSvc.getName() + this.hashCode());
                }
                serviceClient = new ServiceClient(configCtx, axisSvc);
            }
        } catch (AxisFault e) {
            throw ExceptionFactory.makeWebServiceException(
                    Messages.getMessage("serviceClientCreateError"), e);
        }
        return serviceClient;
    }

    //This should eventually be deprecated in favor 'createAxisServiceNameFromDBL
    private String createAxisServiceName() {
        String portName = null;
        if (portQName != null) {
            portName = portQName.getLocalPart();
        } else {
            portName = "NoPortNameSpecified";

        }
        return getServiceDescription().getServiceQName().getLocalPart() + "." + portName;
    }

    public boolean isWSDLFullySpecified() {
        return isAxisServiceBuiltFromWSDL;
    }

    public boolean isProviderBased() {
        return webServiceProviderAnnotation != null;
    }

    public boolean isEndpointBased() {
        return webServiceAnnotation != null;
    }

    // ===========================================
    // ANNOTATION: WebService and WebServiceProvider
    // ===========================================

    public String getAnnoWebServiceWSDLLocation() {
        if (annotation_WsdlLocation == null) {

            if (getAnnoWebService() != null) {
                annotation_WsdlLocation = getAnnoWebService().wsdlLocation();

                //If this is not an implicit SEI, then make sure that its not on the SEI
                if (composite.isServiceProvider() && !composite.isDeprecatedServiceProviderConstruction()) {
                    if (!DescriptionUtils.isEmpty(getAnnoWebServiceEndpointInterface())) {

                        DescriptionBuilderComposite seic =
                                getServiceDescriptionImpl().getDBCMap()
                                        .get(composite.getWebServiceAnnot().endpointInterface());
                        if (!DescriptionUtils.isEmpty(seic.getWebServiceAnnot().wsdlLocation())) {
                            annotation_WsdlLocation = seic.getWebServiceAnnot().wsdlLocation();
                        }
                    }
                }
            } else if (getAnnoWebServiceProvider() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebServiceProvider().wsdlLocation())) {
                annotation_WsdlLocation = getAnnoWebServiceProvider().wsdlLocation();
            } else {
                // There is no default value per JSR-181 MR Sec 4.1 pg 16
                annotation_WsdlLocation = "";
            }
        }
        return annotation_WsdlLocation;
    }

    public String getAnnoWebServiceServiceName() {
        if (annotation_ServiceName == null) {
            if (getAnnoWebService() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebService().serviceName())) {
                annotation_ServiceName = getAnnoWebService().serviceName();
            } else if (getAnnoWebServiceProvider() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebServiceProvider().serviceName())) {
                annotation_ServiceName = getAnnoWebServiceProvider().serviceName();
            } else {
                // Default value is the "simple name" of the class or interface + "Service"
                // Per JSR-181 MR Sec 4.1, pg 15
                annotation_ServiceName = DescriptionUtils.getSimpleJavaClassName(composite.getClassName()) + "Service";
            }
        }
        return annotation_ServiceName;
    }

    public String getAnnoWebServicePortName() {
        if (annotation_PortName == null) {
            if (getAnnoWebService() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebService().portName())) {
                annotation_PortName = getAnnoWebService().portName();
            } else if (getAnnoWebServiceProvider() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebServiceProvider().portName())) {
                annotation_PortName = getAnnoWebServiceProvider().portName();
            } else {
                // Default the value
                if (isProviderBased()) {
                    // This is the @WebServiceProvider annotation path
                    // Default value is not specified in JSR-224, but we can assume it is 
                    // similar to the default in the WebService case, however there is no
                    // name attribute for a WebServiceProvider.  So in this case we use 
                    // the default value for WebService.name per JSR-181 MR sec 4.1 pg 15.
                    // Note that this is really the same thing as the call to getWebServiceName() 
                    // in the WebService case; it is done sepertely just to be clear there is no 
                    // name element on the WebServiceProvider annotation
                    annotation_PortName = DescriptionUtils.getSimpleJavaClassName(composite.getClassName())
                        + "Port";
                } else {
                    // This is the @WebService annotation path
                    // Default value is the @WebService.name of the class or interface + "Port"
                    // Per JSR-181 MR Sec 4.1, pg 15
                    annotation_PortName = getAnnoWebServiceName() + "Port";
                }
            }
        }
        return annotation_PortName;
    }

    public String getAnnoWebServiceTargetNamespace() {
        if (annotation_TargetNamespace == null) {
            if (getAnnoWebService() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebService().targetNamespace())) {
                annotation_TargetNamespace = getAnnoWebService().targetNamespace();
            } else if (getAnnoWebServiceProvider() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebServiceProvider().targetNamespace())) {
                annotation_TargetNamespace = getAnnoWebServiceProvider().targetNamespace();
            } else {
                // Default value per JSR-181 MR Sec 4.1 pg 15 defers to "Implementation defined, 
                // as described in JAX-WS 2.0, section 3.2" which is JAX-WS 2.0 Sec 3.2, pg 29.
                // FIXME: Hardcoded protocol for namespace
                annotation_TargetNamespace =
                    DescriptionUtils.makeNamespaceFromPackageName(
                            DescriptionUtils.getJavaPackageName(composite.getClassName()),
                            "http");
            }
        }
        return annotation_TargetNamespace;
    }

    // ===========================================
    // ANNOTATION: WebServiceProvider
    // ===========================================

    public WebServiceProvider getAnnoWebServiceProvider() {
        return webServiceProviderAnnotation;
    }

    // ===========================================
    // ANNOTATION: WebService
    // ===========================================

    public WebService getAnnoWebService() {
        return webServiceAnnotation;
    }

    public String getAnnoWebServiceEndpointInterface() {
        // TODO: Validation: Not allowed on WebServiceProvider
        if (webService_EndpointInterface == null) {
            if (!isProviderBased() && getAnnoWebService() != null
                    && !DescriptionUtils.isEmpty(getAnnoWebService().endpointInterface())) {
                webService_EndpointInterface = getAnnoWebService().endpointInterface();
            } else {
                // This element is not valid on a WebServiceProvider annotation
                // REVIEW: Is this a correct thing to return if this is called against a WebServiceProvier
                //         which does not support this element?
                webService_EndpointInterface = "";
            }
        }
        return webService_EndpointInterface;
    }

    public String getAnnoWebServiceName() {
        // TODO: Validation: Not allowed on WebServiceProvider

        //TODO: Per JSR109 v1.2 Sec. 5.3.2.1
        //      If not specified then we can use the default value as specified in JSR 181
        //		(but only if it is unique within the module)...or If the name is
        //		not specified in the Service Implementation Bean then fully
        //		qualified name of the Bean class is used to guarantee uniqueness
        //		If the above is not unique then fully qualified name of the
        //		Bean class is used to guarantee uniqueness

        if (webService_Name == null) {
            if (!isProviderBased()) {
                if (getAnnoWebService() != null
                        && !DescriptionUtils.isEmpty(getAnnoWebService().name())) {
                    webService_Name = getAnnoWebService().name();
                } else {
                    webService_Name =
                        DescriptionUtils.getSimpleJavaClassName(composite.getClassName());
                }
            } else {
                // This element is not valid on a WebServiceProvider annotation
                // REVIEW: Is this a correct thing to return if this is called against a WebServiceProvier
                //         which does not support this element?
                webService_Name = "";
            }
        }
        return webService_Name;
    }

    // ===========================================
    // ANNOTATION: ServiceMode
    // ===========================================
    public ServiceMode getAnnoServiceMode() {

        if (serviceModeAnnotation == null) {
            serviceModeAnnotation = composite.getServiceModeAnnot();
        }
        return serviceModeAnnotation;
    }

    public Service.Mode getServiceMode() {
        // REVIEW: WSDL/Anno Merge
        return getAnnoServiceModeValue();
    }

    public Service.Mode getAnnoServiceModeValue() {
        // This annotation is only valid on Provider-based endpoints. 
        if (isProviderBased() && serviceModeValue == null) {
            if (getAnnoServiceMode() != null) {
                serviceModeValue = getAnnoServiceMode().value();
            } else {
                serviceModeValue = ServiceMode_DEFAULT;
            }
        }
        return serviceModeValue;
    }

    // ===========================================
    // ANNOTATION: BindingType
    // ===========================================

    public BindingType getAnnoBindingType() {
        if (bindingTypeAnnotation == null) {
            bindingTypeAnnotation = composite.getBindingTypeAnnot();
        }
        return bindingTypeAnnotation;
    }

    public String getBindingType() {
        // REVIEW: Implement WSDL/Anno merge?
        return getAnnoBindingTypeValue();
    }

    public String getAnnoBindingTypeValue() {
        if (bindingTypeValue == null) {
            if (getAnnoBindingType() != null &&
                    !DescriptionUtils.isEmpty(getAnnoBindingType().value())) {
                bindingTypeValue = getAnnoBindingType().value();
            } else {
                // No BindingType annotation present or value was empty; use default value
                bindingTypeValue = BindingType_DEFAULT;
            }
        }
        return bindingTypeValue;
    }

    // ===========================================
    // ANNOTATION: HandlerChain
    // ===========================================

    public void setHandlerChain(HandlerChainsType handlerChain) {
        handlerChainsType = handlerChain;
    }

    public HandlerChainsType getHandlerChain() {
        return getHandlerChain(null);
    }

    /**
     * Returns a schema derived java class containing the the handler configuration information.  
     * That information, returned in the HandlerChainsType object, is looked for in the following 
     * places in this order:
     * - Set on the sparseComposite for the given key
     * - Set on the composite
     * - Read in from the file specified on HandlerChain annotation
     * 
     * @return HandlerChainsType This is the top-level element for the Handler configuration file
     * 
     */
    public HandlerChainsType getHandlerChain(Object sparseCompositeKey) {
        // If there is a HandlerChainsType in the sparse composite for this ServiceDelegate
        // (i.e. this sparseCompositeKey), then return that.
        if (sparseCompositeKey != null) {
            DescriptionBuilderComposite sparseComposite = composite.getSparseComposite(sparseCompositeKey);
            if (sparseComposite != null && sparseComposite.getHandlerChainsType() != null) {
                return sparseComposite.getHandlerChainsType();
            }
        }
        
        // If there is no HandlerChainsType in the composite, then read in the file specified
        // on the HandlerChain annotation if it is present.
        if (handlerChainsType == null) {
            getAnnoHandlerChainAnnotation();
            if (handlerChainAnnotation != null) {
                String handlerFileName = handlerChainAnnotation.file();

                if (log.isDebugEnabled()) {
                    log.debug(Messages.getMessage("handlerChainsTypeErr",handlerFileName,composite.getClassName()));
                }

                String className = composite.getClassName();

                // REVIEW: This is using the classloader for EndpointDescriptionImpl; is that OK?
                ClassLoader classLoader = (composite.isServiceProvider() && !composite.isDeprecatedServiceProviderConstruction()) ?
                        composite.getClassLoader() :
                        (ClassLoader) AccessController.doPrivileged(
                                new PrivilegedAction() {
                                    public Object run() {
                                        return this.getClass().getClassLoader();
                                    }
                                }
                        );

                InputStream is = DescriptionUtils.openHandlerConfigStream(
                        handlerFileName,
                        className,
                        classLoader);

                if(is == null) {
                    log.warn("Unable to load handlers from file: " + handlerFileName);                    
                } else {
                    ClassLoader classLoader1 = (ClassLoader) AccessController.doPrivileged(
                            new PrivilegedAction() {
                                public Object run() {
                                    return this.getClass().getClassLoader();
                                }
                            }
                    );
                    handlerChainsType =
                        DescriptionUtils.loadHandlerChains(is, classLoader1);
                }
            }
        }
        return handlerChainsType;
    }
  
    public HandlerChain getAnnoHandlerChainAnnotation() {
        if (this.handlerChainAnnotation == null) {
            if (composite.isServiceProvider() && !composite.isDeprecatedServiceProviderConstruction()) {
                /*
                 * Per JSR-181 The @HandlerChain annotation MAY be present on
                 * the endpoint interface and service implementation bean. The
                 * service implementations bean's @HandlerChain is used if
                 * @HandlerChain is present on both. So, if we do find the
                 * annotation on this impl, then don't worry about else
                 * Otherwise, check to see if the SEI might be annotated with
                 * @HandlerChain
                 */

                handlerChainAnnotation = composite.getHandlerChainAnnot();
                if (handlerChainAnnotation == null) {

                    // If this is NOT an implicit SEI, then check for the
                    // annotation on the SEI
                    if (!DescriptionUtils.isEmpty(getAnnoWebServiceEndpointInterface())) {

                        DescriptionBuilderComposite seic = getServiceDescriptionImpl().getDBCMap()
                                        .get(composite.getWebServiceAnnot().endpointInterface());
                        if (seic != null) {
                            handlerChainAnnotation = seic.getHandlerChainAnnot();
                        }
                        //TODO else clause for if to throw exception when seic == null
                    }
                }
            } else {
                handlerChainAnnotation = composite.getHandlerChainAnnot();
            }
        }

        return handlerChainAnnotation;
    }

    // ===========================================
    // ANNOTATION: MTOM
    // ===========================================
    
    /*
     * (non-Javadoc)
     * @see org.apache.axis2.jaxws.description.EndpointDescription#isMTOMEnabled()
     */
    public boolean isMTOMEnabled() {
        if (isMTOMEnabledCache != null) {
            return isMTOMEnabledCache.booleanValue();
        }
        
        // isMTOMEnabled is a combination of the @BindingType and the @MTOM setting.
        MTOM mtomAnnotation =
                (MTOM) getAnnoFeature(MTOMFeature.ID);
        
        // If the @MTOM annotation is set, it wins
        if (mtomAnnotation != null) {
            isMTOMEnabledCache = Boolean.valueOf(mtomAnnotation.enabled());
            return isMTOMEnabledCache.booleanValue();
        }
        
        // Else look at the bindingType
        String bindingType = getBindingType();
        isMTOMEnabledCache = Boolean.valueOf(isMTOMBinding(bindingType));
        return isMTOMEnabledCache.booleanValue();
    }
    
    private static boolean isMTOMBinding(String url) {
        if (url != null && 
               (url.equals(SOAPBinding.SOAP11HTTP_MTOM_BINDING) ||
                       url.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING) ||
                       url.equals(MDQConstants.SOAP11JMS_MTOM_BINDING) ||
                       url.equals(MDQConstants.SOAP12JMS_MTOM_BINDING))) {
            return true;
        }
        return false;
    }
    
    // ===========================================
    // ANNOTATION: RespectBinding
    // ===========================================
    
    public boolean respectBinding() {
        return respectBinding;
    }
    
    public void setRespectBinding(boolean r) {
        respectBinding = r;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.axis2.jaxws.description.EndpointDescription#getMTOMThreshold()
     */
    public int getMTOMThreshold() {
        if (axisService != null) {
            // We should cache this call here so we don't have to make
            // it on every pass through.
            Parameter mtomThreshold = axisService.getParameter(Configuration.MTOM_THRESHOLD);
            if (mtomThreshold != null) {
                return (Integer) mtomThreshold.getValue();
            }
        }
        
        return -1;
    }
    
    // Get the specified WebServiceFeatureAnnotation
    public Annotation getAnnoFeature(String id) {
        return framework.getAnnotation(id);
    }
    
    //The WebServiceFeatures should be configued last so that any other
    //configuration can be overridden. Should only be called on the
    //server side.
    private void configureWebServiceFeatures() {
        String bindingType = getBindingType();
        Set<String> ids = ServerConfiguratorRegistry.getIds();
        
        for (String id : ids) {
            ServerConfigurator configurator = ServerConfiguratorRegistry.getConfigurator(id);
            
            if (configurator.supports(bindingType))
                framework.addConfigurator(id, configurator);
        }
    	
        // The feature instances are stored on the composite from either the 
        // Java class or from something else building the list and setting it there.
        List<Annotation> features = composite.getWebServiceFeatures();
        
        if (features != null && features.size() > 0) {
            // Add each of the annotation instances to the WebServiceFeature framework
            Iterator<Annotation> itr = features.iterator();
            while (itr.hasNext()) {
                Annotation feature = (Annotation) itr.next();
                framework.addAnnotation(feature);
            }
            
            // Kick off the configuration of the WebServiceFeature instances.
            framework.configure(this);
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("No WebServiceFeatureAnnotation instances were found on the composite.");
            }
        }   
    }
    
    private Definition getWSDLDefinition() {
        return ((ServiceDescriptionWSDL)getServiceDescription()).getWSDLDefinition();
    }

    public javax.wsdl.Service getWSDLService() {
        Definition defn = getWSDLDefinition();
        if (defn != null) {
            return defn.getService(getServiceQName());
        } else {
            return null;
        }
    }

    public Port getWSDLPort() {
        javax.wsdl.Service service = getWSDLService();
        if (service != null) {
            return service.getPort(getPortQName().getLocalPart());
        } else {
            return null;
        }
    }

    public Binding getWSDLBinding() {
        Binding wsdlBinding = null;
        Port wsdlPort = getWSDLPort();
        Definition wsdlDef = getWSDLDefinition();
        if (wsdlPort != null && wsdlDef != null) {
            wsdlBinding = wsdlPort.getBinding();
        }
        return wsdlBinding;
    }

    public String getWSDLBindingType() {
        String wsdlBindingType = null;
        String soapTransport = null;
        Binding wsdlBinding = getWSDLBinding();
        if (wsdlBinding != null) {
        	
            // If a WSDL binding was found, we need to find the proper extensibility
            // element and return the namespace.  The namespace for the binding element will 
        	// determine whether it is SOAP 1.1 vs. SOAP 1.2 vs. HTTP (or other). If the namespace 
        	// indicates SOAP we then need to determine what the transport is (HTTP vs. JMS)
            // TODO: What do we do if no extensibility element exists?
            List<ExtensibilityElement> elements = wsdlBinding.getExtensibilityElements();
            Iterator<ExtensibilityElement> itr = elements.iterator();
            while (itr.hasNext()) {
                ExtensibilityElement e = itr.next();
                if (javax.wsdl.extensions.soap.SOAPBinding.class.isAssignableFrom(e.getClass())) {
                    javax.wsdl.extensions.soap.SOAPBinding soapBnd =
                            (javax.wsdl.extensions.soap.SOAPBinding)e;
                    
                    //representation: this is soap:binding = elementType where NamespaceURI is "soap"
                    // The transport is represented by the 'transport' attribute within this binding element
                    wsdlBindingType = soapBnd.getElementType().getNamespaceURI();

                    soapTransport = soapBnd.getTransportURI();


                    break;
                
                } else if (SOAP12Binding.class.isAssignableFrom(e.getClass())) {
                    SOAP12Binding soapBnd = (SOAP12Binding)e;
                    wsdlBindingType = soapBnd.getElementType().getNamespaceURI();
                    soapTransport = soapBnd.getTransportURI();
                    break;
                
                } else if (HTTPBinding.class.isAssignableFrom(e.getClass())) {
                    HTTPBinding httpBnd = (HTTPBinding)e;
                    wsdlBindingType = httpBnd.getElementType().getNamespaceURI();
                    break;
                }
            }

            // We need to convert the wsdl-based SOAP and HTTP namespace into the expected Binding Type for 
            // HTTP or SOAPBindings with the appropriate transport (HTTP, JMS, etc.)
            //
            // Note that what we're actually returning is the WSDL binding type value conveted
            // to the corresponding SOAPBinding or HTTPBinding value.  We are overwite the 
            // wsdlBindingType with that converted JAXWS annotation binding type value and
            // return it.
            wsdlBindingType = DescriptionUtils.mapBindingTypeWsdlToAnnotation(wsdlBindingType, soapTransport);
        }
        return wsdlBindingType;
    }

    public String getName() {
        return getAnnoWebServiceName();
    }

    public String getTargetNamespace() {
        return getAnnoWebServiceTargetNamespace();
    }

    public PortInfo getPortInfo() {
        if (portInfo == null) {
            portInfo = new PortInfoImpl(getServiceQName(), getPortQName(), getBindingType());
        }
        return portInfo;
    }

    public void setClientBindingID(String clientBindingID) {

        if (clientBindingID == null) {
            this.clientBindingID = DEFAULT_CLIENT_BINDING_ID;
        } else if (validateClientBindingID(clientBindingID)) {
            this.clientBindingID = clientBindingID;
        } else {
            throw ExceptionFactory.makeWebServiceException(
                    Messages.getMessage("addPortErr0", getPortQName().toString()));
        }
    }

    private boolean validateClientBindingID(String bindingId) {
        boolean isValid = true;
        if (bindingId != null && !(bindingId.equals(SOAPBinding.SOAP11HTTP_BINDING) ||
                bindingId.equals(javax.xml.ws.http.HTTPBinding.HTTP_BINDING) ||
                bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING) ||
                bindingId.equals(SOAPBinding.SOAP11HTTP_MTOM_BINDING) ||
                bindingId.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING) ||
                bindingId.equals(MDQConstants.SOAP11JMS_BINDING) ||
                bindingId.equals(MDQConstants.SOAP12JMS_BINDING) ||
                bindingId.equals(MDQConstants.SOAP11JMS_MTOM_BINDING) ||
                bindingId.equals(MDQConstants.SOAP12JMS_MTOM_BINDING))) {
            throw ExceptionFactory.makeWebServiceException(
                    Messages.getMessage("addPortErr0", getPortQName().toString()));
        }
        return isValid;
    }

    public String getClientBindingID() {
        if (clientBindingID == null) {
            if (getWSDLDefinition() != null) {
                clientBindingID = getWSDLBindingType();
                if (clientBindingID == null) {
                    clientBindingID = DEFAULT_CLIENT_BINDING_ID;
                }
            } else {
                clientBindingID = DEFAULT_CLIENT_BINDING_ID;
            }
        }
        return clientBindingID;
    }

    public void setEndpointAddress(String endpointAddress) {
        // REVIEW: Should this be called whenever BindingProvider.ENDPOINT_ADDRESS_PROPERTY is set by the client?
        if (!DescriptionUtils.isEmpty(endpointAddress)) {
            this.endpointAddress = endpointAddress;
        } else {
            // Since a port can be added without setting an endpoint address, this is not an error.
            if (log.isDebugEnabled())
                log.debug("A null or empty endpoint address was attempted to be set",
                          new Throwable("Stack Traceback"));
        }
    }

    public String getEndpointAddress() {
        if (endpointAddress == null) {
            // If the endpointAddress has not been set explicitly by a call to setEndpointAddress()
            // then try to get it from the WSDL
            endpointAddress = getWSDLSOAPAddress();
        }
        return endpointAddress;
    }
    
    public void setProperty(String key, Object value) {
        if(properties == null) {
            properties = new HashMap<String, Object>();
        }
        properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        if(properties != null) {
           return properties.get(key);
        }
        return null;
    }

    /**
     * Return the SOAP Address from the WSDL for this port.
     *
     * @return The SOAP Address from the WSDL for this port or null.
     */
    public String getWSDLSOAPAddress() {
        if (wsdlSOAPAddress == null) {
            Port wsdlPort = getWSDLPort();
            if (wsdlPort != null) {
                // The port is in the WSDL, so see if it has a SOAP address extensibility element specified.
                List extElementList = wsdlPort.getExtensibilityElements();
                for (Object listElement : extElementList) {
                    ExtensibilityElement extElement = (ExtensibilityElement)listElement;
                    if (isSOAPAddressElement(extElement)) {
                        String soapAddress = getSOAPAddressFromElement(extElement);
                        if (!DescriptionUtils.isEmpty(soapAddress)) {
                            wsdlSOAPAddress = soapAddress;
                        }
                    }
                }
            }
        }
        return wsdlSOAPAddress;
    }

    /**
     * Determine if the WSDL Extensibility element corresponds to the SOAP Address element.
     *
     * @param exElement
     * @return
     */
    static boolean isSOAPAddressElement(ExtensibilityElement exElement) {
        boolean isAddress = false;
        if (exElement != null) {
            isAddress = (SOAP_11_ADDRESS_ELEMENT.equals(exElement.getElementType())
                    ||
                    (SOAP_12_ADDRESS_ELEMENT.equals(exElement.getElementType())));
        }
        return isAddress;
    }

    static String getSOAPAddressFromElement(ExtensibilityElement extElement) {
        String returnAddress = null;

        if (extElement != null) {
            if (SOAP_11_ADDRESS_ELEMENT.equals(extElement.getElementType())) {
                returnAddress = ((SOAPAddress)extElement).getLocationURI();
            } else if (SOAP_12_ADDRESS_ELEMENT.equals(extElement.getElementType())) {
                returnAddress = ((SOAP12Address)extElement).getLocationURI();
            }
        }

        return returnAddress;
    }

    /**
     * Selects a port to use in the case where a portQName was not specified by the client on the
     * Service.getPort(Class) call.  If WSDL is present, then an appropriate port is looked for
     * under the service element, and an exception is thrown if none can be found.  If WSDL is not
     * present, then the selected port is simply the one determined by annotations.
     *
     * @return A QName representing the port that is to be used.
     */
    private QName selectPortToUse() {
        QName portToUse = null;
        // If WSDL Service for this port is present, then we'll find an appropriate port defined in there and set 
        // the name accordingly.  If no WSDL is present, the the PortQName getter will use annotations to set the value.
        if (getWSDLService() != null) {
            portToUse = selectWSDLPortToUse();
        } else {
            // No WSDL, so the port to use is the one defined by the annotations.
            portToUse = getPortQName();
        }
        return portToUse;
    }

    /**
     * Look through the WSDL Service for a port that should be used.  If none can be found, then
     * throw an exception.
     *
     * @param wsdlService
     * @return A QName representing the port from the WSDL that should be used.
     */
    private QName selectWSDLPortToUse() {
        QName wsdlPortToUse = null;

        // To select which WSDL Port to use, we do the following
        // 1) Find the subset of all ports under the service that use the PortType represented by the SEI
        // 2) From the subset in (1) find all those ports that specify a SOAP Address
        // 3) Use the first port from (2)
        // REVIEW: Should we be looking at the binding type or something else to determin which subset of ports to use;
        //         i.e. instead of just finding ports that specify a SOAP Address?

        // Per JSR-181, 
        // - The portType name corresponds to the WebService.name annotation value, which is
        //   returned by getName()
        // - The portType namespace corresponds to the WebService.targetNamespace annotation, which
        //   is returned by getTargetNamespace()
        String portTypeLP = getName();
        String portTypeTNS = getTargetNamespace();
        QName portTypeQN = new QName(portTypeTNS, portTypeLP);

        ServiceDescriptionWSDL serviceDescWSDL = (ServiceDescriptionWSDL)getServiceDescription();

        List<Port> wsdlPortsUsingPortType = serviceDescWSDL.getWSDLPortsUsingPortType(portTypeQN);
        List<Port> wsdlPortsUsingSOAPAddresses =
                serviceDescWSDL.getWSDLPortsUsingSOAPAddress(wsdlPortsUsingPortType);
        if (wsdlPortsUsingSOAPAddresses != null && !wsdlPortsUsingSOAPAddresses.isEmpty()) {
            // We return the first port that uses the particluar PortType and has a SOAP address.
            // HOWEVER, that is not necessarily the first one in the WSDL that meets that criteria!  
            // The problem is that WSDL4J Service.getPorts(), which is used to get a Map of ports under the service 
            // DOES NOT return the ports in the order they are defined in the WSDL.  
            // Therefore, we can't necessarily predict which one we'll get back as the "first" one in the collection.
            // REVIEW: Note the above comment; is there anything more predictible and determinstic we can do?
            Port portToUse = (Port)wsdlPortsUsingSOAPAddresses.toArray()[0];
            String portLocalPart = portToUse.getName();
            String portNamespace = serviceDescWSDL.getWSDLService().getQName().getNamespaceURI();
            wsdlPortToUse = new QName(portNamespace, portLocalPart);
        }

        return wsdlPortToUse;
    }

    private WsdlComposite generateWSDL(DescriptionBuilderComposite dbc) {

        WsdlComposite wsdlComposite = null;
        Definition defn = dbc.getWsdlDefinition();
        if (defn == null || !isAxisServiceBuiltFromWSDL) {

            //Invoke the callback for generating the wsdl
            if (dbc.getCustomWsdlGenerator() != null) {
                String implName = null;
                if (axisService == null) {
                    implName = DescriptionUtils.javifyClassName(composite.getClassName());
                } else {
                    implName = (String)axisService.getParameterValue(MDQConstants.SERVICE_CLASS);
                }
                wsdlComposite =
                        dbc.getCustomWsdlGenerator().generateWsdl(implName, this);

                if (wsdlComposite != null) {
                    wsdlComposite.setWsdlFileName(
                            (this.getAnnoWebServiceServiceName() + ".wsdl").toLowerCase());

                    Definition wsdlDef = wsdlComposite.getRootWsdlDefinition();

                    try {
                        ConfigurationContext cc = dbc.getConfigurationContext();
                        WSDL4JWrapper wsdl4jWrapper = null;
                        if (cc != null) {
                            wsdl4jWrapper = new WSDL4JWrapper(dbc.getWsdlURL(), wsdlDef, cc);
                        } else {
                            wsdl4jWrapper = new WSDL4JWrapper(dbc.getWsdlURL(), wsdlDef, true, 2);
                        }
                        getServiceDescriptionImpl().setGeneratedWsdlWrapper(wsdl4jWrapper);
                    } catch (Exception e) {
                        throw ExceptionFactory.makeWebServiceException(Messages.getMessage("generateWSDLErr"),e);
                    }
                } else {
                    // REVIEW:Determine if we should always throw an exception on this, or at this point
                    //throw ExceptionFactory.makeWebServiceException("EndpointDescriptionImpl: Unable to find custom WSDL generator");
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "The custom WSDL generator returned null, so no generated WSDL is available");
                    }

                }
            } else {
                // REVIEW: This used to throw an exception, but it seems we shouldn't require
                // a wsdl generator be provided.
//                throw ExceptionFactory.makeWebServiceException("EndpointDescriptionImpl: Unable to find custom WSDL generator");
                if (log.isDebugEnabled()) {
                    log.debug(
                            "No custom WSDL generator was supplied, so WSDL can not be generated");
                }
            }
        }
        return wsdlComposite;
    }
    
    List<CustomAnnotationInstance> getCustomAnnotationInstances() {
        return customAnnotations;
    }
    
    CustomAnnotationProcessor getCustomAnnotationProcessor(String annotationInstanceClassName) {
        return customAnnotationProcessors != null ? 
                customAnnotationProcessors.get(annotationInstanceClassName) : null;
    }
    
    public DescriptionBuilderComposite getDescriptionBuilderComposite() {
        return composite;
    }

    public String toString() {
        final String newline = "\n";
        final String sameline = "; ";
        StringBuffer string = new StringBuffer();
        try {
            string.append(super.toString());
            string.append(newline);
            string.append("Name: " + getName());
            string.append(sameline);
            string.append("Endpoint Address: " + getEndpointAddress());
            //
            string.append(newline);
            string.append("ServiceQName: " + getServiceQName());
            string.append(sameline);
            string.append("PortQName: " + getPortQName());
            string.append(sameline);
            string.append("TargetNamespace: " + getTargetNamespace());
            //
            string.append(newline);
            string.append("Service Mode: " + getServiceMode());
            string.append(sameline);
            string.append("Binding Type: " + getBindingType());
            string.append(sameline);
            string.append("Client Binding Type: " + getClientBindingID());
            //
            string.append(newline);
            string.append("Is provider-based: " + (isProviderBased() == true));
            string.append(sameline);
            string.append("Is proxy-based: " + (isEndpointBased() == true));
            string.append(sameline);
            string.append("Is WSDL fully specified: " + (isWSDLFullySpecified() == true));
            //
            string.append(newline);
            string.append("AxisService: " + getAxisService());
            //
            string.append(newline);
            EndpointInterfaceDescription endpointInterfaceDesc = getEndpointInterfaceDescription();
            if (endpointInterfaceDesc != null) {
                string.append("EndpointInterfaceDescription: " + endpointInterfaceDesc.toString());
            } else {
                string.append("EndpointInterfaceDescription is null.");
            }
        }
        catch (Throwable t) {
            string.append(newline);
            string.append("Complete debug information not currently available for " +
                    "EndpointDescription");
            return string.toString();
        }
        return string.toString();
    }
    
    /**
     * Get an annotation.  This is wrappered to avoid a Java2Security violation.
     * @param cls Class that contains annotation 
     * @param annotation Class of requrested Annotation
     * @return annotation or null
     */
    private static Annotation getAnnotation(final Class cls, final Class annotation) {
        return (Annotation) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return cls.getAnnotation(annotation);
            }
        });
    }

    /**
     * Return the class for this name
     *
     * @return Class
     */
    private static Class forName(final String className, final boolean initialize,
                                final ClassLoader classloader) throws ClassNotFoundException {
        Class cl = null;
        try {
            cl = (Class) AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public Object run() throws ClassNotFoundException {
                            return Class.forName(className, initialize, classloader);
                        }
                    }
            );
        } catch (PrivilegedActionException e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception thrown from AccessController: " + e.getMessage(), e);
            }
            throw (ClassNotFoundException) e.getException();
        }

        return cl;
    }

    private static ClassLoader getContextClassLoader(final ClassLoader classLoader) {
        ClassLoader cl;
        try {
            cl = (ClassLoader) AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public Object run() throws ClassNotFoundException {
                            return classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
                        }
                    }
            );
        } catch (PrivilegedActionException e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception thrown from AccessController: " + e.getMessage(), e);
            }
            throw ExceptionFactory.makeWebServiceException(e.getException());
        }

        return cl;
    }
    
    /**
     * This will update or set the parameter on the AxisService that represents the
     * value of the @WebService.name attribute. This is needed since the @WebService.name
     * value may not be known until the EndpointInterfaceDescription is created for
     * the explicitly defined SEI.
     */
    void updateWebServiceNameParameter(String newName, AxisService service) {
        if(log.isDebugEnabled()) {
            log.debug("Setting @WebService.name value on the " + service.getName() + 
                      " AxisService to: " + newName);
        }
        Parameter param = service.getParameter(MDQConstants.WSDL_PORTTYPE_NAME);
        if(param != null) {
            param.setValue(newName);
        }
        else {
            param = new Parameter();
            param.setName(MDQConstants.WSDL_PORTTYPE_NAME);
            param.setValue(newName);
            try {
              service.addParameter(param);  
            }
            catch (AxisFault e) {
                throw ExceptionFactory.makeWebServiceException(Messages.getMessage("setupAxisServiceErr2"),e);
            }
        }
    }
}

