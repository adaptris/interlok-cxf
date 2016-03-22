package com.adaptris.core.services.cxf;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Assert;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.BaseCase;
import com.adaptris.core.CoreException;
import com.adaptris.core.ServiceException;
import com.adaptris.core.services.cxf.ApacheSoapService;
import com.adaptris.util.TimeInterval;

public class SoapServiceTestbedDocumentTest extends BaseCase {

  private static String GET_PERSON_REQUEST = "<getPersonDetails xmlns=\"http://ws.wst.adaptris.com/\"><arg0 xmlns=\"\">Mickey</arg0><arg1 xmlns=\"\">Mouse</arg1></getPersonDetails>";
  
  private ApacheSoapService service;
  
  public SoapServiceTestbedDocumentTest(String name) {
    super(name);
  }

  @Override
  public void setUp() throws CoreException {
    service = new ApacheSoapService();
    service.setWsdlUrl("http://testbed.adaptris.net/web-service-test/webservicetestdocument?wsdl");
    service.setNamespace("http://ws.wst.adaptris.com/");
    service.setServiceName("WebServiceMockDocumentService");    
    service.setPortName("WebServiceMockDocumentPort");
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
  
  public void testInvokeEchoService() throws ServiceException, SAXException, IOException, ParserConfigurationException {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(GET_PERSON_REQUEST);
    service.doService(msg);
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
    String val = doc.getElementsByTagName("firstname").item(0).getTextContent();
    Assert.assertEquals("Mickey", val);
    val = doc.getElementsByTagName("surname").item(0).getTextContent();
    Assert.assertEquals("Mouse", val);
  }
  
}
