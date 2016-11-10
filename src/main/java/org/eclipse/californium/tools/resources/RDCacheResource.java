package org.eclipse.californium.tools.resources;

import java.util.List;
import java.util.Scanner;
import java.util.Set;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

/**
 *
 * @author garayzuev@gmail.com
 */
public class RDCacheResource extends CoapResource {
  
  private final RDResource rdResource;
  
  public RDCacheResource(RDResource rdResource) {
    this("rd-cache", rdResource);
  }
  
  public RDCacheResource(String resourceIdentifier, RDResource rdResource) {
    super(resourceIdentifier);
    getAttributes().addResourceType("core.rd-cache");
    this.rdResource = rdResource;
  }
  
  @Override
  public void handleGET(CoapExchange exchange) {
    String payload = "";
    for (Resource res : getChildren()) {
      payload += "</" + res.getName() + ">,";
    }
    Response response = new Response(CoAP.ResponseCode.CONTENT);
    response.setPayload(payload.substring(0, payload.length() - 1));
    response.setOptions(new OptionSet().setContentFormat(MediaTypeRegistry.APPLICATION_LINK_FORMAT));
    exchange.respond(response);
  }
  
  @Override
  public void handlePOST(CoapExchange exchange) {

    // Create a RDNode
    String endpointName = "";
    String domain = "local";
    String endpointType;
    int lifeTime = 86400;
    RDNodeResource resource = null;
    CoAP.ResponseCode responseCode;
    
    List<String> query = exchange.getRequestOptions().getUriQuery();
    for (String q : query) {
      
      KeyValuePair kvp = KeyValuePair.parse(q);
      
      if (LinkFormat.END_POINT.equals(kvp.getName()) && !kvp.isFlag()) {
        endpointName = kvp.getValue();
      }
      
      if (LinkFormat.DOMAIN.equals(kvp.getName()) && !kvp.isFlag()) {
        domain = kvp.getValue();
      }
      if (LinkFormat.END_POINT_TYPE.equals(kvp.getName()) && !kvp.isFlag()) {
        endpointType = kvp.getValue();
      }
      
      if (LinkFormat.LIFE_TIME.equals(kvp.getName()) && !kvp.isFlag()) {
        lifeTime = kvp.getIntValue();
        if (lifeTime < 60) {
          lifeTime = 60;
        }
      }
    }
    // mandatory variables
    if (endpointName.isEmpty()) {
      exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
      return;
    }

    // find already registered EP
    for (Resource node : rdResource.getChildren()) {
      if (((RDNodeResource) node).getEndpointName().equals(endpointName) && ((RDNodeResource) node).getDomain().equals(domain)) {
        resource = (RDNodeResource) node;
      }
    }
    
    if (resource == null) {
      
      resource = new RDNodeResource(endpointName, domain);
      rdResource.add(resource);
      
      responseCode = CoAP.ResponseCode.CREATED;
    } else {
      responseCode = CoAP.ResponseCode.CHANGED;
    }
    //TODO: set context as "con=coap://${rd_uri}:${rd_port}" for relate SN resources and rd-cache resources
    //String context = LinkFormat.CONTEXT + "=";
    //exchange.advanced().getResponse().getSource();

    // set parameters of resource or abort on failure
    if (!resource.setParameters(exchange.advanced().getRequest())) {
      resource.delete();
      exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
      return;
    }
    // inform client about the location of the new resource
    exchange.setLocationPath(resource.getURI());
    exchange.respond(responseCode);

    //Create a RDCacheNode
    CoapResource node = new RDCacheSubResource(endpointName, lifeTime);
    updateEndpointResources(node, exchange.getRequestText(), endpointName, lifeTime);
    add(node);
  }
  
  public CoapResource addNodeResource(String path, CoapResource root, int lt) {
    Scanner scanner = new Scanner(path);
    scanner.useDelimiter("/");
    String next = "";
    boolean resourceExist = false;
    Resource resource = root; // It's the resource that represents the endpoint

    CoapResource subResource = null;
    while (scanner.hasNext()) {
      resourceExist = false;
      next = scanner.next();
      for (Resource res : resource.getChildren()) {
        if (res.getName().equals(next)) {
          subResource = (CoapResource) res;
          resourceExist = true;
        }
      }
      if (!resourceExist) {
        subResource = new RDCacheSubResource(next, lt);
        resource.add(subResource);
      }
      resource = subResource;
    }
    subResource.setPath(resource.getPath());
    subResource.setName(next);
    scanner.close();
    return subResource;
  }
  
  private boolean updateEndpointResources(CoapResource root, String linkFormat, String endpointName, int lt) {
    
    Set<WebLink> links = LinkFormat.parse(linkFormat);
    
    for (WebLink l : links) {
      CoapResource resource;
      if (!l.getURI().equals("/")) {
        resource = addNodeResource(l.getURI().substring(l.getURI().indexOf("/")), root, lt);
      } else {
        resource = root;
      }

      // clear attributes to make registration idempotent
      for (String attribute : resource.getAttributes().getAttributeKeySet()) {
        resource.getAttributes().clearAttribute(attribute);
      }

      // copy to resource list
      for (String attribute : l.getAttributes().getAttributeKeySet()) {
        for (String value : l.getAttributes().getAttributeValues(attribute)) {
          resource.getAttributes().addAttribute(attribute, value);
        }
      }
      
      resource.getAttributes().setAttribute(LinkFormat.END_POINT, endpointName);
    }
    
    return true;
  }
  
}
