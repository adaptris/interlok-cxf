package com.adaptris.core.services.cxf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.ws.Dispatch;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.MessageContext;

import org.apache.cxf.jaxws.DispatchImpl;
import org.apache.cxf.jaxws.binding.http.HTTPBindingImpl;
import org.junit.Test;
import org.mockito.Mockito;

import com.adaptris.core.MetadataElement;

public class MetadataToRequestHeadersTest {

  @Test
  public void testGetRequestHeaders() {
    MyMessageContext ctx = new MyMessageContext(false);
    Map<String, List> h1 = MetadataToRequestHeaders.getRequestHeaders(ctx);
    assertNotNull(h1);
    assertFalse(h1 == ctx.httpHeaders);
    MyMessageContext ctx2 = new MyMessageContext(true);
    Map<String, List> h2 = MetadataToRequestHeaders.getRequestHeaders(ctx2);
    assertNotNull(h2);
    assertTrue(h2 == ctx2.httpHeaders);
  }


  @Test
  public void testRegister_NoHandlerChain() {
    Dispatch dispatch = Mockito.mock(DispatchImpl.class);
    HTTPBindingImpl binding = Mockito.mock(HTTPBindingImpl.class);
    Mockito.when(dispatch.getBinding()).thenReturn(binding);
    Mockito.when(binding.getHandlerChain()).thenReturn(null);

    MetadataToRequestHeaders.register(dispatch, (context) -> true);

  }

  @Test
  public void testRegister_HandlerChain() {
    List<Handler> list = new ArrayList<>();
    Dispatch dispatch = Mockito.mock(DispatchImpl.class);
    HTTPBindingImpl binding = Mockito.mock(HTTPBindingImpl.class);
    Mockito.when(dispatch.getBinding()).thenReturn(binding);
    Mockito.when(binding.getHandlerChain()).thenReturn(list);

    MetadataToRequestHeaders.register(dispatch, (context) -> true);
    assertEquals(1, list.size());
  }

  @Test
  public void testHandleMessage() {
    List<Handler> list = new ArrayList<>();
    List<MetadataElement> elements = Arrays.asList(new MetadataElement("hello", "world"));

    Dispatch dispatch = Mockito.mock(DispatchImpl.class);
    HTTPBindingImpl binding = Mockito.mock(HTTPBindingImpl.class);
    Mockito.when(dispatch.getBinding()).thenReturn(binding);
    Mockito.when(binding.getHandlerChain()).thenReturn(list);

    MetadataToRequestHeaders.register(elements, dispatch);

    assertEquals(1, list.size());
    MyMessageContext ctx = new MyMessageContext(true);

    assertTrue(list.get(0).handleMessage(ctx));
    assertTrue(ctx.httpHeaders.containsKey("hello"));
  }

  @Test
  public void testHandleMessage_Failure() {
    List<Handler> list = new ArrayList<>();
    List<MetadataElement> elements = Arrays.asList(new BrokenMetadataElement("hello", "world"));

    Dispatch dispatch = Mockito.mock(DispatchImpl.class);
    HTTPBindingImpl binding = Mockito.mock(HTTPBindingImpl.class);
    Mockito.when(dispatch.getBinding()).thenReturn(binding);
    Mockito.when(binding.getHandlerChain()).thenReturn(list);

    MetadataToRequestHeaders.register(elements, dispatch);

    assertEquals(1, list.size());
    MyMessageContext ctx = new MyMessageContext(true);

    assertFalse(list.get(0).handleMessage(ctx));
  }

  @Test
  public void testHandleFault() {
    List<Handler> list = new ArrayList<>();
    List<MetadataElement> elements = Arrays.asList(new MetadataElement("hello", "world"));

    Dispatch dispatch = Mockito.mock(DispatchImpl.class);
    HTTPBindingImpl binding = Mockito.mock(HTTPBindingImpl.class);
    Mockito.when(dispatch.getBinding()).thenReturn(binding);
    Mockito.when(binding.getHandlerChain()).thenReturn(list);

    MetadataToRequestHeaders.register(elements, dispatch);

    assertEquals(1, list.size());
    MyMessageContext ctx = new MyMessageContext(true);

    assertTrue(list.get(0).handleFault(ctx));
  }


  @SuppressWarnings("serial")
  private class MyMessageContext extends HashMap<String, Object> implements MessageContext {

    private Map<String, Scope> scopes = new HashMap<>();
    private Map<String, List> httpHeaders = new HashMap<>();

    public MyMessageContext(boolean withRequestHdrs) {
      super();
      if (withRequestHdrs) {
        put(MessageContext.HTTP_REQUEST_HEADERS, httpHeaders);
      }
    }

    @Override
    public void setScope(String name, Scope scope) {
      scopes.put(name, scope);
    }

    @Override
    public Scope getScope(String name) {
      return scopes.get(name);
    }

  }

  @SuppressWarnings("serial")
  private class BrokenMetadataElement extends MetadataElement {
    public BrokenMetadataElement(String key, String value) {
      super(key, value);
    }

    @Override
    public String getValue() {
      throw new UnsupportedOperationException();
    }
  }
}
