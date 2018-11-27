package com.adaptris.core.services.cxf;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.ws.Dispatch;

import org.junit.Test;
import org.mockito.Mockito;

public class DispatchConfigTest {

  @Test
  public void testSoapAction() throws Exception {
    Map<String, Object> ctx = new HashMap<>();
    Dispatch<Source> mock = mock(ctx);
    ApacheSoapService.DispatchConfig.SoapAction.apply(mock, () -> {
      return "";
    });
    assertEquals(0, ctx.size());
    ApacheSoapService.DispatchConfig.SoapAction.apply(mock, () -> {
      return "soapAction";
    });
    assertEquals(2, ctx.size());
    assertEquals("soapAction", ctx.get(Dispatch.SOAPACTION_URI_PROPERTY));
    assertEquals(Boolean.TRUE, ctx.get(Dispatch.SOAPACTION_USE_PROPERTY));
  }

  @Test
  public void testEndpointAddress() throws Exception {
    Map<String, Object> ctx = new HashMap<>();
    Dispatch<Source> mock = mock(ctx);
    ApacheSoapService.DispatchConfig.EndpointAddress.apply(mock, () -> {
      return "";
    });
    assertEquals(0, ctx.size());
    ApacheSoapService.DispatchConfig.EndpointAddress.apply(mock, () -> {
      return "endpointAddress";
    });
    assertEquals(1, ctx.size());
    assertEquals("endpointAddress", ctx.get(Dispatch.ENDPOINT_ADDRESS_PROPERTY));
  }

  @Test
  public void testUsername() throws Exception {
    Map<String, Object> ctx = new HashMap<>();
    Dispatch<Source> mock = mock(ctx);
    ApacheSoapService.DispatchConfig.Username.apply(mock, () -> {
      return "";
    });
    assertEquals(0, ctx.size());
    ApacheSoapService.DispatchConfig.Username.apply(mock, () -> {
      return "username";
    });
    assertEquals(1, ctx.size());
    assertEquals("username", ctx.get(Dispatch.USERNAME_PROPERTY));
  }

  @Test
  public void testPassword() throws Exception {
    Map<String, Object> ctx = new HashMap<>();
    Dispatch<Source> mock = mock(ctx);
    ApacheSoapService.DispatchConfig.Password.apply(mock, () -> {
      return "";
    });
    assertEquals(0, ctx.size());
    ApacheSoapService.DispatchConfig.Password.apply(mock, () -> {
      return "password";
    });
    assertEquals(1, ctx.size());
    assertEquals("password", ctx.get(Dispatch.PASSWORD_PROPERTY));
  }

  private Dispatch<Source> mock(Map<String, Object> requestContext) {
    Dispatch<Source> dispatcher = Mockito.mock(Dispatch.class);
    Mockito.when(dispatcher.getRequestContext()).thenReturn(requestContext);
    return dispatcher;
  }
}
