package com.adaptris.core.services.cxf;

import java.io.ByteArrayInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.BaseCase;
import com.adaptris.core.ServiceCase;
import com.adaptris.core.metadata.NoOpMetadataFilter;
import com.adaptris.util.TimeInterval;

public class SoapServiceTestbedDocumentTest extends BaseCase {

  private static String GET_PERSON_REQUEST = "<getPersonDetails xmlns=\"http://ws.wst.adaptris.com/\"><arg0 xmlns=\"\">Mickey</arg0><arg1 xmlns=\"\">Mouse</arg1></getPersonDetails>";

  public SoapServiceTestbedDocumentTest() {
  }
  
  @Test
  public void testInvokeGetPerson() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(GET_PERSON_REQUEST);
    ServiceCase.execute(create(), msg);
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
    String val = doc.getElementsByTagName("firstname").item(0).getTextContent();
    Assert.assertEquals("Mickey", val);
    val = doc.getElementsByTagName("surname").item(0).getTextContent();
    Assert.assertEquals("Mouse", val);
  }
  
  @Test
  public void testInvokeGetPerson_WithFilter() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(GET_PERSON_REQUEST);
    ApacheSoapService service = create();
    service.setMetadataFilter(new NoOpMetadataFilter());
    ServiceCase.execute(service, msg);
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(msg.getPayload()));
    String val = doc.getElementsByTagName("firstname").item(0).getTextContent();
    Assert.assertEquals("Mickey", val);
    val = doc.getElementsByTagName("surname").item(0).getTextContent();
    Assert.assertEquals("Mouse", val);
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
  public boolean isAnnotatedForJunit4() {
    return true;
  }
}
