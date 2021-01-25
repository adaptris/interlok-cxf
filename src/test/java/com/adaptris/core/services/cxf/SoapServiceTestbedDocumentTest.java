package com.adaptris.core.services.cxf;

import java.io.ByteArrayInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import com.adaptris.core.AdaptrisMessage;
import com.adaptris.core.AdaptrisMessageFactory;
import com.adaptris.core.metadata.NoOpMetadataFilter;
import com.adaptris.interlok.junit.scaffolding.services.ExampleServiceCase;
import com.adaptris.util.TimeInterval;

public class SoapServiceTestbedDocumentTest {

  private static String GET_PERSON_REQUEST = "<getPersonDetails xmlns=\"http://ws.wst.adaptris.com/\"><arg0 xmlns=\"\">Mickey</arg0><arg1 xmlns=\"\">Mouse</arg1></getPersonDetails>";

  public SoapServiceTestbedDocumentTest() {
  }

  @Before
  public void setUp() throws Exception {
    // Skip testbed tests.
    Assume.assumeTrue(false);
  }

  @Test
  public void testInvokeGetPerson() throws Exception {
    AdaptrisMessage msg = AdaptrisMessageFactory.getDefaultInstance().newMessage(GET_PERSON_REQUEST);
    ExampleServiceCase.execute(create(), msg);
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
    ExampleServiceCase.execute(service, msg);
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
    service.setRequestTimeout(new TimeInterval(30L, "SECONDS"));
    return service;
  }
}
