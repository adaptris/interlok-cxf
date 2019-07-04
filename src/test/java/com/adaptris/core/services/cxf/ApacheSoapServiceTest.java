package com.adaptris.core.services.cxf;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.ws.soap.SOAPFaultException;

import org.junit.Assert;
import org.w3c.dom.Document;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.ServiceCase;
import com.adaptris.core.ServiceException;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.util.TimeInterval;

public class ApacheSoapServiceTest extends ServiceCase {


  private static String FAULT_REQUEST = "<oxy:encounterError xmlns:oxy=\"http://ws.wst.adaptris.com/\"/>";
  private static String ECHO_REQUEST = "<oxy:performEcho xmlns:oxy=\"http://ws.wst.adaptris.com/\"><arg0>Hello World</arg0></oxy:performEcho>";

  public ApacheSoapServiceTest() {
    super();
  }

  public void testWsdlUrl() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getWsdlUrl());
    service.setWsdlUrl("http://localhost:8080/?wsdl");
    assertEquals("http://localhost:8080/?wsdl", service.getWsdlUrl());
  }

  public void testPortName() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getPortName());
    service.setPortName("Port");
    assertEquals("Port", service.getPortName());
  }

  public void testServiceName() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getServiceName());
    service.setServiceName("ServiceName");
    assertEquals("ServiceName", service.getServiceName());
  }

  public void testNamespace() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getNamespace());
    service.setNamespace("ns");
    assertEquals("ns", service.getNamespace());
  }

  public void testSoapAction() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getSoapAction());
    service.setSoapAction("stuff");
    assertEquals("stuff", service.getSoapAction());
  }

  @SuppressWarnings("deprecation")
  public void testWsdlPortUrl() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getWsdlPortUrl());
    assertNull(service.getEndpointAddress());
    service.setWsdlPortUrl("portUrl");
    assertNull(service.getEndpointAddress());
    assertEquals("portUrl", service.getWsdlPortUrl());
    assertEquals("portUrl", service.endpointAddress());
  }

  @SuppressWarnings("deprecation")
  public void testEndpointAddress() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getEndpointAddress());
    assertNull(service.getWsdlPortUrl());
    service.setEndpointAddress("endpointAddress");
    assertEquals("endpointAddress", service.getEndpointAddress());
    assertNull(service.getWsdlPortUrl());
    assertEquals("endpointAddress", service.endpointAddress());
  }

  public void testUsername() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getUsername());
    service.setUsername("user");
    assertEquals("user", service.getUsername());
  }

  public void testPassword() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getPassword());
    service.setPassword("pw");
    assertEquals("pw", service.getPassword());
  }

  public void testConnectTimeout() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    TimeInterval t = new TimeInterval(10L, TimeUnit.MILLISECONDS);
    assertNull(service.getConnectionTimeout());
    service.setConnectTimeout(t);
    assertEquals(t, service.getConnectionTimeout());
  }

  public void testRequestTimeout() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    TimeInterval t = new TimeInterval(10L, TimeUnit.MILLISECONDS);
    assertNull(service.getRequestTimeout());
    service.setRequestTimeout(t);
    assertEquals(t, service.getRequestTimeout());
  }

  public void testGenerateFault() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(FAULT_REQUEST);
    try {
      ServiceCase.execute(create(), msg);
      Assert.fail("Should have received a SOAPFault");
    } catch (ServiceException e) {
      Assert.assertTrue(e.getCause() instanceof SOAPFaultException);
      Assert.assertTrue(e.getCause().getMessage().contains("We at Adaptris do no support this operation"));
    }
  }
  
  public void testInvokeEchoService() throws Exception {
    ApacheSoapService service = LifecycleHelper.initAndStart(create());
    try {
      AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST);
      service.doService(AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST));
      service.doService(msg);
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
      String val = doc.getElementsByTagName("return").item(0).getTextContent();
      Assert.assertEquals("Echoing data: Hello World", val);
    } finally {
      LifecycleHelper.stopAndClose(service);
    }
  }

  public void testInvokeEchoServicePerMessage() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST);
    ApacheSoapService service = create();
    try {
      service.setPerMessageDispatch(true);
      service.setUseFallbackTransformer(true);
      LifecycleHelper.initAndStart(service);
      service.doService(AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST));
      service.doService(msg);
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
      String val = doc.getElementsByTagName("return").item(0).getTextContent();
      Assert.assertEquals("Echoing data: Hello World", val);
    } finally {
      LifecycleHelper.stopAndClose(service);
    }
  }

  private ApacheSoapService create() {
    ApacheSoapService service = new ApacheSoapService();
    service.setWsdlUrl("http://testbed.adaptris.net/web-service-test/webservicetestrpc?wsdl");
    service.setNamespace("http://ws.wst.adaptris.com/");
    service.setServiceName("WebServiceMockRPCService");
    service.setPortName("WebServiceMockRPCPort");
    service.setEnableDebug(true);
    service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    return service;
  }

  @Override
  protected ApacheSoapService retrieveObjectForSampleConfig() {
    ApacheSoapService service = new ApacheSoapService();
    service.setWsdlUrl("http://your.wsdl.url?wsdl");
    service.setNamespace("http://your.name.space/");
    service.setServiceName("service name");    
    service.setPortName("service port");
    // service.setConnectTimeout(new TimeInterval(10L, "SECONDS"));
    // service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    return service;
  }
}
