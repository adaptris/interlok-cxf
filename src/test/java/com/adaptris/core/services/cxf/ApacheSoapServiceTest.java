package com.adaptris.core.services.cxf;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.ws.soap.SOAPFaultException;

import org.junit.Assert;
import org.w3c.dom.Document;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.ServiceCase;
import com.adaptris.core.ServiceException;
import com.adaptris.util.TimeInterval;

public class ApacheSoapServiceTest extends ServiceCase {


  private static String FAULT_REQUEST = "<oxy:encounterError xmlns:oxy=\"http://ws.wst.adaptris.com/\"/>";
  private static String ECHO_REQUEST = "<oxy:performEcho xmlns:oxy=\"http://ws.wst.adaptris.com/\"><arg0>Hello World</arg0></oxy:performEcho>";

  public ApacheSoapServiceTest() {
    super();
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
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST);
    ServiceCase.execute(create(), msg);
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
    String val = doc.getElementsByTagName("return").item(0).getTextContent();
    Assert.assertEquals("Echoing data: Hello World", val);
  }  

  public void testInvokeEchoServicePerMessage() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST);
    ApacheSoapService service = create();
    service.setPerMessageDispatch(true);
    ServiceCase.execute(service, AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST));
    ServiceCase.execute(service, msg);
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
    String val = doc.getElementsByTagName("return").item(0).getTextContent();
    Assert.assertEquals("Echoing data: Hello World", val);
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
