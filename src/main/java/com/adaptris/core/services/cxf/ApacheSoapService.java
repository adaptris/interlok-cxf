package com.adaptris.core.services.cxf;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
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

import org.apache.commons.lang.BooleanUtils;
import org.apache.cxf.BusFactory;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.staxutils.StaxSource;
import org.hibernate.validator.constraints.NotBlank;

import com.adaptris.annotation.AdapterComponent;
import com.adaptris.annotation.AdvancedConfig;
import com.adaptris.annotation.ComponentProfile;
import com.adaptris.annotation.DisplayOrder;
import com.adaptris.annotation.InputFieldHint;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.ServiceImp;
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
@DisplayOrder(order = {"wsdlUrl", "portName", "serviceName", "soapAction", "wsdlPortUrl", "username", "password"})
public class ApacheSoapService extends ServiceImp {

  private static final TimeInterval DEFAULT_REQUEST_TIMEOUT = new TimeInterval(50l, TimeUnit.SECONDS);
  private static final TimeInterval DEFAULT_CONNECTION_TIMEOUT = new TimeInterval(10l, TimeUnit.SECONDS);

  // These are just here so we don't need to reference BindingProviderProperties or JAXWSProperties.
  private static final String COM_SUN_REQUEST_TIMEOUT = "com.sun.xml.internal.ws.request.timeout";
  private static final String COM_SUN_ALT_REQUEST_TIMEOUT = "com.sun.xml.ws.request.timeout";
  private static final String COM_SUN_CONNECT_TIMEOUT = "com.sun.xml.internal.ws.connect.timeout";
  private static final String COM_SUN_ALT_CONNECT_TIMEOUT = "com.sun.xml.internal.ws.request.timeout";

  @NotBlank
  private String wsdlUrl;
  @NotBlank
  private String portName;
  @NotBlank
  private String serviceName;
  @NotBlank
  private String namespace;
  @AdvancedConfig
  private String soapAction;
  @AdvancedConfig
  private String wsdlPortUrl;
  private String username;
  @InputFieldHint(style = "PASSWORD", external = true)
  private String password;
  @AdvancedConfig
  private TimeInterval connectionTimeout;
  @AdvancedConfig
  private TimeInterval requestTimeout;
  @AdvancedConfig
  private Boolean enableDebug;

  private transient Dispatch<Source> dispatch;
  private transient Transformer transformer;

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
  protected void initService() throws CoreException {
    URL wsdlURL;
    try {
      wsdlURL = new URL(getWsdlUrl());
    }
    catch (MalformedURLException e) {
      throw new CoreException("Failed to access WSDL URL [" + getWsdlUrl() + "]", e);
    }
    try {
      if (debugEnabled()) {
        BusFactory.getDefaultBus().getOutInterceptors().add(new LoggingOutInterceptor());
      }
      Service service = Service.create(wsdlURL, new QName(getNamespace(), getServiceName()));
      dispatch = service.createDispatch(new QName(getNamespace(), getPortName()), Source.class, Service.Mode.PAYLOAD);

      if (!isEmpty(getSoapAction())) {
        dispatch.getRequestContext().put(Dispatch.SOAPACTION_USE_PROPERTY, true);
        dispatch.getRequestContext().put(Dispatch.SOAPACTION_URI_PROPERTY, getSoapAction());
      }

      if (!isEmpty(getWsdlPortUrl())) {
        dispatch.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, getWsdlPortUrl());
      }

      if (!isEmpty(getUsername())) {
        dispatch.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, getUsername());
      }

      if (!isEmpty(getPassword())) {
        dispatch.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY,
            Password.decode(ExternalResolver.resolve(getPassword())));
      }
      dispatch.getRequestContext().put(COM_SUN_REQUEST_TIMEOUT, requestTimeout());
      dispatch.getRequestContext().put(COM_SUN_ALT_REQUEST_TIMEOUT, requestTimeout());
      dispatch.getRequestContext().put(COM_SUN_CONNECT_TIMEOUT, connectTimeout());
      dispatch.getRequestContext().put(COM_SUN_ALT_CONNECT_TIMEOUT, connectTimeout());
      transformer = TransformerFactory.newInstance().newTransformer();
    }
    catch (Exception e) {
      throw ExceptionHelper.wrapCoreException(e);
    }
  }

  @Override
  public void doService(AdaptrisMessage msg) throws ServiceException {
    try (InputStream in = msg.getInputStream(); OutputStream out = msg.getOutputStream()) {
      StaxSource source = new StaxSource(XMLInputFactory.newInstance().createXMLStreamReader(in));
      Source response = dispatch.invoke(source);
      transformer.transform(response, new StreamResult(out));
    }
    catch (Exception e) {
      throw new ServiceException("Failed to invoke service [" + getServiceName() + "] for operation [" + getPortName() + "]", e);
    }
  }

  @Override
  public void prepare() throws CoreException {}


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
   */
  public String getWsdlPortUrl() {
    return wsdlPortUrl;
  }

  /**
   * This optional property may be used to specify a different service address to that specified in the WSDL.
   * 
   * @param wsdlPortUrl
   */
  public void setWsdlPortUrl(String wsdlPortUrl) {
    this.wsdlPortUrl = wsdlPortUrl;
  }

  /**
   * Provide additional web service debugging information.
   * 
   * @return enableDebug true = provide web service debug information
   */
  public Boolean getEnableDebug() {
    return enableDebug;
  }

  /**
   * Whether to provide additional debugging information.
   * 
   * @param enableDebug true = provide web service debug information
   */
  public void setEnableDebug(Boolean enableDebug) {
    this.enableDebug = enableDebug;
  }

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
    return (int) (getConnectionTimeout() != null ? getConnectionTimeout().toMilliseconds() : DEFAULT_CONNECTION_TIMEOUT
        .toMilliseconds());
  }

  private int requestTimeout() {
    return (int) (getRequestTimeout() != null ? getRequestTimeout().toMilliseconds() : DEFAULT_REQUEST_TIMEOUT.toMilliseconds());
  }
}
