package com.adaptris.core.services.cxf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.ws.soap.SOAPFaultException;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.ServiceException;
import com.adaptris.core.metadata.RemoveAllMetadataFilter;
import com.adaptris.core.stubs.DefectiveMessageFactory;
import com.adaptris.core.stubs.DefectiveMessageFactory.WhenToBreak;
import com.adaptris.core.util.LifecycleHelper;
import com.adaptris.interlok.junit.scaffolding.services.ExampleServiceCase;
import com.adaptris.util.TimeInterval;

public class ApacheSoapServiceTest extends ExampleServiceCase {

  private static String INVERT_PAYLOAD =
      "<web:InvertStringCase xmlns:web=\"http://www.dataaccess.com/webservicesserver/\">\n"
          + "<web:sAString>hello world</web:sAString>\n"
          + "</web:InvertStringCase>";

  private static String CONVERT_PAYLOAD =
      "<CelsiusToFahrenheit xmlns=\"http://webservices.daehosting.com/temperature\">\n"
          + "  <nCelsius>0</nCelsius>\n"
          + "</CelsiusToFahrenheit>";

  private static String FAULT_REQUEST = "<oxy:encounterError xmlns:oxy=\"http://ws.wst.adaptris.com/\"/>";
  private static String ECHO_REQUEST = "<oxy:performEcho xmlns:oxy=\"http://ws.wst.adaptris.com/\"><arg0>Hello World</arg0></oxy:performEcho>";

