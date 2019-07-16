package com.adaptris.core.services.cxf;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.cxf.BusFactory;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.staxutils.StaxSource;

import com.adaptris.annotation.AdapterComponent;
import com.adaptris.annotation.AdvancedConfig;
import com.adaptris.annotation.ComponentProfile;
import com.adaptris.annotation.DisplayOrder;
import com.adaptris.annotation.InputFieldDefault;
import com.adaptris.annotation.InputFieldHint;
import com.adaptris.annotation.Removal;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.ServiceImp;
import com.adaptris.core.metadata.MetadataFilter;
import com.adaptris.core.metadata.RemoveAllMetadataFilter;
import com.adaptris.core.util.ExceptionHelper;
import com.adaptris.interlok.resolver.ExternalResolver;
import com.adaptris.security.password.Password;
import com.adaptris.util.TimeInterval;
import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * <p>
 * SOAP Service Requester that will invoke a webservice using the payload of the message as the operation argument (the service
 * looks after the SOAP envelope).
 * </p>
 * <p>
 * Note that this service attempts to set the system property <code>org.apache.cxf.stax.allowInsecureParser</code> to 1 to remove
 * the dependency on the woodstox stax implementation. Woodstox causes an incompatibility with XStream when it comes to
 * unmarshalling CDATA tags, which can present problems if you are embedding XML as part of {@link com.adaptris.core.PollingTrigger}
 * or {@link com.adaptris.core.services.metadata.PayloadFromMetadataService} in your adapter configuration. This will cause a
 * warning in the log file which can be safely ignored.
 * </p>
 * <p>
 * If you wish to use woodstox, then explicitly set the property to your preferred value (probably <code>false</code>)as the service
 * will only attempt to set it if it has not already been set.
 * </p>
 * 
 * @config apache-cxf-soap-service
 * 
 */
@XStreamAlias("apache-cxf-soap-service")
@AdapterComponent
@ComponentProfile(summary = "Execute a webservice using CXF", tag = "service,webservices,cxf")
@DisplayOrder(order = {"wsdlUrl", "portName", "serviceName", "soapAction", "endpointAddress", "wsdlPortUrl", "username", "password",
    "useFallbackTransformer", "perMessageDispatch"})
public class ApacheSoapService extends ServiceImp {

  private static final TimeInterval DEFAULT_REQUEST_TIMEOUT = new TimeInterval(50l, TimeUnit.SECONDS);
  private static final TimeInterval DEFAULT_CONNECTION_TIMEOUT = new TimeInterval(10l, TimeUnit.SECONDS);

  // These are just here so we don't need to reference BindingProviderProperties or JAXWSProperties.
  private static final String COM_SUN_REQUEST_TIMEOUT = "com.sun.xml.internal.ws.request.timeout";
  private static final String COM_SUN_ALT_REQUEST_TIMEOUT = "com.sun.xml.ws.request.timeout";
  private static final String COM_SUN_CONNECT_TIMEOUT = "com.sun.xml.internal.ws.connect.timeout";
  private static final String COM_SUN_ALT_CONNECT_TIMEOUT = "com.sun.xml.internal.ws.request.timeout";

  public static final String FALLBACK_TRANSFORMER_FACTORY_IMPL = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

  private String wsdlUrl;
  private String portName;
  private String serviceName;
  private String namespace;
  @AdvancedConfig
  private String soapAction;
  @AdvancedConfig
  @Deprecated
  @Removal(version = "3.10.0")
  private String wsdlPortUrl;
  @AdvancedConfig
  private String endpointAddress;
  private String username;
  @InputFieldHint(style = "PASSWORD", external = true)
  private String password;
  @AdvancedConfig
  private TimeInterval connectionTimeout;
  @AdvancedConfig
  private TimeInterval requestTimeout;
  @AdvancedConfig
  @InputFieldDefault(value = "false")
  @Deprecated
  @Removal(version = "3.10.0")
  private Boolean enableDebug;
  @AdvancedConfig
  @InputFieldDefault(value = "false")
  private Boolean perMessageDispatch;
  @InputFieldDefault(value = "false")
  private Boolean useFallbackTransformer;
  @AdvancedConfig
  @InputFieldDefault(value = "remove-all-metadata")
  private MetadataFilter metadataFilter;

  private transient Transformer transformer;
  private transient DispatchBuilder dispatchBuilder;

