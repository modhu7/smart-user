/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.smartitengineering.user.client.impl;

import com.smartitengineering.smartuser.client.api.User;
import com.smartitengineering.smartuser.client.api.UserFilter;
import com.smartitengineering.smartuser.client.api.UserResource;
import com.smartitengineering.smartuser.client.api.UsersResource;
import com.smartitengineering.util.rest.atom.AtomClientUtil;
import com.smartitengineering.util.rest.client.ClientUtil;
import com.smartitengineering.util.rest.client.ResourceLink;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;

/**
 *
 * @author russel
 */
class UsersResourceImpl extends AbstractClientImpl implements UsersResource{

  public static final String REL_USERS = "users";
  public static final String REL_ALT = "alternate";
  
  private ResourceLink usersLink;
  private URI usersURI;

  private boolean isCacheEnabled;
  private Date lastModifiedDate;
  private Date expirationDate;
  private List<Entry> entries;

  public UsersResourceImpl(ResourceLink usersLink) {

    this.usersLink = usersLink;

    usersURI = getBaseUriBuilder().path(usersLink.getUri().toString()).build();

    URI uri = getBaseUriBuilder().path(usersLink.getUri().toString()).build();
    ClientResponse response = ClientUtil.readClientResponse(uri, getHttpClient(), MediaType.APPLICATION_ATOM_XML);

    //PaginatedFeedEntitiesList<Organization> pgs;
    if (response.getStatus() == 200) {
      Feed feed = AtomClientUtil.getFeed(response);

      entries = feed.getEntries();

      if(response.getHeaders().getFirst("Cache-Control") != null)
        isCacheEnabled = response.getHeaders().getFirst("Cache-Control").equals("no-cache");
      String dateString = response.getHeaders().getFirst("Last-Modified");
      SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
      try {
        lastModifiedDate = format.parse(dateString);
      }
      catch (Exception ex) {
      }
      dateString = response.getHeaders().getFirst("Expires");
      try {
        lastModifiedDate = format.parse(dateString);
      }
      catch (Exception ex) {
      }
      uri = response.getLocation();

    }else{

      usersLink=null;

    }
  }

  @Override
  public List<UserResource> getUserResources() {
        List<UserResource> organizationResources = new ArrayList<UserResource>();

    //LinkedResource<OrganizationResource> linkedResource = new LinkedList<OrganizationResource>();

    for(Entry entry: entries){
      organizationResources.add( new UserResourceImpl(entry.getLink(REL_ALT)));
    }
    return organizationResources;
  }

//  @Override
//  public Collection<LinkedResource<UserResource>> getUserResources() {
//
//    List<UserResource> organizationResources = new ArrayList<UserResource>();
//
//    //LinkedResource<OrganizationResource> linkedResource = new LinkedList<OrganizationResource>();
//
//    for(Entry entry: entries){
//      organizationResources.add( new UserResourceImpl(entry.getLink(REL_ALT)));
//    }
//
//
//    return null;
//
//  }


  @Override
  public UserResource create(User user) {

    WebResource webResource = getClient().resource(usersURI);
    webResource.type(MediaType.APPLICATION_JSON).post(user);
    return new UserResourceImpl(user);
  }

  @Override
  public UsersResource search(UserFilter filter) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean isCacheEnabled() {
    
    return isCacheEnabled;
  }

  @Override
  public Date getLastModifiedDate() {
    
    return lastModifiedDate;
  }

  @Override
  public Date getExpirationDate() {
    
    return expirationDate;
  }

  @Override
  public String getUUID() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public URI getUri() {
    
    return usersURI;
  }

  @Override
  public Object refresh() {
    
    return new UsersResourceImpl(usersLink);
  }

}