  @Test
  public void testWsdlUrl() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getWsdlUrl());
    service.setWsdlUrl("http://localhost:8080/?wsdl");
    assertEquals("http://localhost:8080/?wsdl", service.getWsdlUrl());
  }

  @Test
  public void testPortName() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getPortName());
    service.setPortName("Port");
    assertEquals("Port", service.getPortName());
  }

  @Test
  public void testServiceName() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getServiceName());
    service.setServiceName("ServiceName");
    assertEquals("ServiceName", service.getServiceName());
  }

  @Test
  public void testNamespace() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getNamespace());
    service.setNamespace("ns");
    assertEquals("ns", service.getNamespace());
  }

  @Test
  public void testSoapAction() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getSoapAction());
    service.setSoapAction("stuff");
    assertEquals("stuff", service.getSoapAction());
  }

  @Test
  public void testEndpointAddress() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getEndpointAddress());
    service.setEndpointAddress("endpointAddress");
    assertEquals("endpointAddress", service.getEndpointAddress());
  }

  @Test
  public void testUsername() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getUsername());
    service.setUsername("user");
    assertEquals("user", service.getUsername());
  }

  @Test
  public void testPassword() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    assertNull(service.getPassword());
    service.setPassword("pw");
    assertEquals("pw", service.getPassword());
  }

  @Test
  public void testConnectTimeout() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    TimeInterval t = new TimeInterval(10L, TimeUnit.MILLISECONDS);
    assertNull(service.getConnectionTimeout());
    service.setConnectTimeout(t);
    assertEquals(t, service.getConnectionTimeout());
  }

  @Test
  public void testRequestTimeout() throws Exception {
    ApacheSoapService service = new ApacheSoapService();
    TimeInterval t = new TimeInterval(10L, TimeUnit.MILLISECONDS);
    assertNull(service.getRequestTimeout());
    service.setRequestTimeout(t);
    assertEquals(t, service.getRequestTimeout());
  }

  @Test
  public void testGenerateFault() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(FAULT_REQUEST);
    try {
      execute(create(), msg);
      Assert.fail("Should have received a SOAPFault");
    } catch (ServiceException e) {
      Assert.assertTrue(e.getCause() instanceof SOAPFaultException);
      Assert.assertTrue(e.getCause().getMessage().contains("We at Adaptris do no support this operation"));
    }
  }

  @Test
  public void testUseFallbackTransformer() {
    ApacheSoapService service = new ApacheSoapService();
    Assert.assertNull(service.getUseFallbackTransformer());
  }

  @Test
  public void testInvokeEchoService() throws Exception {
    ApacheSoapService service = create();
    try {
      service.setUseFallbackTransformer(false);
      LifecycleHelper.initAndStart(service);
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

  @Test
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

  @Ignore // http://www.dataaccess.com/webservicesserver is down
  @Test
  public void testDataAccess_InvertCase() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(INVERT_PAYLOAD);

    ApacheSoapService service = createDataAccessComWsdl();
    service.setUseFallbackTransformer(true);
    execute(service, msg);
    assertTrue(msg.getContent().contains("HELLO WORLD"));
  }

  @Ignore // http://www.dataaccess.com/webservicesserver is down
  @Test
  public void testDataAccess_InvertCase_PerMessage() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(INVERT_PAYLOAD);

    ApacheSoapService service = createDataAccessComWsdl();
    try {
      service.setUseFallbackTransformer(false);
      service.setMetadataFilter(new RemoveAllMetadataFilter());
      service.setPerMessageDispatch(true);
      LifecycleHelper.initAndStart(service);
      service.doService(AdaptrisMessageFactory.getDefaultInstance().newMessage(INVERT_PAYLOAD));
      service.doService(msg);
      assertTrue(msg.getContent().contains("HELLO WORLD"));
    } finally {
      LifecycleHelper.stopAndClose(service);
    }
  }

  @Test
  public void testDataAccess_Convert() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(CONVERT_PAYLOAD);

    ApacheSoapService service = createDaehostingComWsdl();
    service.setUseFallbackTransformer(true);
    execute(service, msg);
    assertTrue(msg.getContent().contains("32"));
  }

  @Test
  public void testDataAccess_Convert_PerMessage() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(CONVERT_PAYLOAD);

    ApacheSoapService service = createDaehostingComWsdl();
    try {
      service.setUseFallbackTransformer(false);
      service.setMetadataFilter(new RemoveAllMetadataFilter());
      service.setPerMessageDispatch(true);
      LifecycleHelper.initAndStart(service);
      service.doService(AdaptrisMessageFactory.getDefaultInstance().newMessage(CONVERT_PAYLOAD));
      service.doService(msg);
      assertTrue(msg.getContent().contains("32"));
    } finally {
      LifecycleHelper.stopAndClose(service);
    }
  }

  @Test(expected = ServiceException.class)
  public void testWithException() throws Exception {

    AdaptrisMessage msg = new DefectiveMessageFactory(WhenToBreak.INPUT).newMessage();
    ApacheSoapService service = createDaehostingComWsdl();
    try {
      service.setUseFallbackTransformer(true);
      LifecycleHelper.initAndStart(service);
      service.doService(msg);
    } finally {
      LifecycleHelper.stopAndClose(service);
    }
  }

  private ApacheSoapService createDataAccessComWsdl() {
    ApacheSoapService service = new ApacheSoapService();
    service.setWsdlUrl("https://www.dataaccess.com/webservicesserver/TextCasing.wso?WSDL");

    service.setNamespace("http://www.dataaccess.com/webservicesserver/");
    service.setServiceName("TextCasing");
    service.setPortName("TextCasingSoap");
    service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    return service;
  }

  private ApacheSoapService createDaehostingComWsdl() {
    ApacheSoapService service = new ApacheSoapService();
    service.setWsdlUrl("http://webservices.daehosting.com/services/TemperatureConversions.wso?WSDL");

    service.setNamespace("http://webservices.daehosting.com/temperature");
    service.setServiceName("TemperatureConversions");
    service.setPortName("TemperatureConversionsSoap");
    service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    return service;
  }

  private ApacheSoapService create() {
    Assume.assumeTrue(false);
    ApacheSoapService service = new ApacheSoapService();
    service.setWsdlUrl("http://testbed.adaptris.net/web-service-test/webservicetestrpc?wsdl");
    service.setNamespace("http://ws.wst.adaptris.com/");
    service.setServiceName("WebServiceMockRPCService");
    service.setPortName("WebServiceMockRPCPort");
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
