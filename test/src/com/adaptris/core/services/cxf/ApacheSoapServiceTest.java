package com.adaptris.core.services.cxf;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.soap.SOAPFaultException;

import org.junit.Assert;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.CoreException;
import com.adaptris.core.ExampleConfigCase;
import com.adaptris.core.ServiceException;
import com.adaptris.core.services.cxf.ApacheSoapService;
import com.adaptris.util.TimeInterval;

public class ApacheSoapServiceTest extends ExampleConfigCase {


  private static String FAULT_REQUEST = "<oxy:encounterError xmlns:oxy=\"http://ws.wst.adaptris.com/\"/>";
  private static String ECHO_REQUEST = "<oxy:performEcho xmlns:oxy=\"http://ws.wst.adaptris.com/\"><arg0>Hello World</arg0></oxy:performEcho>";
  
  private ApacheSoapService service;
  
  public ApacheSoapServiceTest(String name) {
    super(name);
  }

  @Override
  public void setUp() throws CoreException {
    service = new ApacheSoapService();
    service.setWsdlUrl("http://testbed.adaptris.net/web-service-test/webservicetestrpc?wsdl");
    service.setNamespace("http://ws.wst.adaptris.com/");
    service.setServiceName("WebServiceMockRPCService");    
    service.setPortName("WebServiceMockRPCPort");
    service.setEnableDebug(true);
    service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    
    service.init();
    service.start();
  }
  
  @Override
  public void tearDown() {
    service.stop();
    service.close();
  }
  
  public void testGenerateFault() throws ServiceException {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(FAULT_REQUEST);
    try {
      service.doService(msg);
      Assert.fail("Should have received a SOAPFault");
    } catch (ServiceException e) {
      Assert.assertTrue(e.getCause() instanceof SOAPFaultException);
      Assert.assertTrue(e.getCause().getMessage().contains("We at Adaptris do no support this operation"));
    }
  }
  
  public void testInvokeEchoService() throws ServiceException, SAXException, IOException, ParserConfigurationException {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(ECHO_REQUEST);
    service.doService(msg);
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
    String val = doc.getElementsByTagName("return").item(0).getTextContent();
    Assert.assertEquals("Echoing data: Hello World", val);
  }  


  @Override
  protected String createExampleXml(Object object) throws Exception {
    String result = getExampleCommentHeader(object);

    result = result + configMarshaller.marshal(object);

    return result;
  }

  @Override
  protected Object retrieveObjectForSampleConfig() {
    ApacheSoapService service = new ApacheSoapService();
    service.setUniqueId("dummy Id");
    service.setWsdlUrl("http://your.wsdl.url?wsdl");
    service.setNamespace("http://your.name.space/");
    service.setServiceName("service name");    
    service.setPortName("service port");
    // service.setConnectTimeout(new TimeInterval(10L, "SECONDS"));
    // service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    return service;
  }

}