  public enum DispatchConfig {
    Username() {
      @Override
      void apply(Dispatch<Source> d, ConfigItem c) throws Exception {
        if (isNotEmpty(c.asString())) {
          d.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, c.asString());

        }
      }

    },
    Password() {
      @Override
      void apply(Dispatch<Source> d, ConfigItem c) throws Exception {
        if (isNotEmpty(c.asString())) {
          d.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, c.asString());

        }
      }

    },
    EndpointAddress() {
      @Override
      void apply(Dispatch<Source> d, ConfigItem c) throws Exception {
        if (isNotEmpty(c.asString())) {
          d.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, c.asString());

        }
      }

    },
    SoapAction() {
      @Override
      void apply(Dispatch<Source> d, ConfigItem c) throws Exception {
        if (isNotEmpty(c.asString())) {
          d.getRequestContext().put(Dispatch.SOAPACTION_USE_PROPERTY, true);
          d.getRequestContext().put(Dispatch.SOAPACTION_URI_PROPERTY, c.asString());
        }
      }
    };

    abstract void apply(Dispatch<Source> d, ConfigItem c) throws Exception;
  }

  static {
    // Because we can't depend on woodstox due to XStream issues, we have to
    // allow an insecure parser.
    if (isEmpty(System.getProperty("org.apache.cxf.stax.allowInsecureParser"))) {
      System.setProperty("org.apache.cxf.stax.allowInsecureParser", "1");
    }
  }

  public ApacheSoapService() {
  }

  @Override
  protected void closeService() {
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void initService() throws CoreException {
    try {
      URL wsdlURL = new URL(getWsdlUrl());
      if (debugEnabled()) {
        BusFactory.getDefaultBus().getOutInterceptors().add(new LoggingOutInterceptor());
      }
      Service service = Service.create(wsdlURL, new QName(getNamespace(), getServiceName()));
      if (perMessageDispatch()) {
        dispatchBuilder = new PerMessageDispatcher(service);
      } else {
        dispatchBuilder = new PersistentDispatcher(service);
      }
      if (useJavaFallBackTransformer()) {
        transformer = TransformerFactory.newInstance(FALLBACK_TRANSFORMER_FACTORY_IMPL, Thread.currentThread().getContextClassLoader())
            .newTransformer();
      } else {
        transformer = TransformerFactory.newInstance().newTransformer();
      }
    }
    catch (Exception e) {
      throw ExceptionHelper.wrapCoreException(e);
    }
  }

  @Override
  public void doService(AdaptrisMessage msg) throws ServiceException {
    try (InputStream in = msg.getInputStream(); OutputStream out = msg.getOutputStream()) {
      Dispatch<Source> dispatcher = dispatchBuilder.build(msg);
      MetadataToRequestHeaders.register(metadataFilter().filter(msg), dispatcher);
      XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
      xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
      StaxSource source = new StaxSource(xmlInputFactory.createXMLStreamReader(in));
      Source response = dispatcher.invoke(source);
      transformer.transform(response, new StreamResult(out));
    }
    catch (Exception e) {
      throw new ServiceException("Failed to invoke service [" + getServiceName() + "] for operation [" + getPortName() + "]", e);
    }
  }

  @Override
  public void prepare() throws CoreException {}

  private Dispatch<Source> configureTimeouts(Dispatch<Source> d) {
    d.getRequestContext().put(COM_SUN_REQUEST_TIMEOUT, requestTimeout());
    d.getRequestContext().put(COM_SUN_ALT_REQUEST_TIMEOUT, requestTimeout());
    d.getRequestContext().put(COM_SUN_CONNECT_TIMEOUT, connectTimeout());
    d.getRequestContext().put(COM_SUN_ALT_CONNECT_TIMEOUT, connectTimeout());
    return d;
  }

  /**
   * The URL from which to download the WSDL.
   * 
   * @return wsdlUrl
   */
  public String getWsdlUrl() {
    return wsdlUrl;
  }

  /**
   * The URL from which to download the WSDL.
   * 
   * @param wsdlUrl
   */
  public void setWsdlUrl(String wsdlUrl) {
    this.wsdlUrl = wsdlUrl;
  }

  /**
   * The port name for the WSDL.
   * 
   * @return portName
   */
  public String getPortName() {
    return portName;
  }

  /**
   * The port name for the WSDL.
   * 
   * @param portName
   */
  public void setPortName(String portName) {
    this.portName = portName;
  }

  /**
   * The name of the Service to be invoked.
   * 
   * @return serviceName
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * The name of the Service to be invoked.
   * 
   * @param serviceName
   */
  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  /**
   * The namespace of this Service.
   * 
   */
  public String getNamespace() {
    return namespace;
  }

  /**
   * The namespace of this Service.
   * 
   * @param namespace
   */
  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  /**
   * The SOAP Action to be used.
   * 
   * @return soapAction
   */
  public String getSoapAction() {
    return soapAction;
  }

  /**
   * The SOAP Action to be used.
   * 
   * @param soapAction
   */
  public void setSoapAction(String soapAction) {
    this.soapAction = soapAction;
  }

  /**
   * This optional property may be used to specify a different service address to that specified in the WSDL.
   * 
   * @return wsdlPortUrl
   * @deprecated since 3.8.2; use {@link #getEndpointAddress()} instead as this matches the
   *             {@code BindingProvider#ENDPOINT_ADDRESS_PROPERTY} better.
   */
  @Deprecated
  @Removal(version = "3.10.0")
  public String getWsdlPortUrl() {
    return wsdlPortUrl;
  }

  /**
   * This optional property may be used to specify a different service address to that specified in the WSDL.
   * 
   * @param wsdlPortUrl
   * @deprecated since 3.8.2; use {@link #setEndpointAddress(String)} instead as this matches the
   *             {@code BindingProvider#ENDPOINT_ADDRESS_PROPERTY} better.
   */
  @Deprecated
  @Removal(version = "3.10.0")
  public void setWsdlPortUrl(String wsdlPortUrl) {
    this.wsdlPortUrl = wsdlPortUrl;
  }

  public String getEndpointAddress() {
    return endpointAddress;
  }

  public void setEndpointAddress(String endpointAddress) {
    this.endpointAddress = endpointAddress;
  }

  protected String endpointAddress() {
    if (isNotEmpty(getWsdlPortUrl())) {
      log.warn("Use of deprecated wsdl-port-url; use endpoint-address instead");
      return getWsdlPortUrl();
    }
    return getEndpointAddress();
  }


  /**
   * Provide additional web service debugging information.
   * 
   * @return enableDebug true = provide web service debug information
   * @deprecated since 3.8.2; use log4j/slf4j/java.util.logging to get debug logging c.f. <a
   *             href="http://cxf.apache.org/docs/general-cxf-logging.html>CXF Logging</a>
   */
  @Deprecated
  @Removal(version = "3.10.0")
  public Boolean getEnableDebug() {
    return enableDebug;
  }

  /**
   * Whether to provide additional debugging information.
   * 
   * @param b true = provide web service debug information
   * @deprecated since 3.8.2; use log4j/slf4j/java.util.logging to get debug logging c.f. <a
   *             href="http://cxf.apache.org/docs/general-cxf-logging.html>CXF Logging</a>
   */
  @Deprecated
  @Removal(version = "3.10.0")
  public void setEnableDebug(Boolean b) {
    this.enableDebug = b;
  }

  @Deprecated
  private boolean debugEnabled() {
    return BooleanUtils.toBooleanDefaultIfNull(getEnableDebug(), false);
  }

  /**
   * The time to wait for a connection to this service invocation.
   * 
   * @return connectionTimeout
   */
  public TimeInterval getConnectionTimeout() {
    return connectionTimeout;
  }

  /**
   * The time to wait for a connection to this service invocation.
   * 
   * @param ti the connect timeout, if unspecified defaults to 10 seconds
   */
  public void setConnectTimeout(TimeInterval ti) {
    this.connectionTimeout = ti;
  }

  /**
   * The time to wait for a request completion for this service invocation.
   * 
   * @return requestTimeout
   */
  public TimeInterval getRequestTimeout() {
    return requestTimeout;
  }

  /**
   * The time to wait for a request completion for this service invocation.
   * 
   * @param rt the request timeout, if unspecified defaults to 50 seconds.
   */
  public void setRequestTimeout(TimeInterval rt) {
    this.requestTimeout = rt;
  }

  /**
   * Optional username for HTTP basic authentication.
   * 
   * @return username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Optional username for HTTP basic authentication.
   * 
   * @param username
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Optional password for HTTP basic authentication.
   * 
   */
  public String getPassword() {
    return password;
  }

  /**
   * Optional password for HTTP basic authentication.
   * 
   * @param pw the password which may be encoded by {@link Password#encode(String, String)}
   */
  public void setPassword(String pw) {
    this.password = pw;
  }

  private int connectTimeout() {
    return (int) TimeInterval.toMillisecondsDefaultIfNull(getConnectionTimeout(), DEFAULT_CONNECTION_TIMEOUT);
  }

  private int requestTimeout() {
    return (int) TimeInterval.toMillisecondsDefaultIfNull(getRequestTimeout(), DEFAULT_REQUEST_TIMEOUT);
  }

  public Boolean getPerMessageDispatch() {
    return perMessageDispatch;
  }

  /**
   * Set this to true if you want to dynamically resolve {@link #getSoapAction()}, {@link #getUsername()}, {@link #getPassword()}
   * and {@link #getEndpointAddress()} dynamically from the message using the standard expression syntax.
   * <p>
   * Note that setting this to true, will cause a new {@code Dispatch} object to be created during every
   * {@link #doService(AdaptrisMessage)} method, this may incur a high cost of initialisation.
   * </p>
   * 
   * @param b true to enable, default is false.
   */
  public void setPerMessageDispatch(Boolean b) {
    this.perMessageDispatch = b;
  }

  private boolean perMessageDispatch() {
    return BooleanUtils.toBooleanDefaultIfNull(getPerMessageDispatch(), false);
  }

  public Boolean getUseFallbackTransformer() {
    return useFallbackTransformer;
  }

  /**
   * Specify whether to force the service to use the java fallback {@link TransformerFactory} rather than discovering one via the
   * normal {@link TransformerFactory#newInstance()}.
   * <p>
   * Recent versions of {@code net.sf.saxon:Saxon-HE} (specificially 9.9.x onwards) have strong opinions and may omit namespace
   * attributes when their identity transformer is used; this can result in something that is unexpected to downstream services. If
   * this is the case for you, then set this to be true; and you will use the {@value #FALLBACK_TRANSFORMER_FACTORY_IMPL} as the
   * transformer factory instead.
   * </p>
   * 
   * @param b true to use {@value #FALLBACK_TRANSFORMER_FACTORY_IMPL} as the {@link TransformerFactory}; false if not explicitly
   *        configured.
   * @since 3.9.1
   */
  public void setUseFallbackTransformer(Boolean b) {
    this.useFallbackTransformer = b;
  }

  private boolean useJavaFallBackTransformer() {
    return BooleanUtils.toBooleanDefaultIfNull(getUseFallbackTransformer(), false);
  }


  public MetadataFilter getMetadataFilter() {
    return metadataFilter;
  }

  /**
   * Allows you to send arbitrary metadata as HTTP headers.
   * <p>
   * Uses {@link MetadataToRequestHeaders} to add the filtered metadata as HTTP Request headers.
   * </p>
   * 
   * @param filter the filter to apply on the metadata; default is {@link RemoveAllMetadataFilter} if not explicitly specified.
   */
  public void setMetadataFilter(MetadataFilter filter) {
    this.metadataFilter = filter;
  }

  protected MetadataFilter metadataFilter() {
    return ObjectUtils.defaultIfNull(getMetadataFilter(), new RemoveAllMetadataFilter());
  }

  @FunctionalInterface
  protected interface ConfigItem {
    String asString();
  }

  protected interface DispatchBuilder {
    Dispatch<Source> build(AdaptrisMessage msg) throws Exception;
  }

  private class PersistentDispatcher implements DispatchBuilder {

    private Dispatch<Source> dispatch;

    private PersistentDispatcher(Service s) throws Exception {
      dispatch = s.createDispatch(new QName(getNamespace(), getPortName()), Source.class, Service.Mode.PAYLOAD);
      final String pw = Password.decode(ExternalResolver.resolve(getPassword()));
      DispatchConfig.SoapAction.apply(dispatch, () -> { return getSoapAction(); });
      DispatchConfig.EndpointAddress.apply(dispatch, () -> {return endpointAddress(); });
      DispatchConfig.Username.apply(dispatch, () -> { return getUsername(); });
      DispatchConfig.Password.apply(dispatch, () -> { return pw; });
      dispatch = configureTimeouts(dispatch);
    }

    @Override
    public Dispatch<Source> build(AdaptrisMessage msg) throws Exception {
      return dispatch;
    }

  }

  private class PerMessageDispatcher implements DispatchBuilder {

    private Service service;

    private PerMessageDispatcher(Service s) throws Exception {
      service = s;
    }

    @Override
    public Dispatch<Source> build(AdaptrisMessage msg) throws Exception {
      final String pw = Password.decode(msg.resolve(ExternalResolver.resolve(getPassword())));
      Dispatch d = service.createDispatch(new QName(getNamespace(), getPortName()), Source.class, Service.Mode.PAYLOAD);
      DispatchConfig.SoapAction.apply(d, () -> { return msg.resolve(getSoapAction()); });
      DispatchConfig.EndpointAddress.apply(d, () -> { return msg.resolve(endpointAddress()); });
      DispatchConfig.Username.apply(d, () -> { return msg.resolve(getUsername()); });
      DispatchConfig.Password.apply(d, () -> { return pw; });
      return configureTimeouts(d);
    }
  }
}
