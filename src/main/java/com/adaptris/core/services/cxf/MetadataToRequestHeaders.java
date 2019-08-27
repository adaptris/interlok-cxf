package com.adaptris.core.services.cxf;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.ws.Dispatch;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.MessageContext;

import com.adaptris.core.MetadataElement;

/**
 * Handler implementation that just adds key-value pairs to the context request headers.
 * 
 */
@FunctionalInterface
public interface MetadataToRequestHeaders extends Handler {


  @Override
  default boolean handleFault(MessageContext context) {
    return true;
  }

  @Override
  default void close(MessageContext context) {
    // Do nothing
  }

  static Map<String, List> getRequestHeaders(MessageContext context) {
    Map<String, List> headers = (Map<String, List>) context.get(MessageContext.HTTP_REQUEST_HEADERS);
    if (headers == null) {
      headers = new HashMap<>();
      context.put(MessageContext.HTTP_REQUEST_HEADERS, headers);
    }
    return headers;
  }

  static void register(Dispatch dispatch, MetadataToRequestHeaders handler) {
    List<Handler> handlers = dispatch.getBinding().getHandlerChain();
    if (handlers == null) {
      handlers = Arrays.asList(handler);
    } else {
      handlers.add(handler);
    }
    dispatch.getBinding().setHandlerChain(handlers);
  }

  static void register(final Collection<MetadataElement> metadata, final Dispatch dispatch) {
    register(dispatch,  (context) -> {
      try {
        Map<String, List> headers = getRequestHeaders(context);
        for (MetadataElement e: metadata) {
          headers.put(e.getKey(), Collections.singletonList(e.getValue()));
        }
      } catch (Exception ce) {
        return false;
      }
      return true;
    });
  }

}
