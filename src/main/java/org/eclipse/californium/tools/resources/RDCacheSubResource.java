package org.eclipse.californium.tools.resources;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 *
 * @author garayzuev@gmail.com
 */
public class RDCacheSubResource extends CoapResource {

  String payload = "";
  private int lifeTime = 86400;
  private ScheduledFuture<?> ltExpiryFuture;
  private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public RDCacheSubResource(String resourceIdentifier) {
    super(resourceIdentifier);
  }

  public RDCacheSubResource(String resourceIdentifier, int lt) {
    super(resourceIdentifier);
    if (ltExpiryFuture == null) {
      setLifeTime(lifeTime);
    }
    setObservable(true);
  }

  @Override
  public void handleGET(CoapExchange exchange) {
    Response resp = new Response(CoAP.ResponseCode.CONTENT);
    resp.setPayload(payload);
    resp.setOptions(new OptionSet().setContentFormat(Integer.valueOf(getAttributes().getContentTypes().get(0))));    
    exchange.respond(resp);
  }

  @Override
  public void handlePOST(CoapExchange exchange) {
    payload = exchange.getRequestText();
    if (ltExpiryFuture != null) {
      ltExpiryFuture.cancel(true); // try to cancel before delete is called
    }
    List<String> query = exchange.advanced().getRequest().getOptions().getUriQuery();
    for (String q : query) {

      KeyValuePair kvp = KeyValuePair.parse(q);

      if (LinkFormat.LIFE_TIME.equals(kvp.getName()) && !kvp.isFlag()) {
        lifeTime = kvp.getIntValue();
        if (lifeTime < 60) {
          lifeTime = 60;
        }
      }
    }
    setLifeTime(this.lifeTime);
    changed();
    exchange.respond(CoAP.ResponseCode.CHANGED);
  }

  @Override
  public void handleDELETE(CoapExchange exchange) {
    delete();
    exchange.respond(CoAP.ResponseCode.DELETED);
  }

  @Override
  public void delete() {

    if (ltExpiryFuture != null) {
      // delete may be called from within the future
      ltExpiryFuture.cancel(false);
    }

    super.delete();
  }

  public void setLifeTime(int newLifeTime) {

    lifeTime = newLifeTime;

    if (ltExpiryFuture != null) {
      ltExpiryFuture.cancel(true);
    }

    ltExpiryFuture = scheduler.schedule(new Runnable() {
      @Override
      public void run() {
        delete();
      }
    }, lifeTime + 2, // contingency time
            TimeUnit.SECONDS);

  }
}
