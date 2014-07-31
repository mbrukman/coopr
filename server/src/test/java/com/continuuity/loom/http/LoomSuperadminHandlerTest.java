/*
 * Copyright 2012-2014, Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.continuuity.loom.http;

import com.continuuity.loom.Entities;
import com.continuuity.loom.admin.AutomatorType;
import com.continuuity.loom.admin.ProviderType;
import com.continuuity.loom.admin.Tenant;
import com.continuuity.loom.common.conf.Constants;
import com.continuuity.loom.provisioner.Provisioner;
import com.continuuity.loom.provisioner.TenantProvisionerService;
import com.continuuity.loom.store.provisioner.SQLProvisionerStore;
import com.continuuity.loom.store.tenant.SQLTenantStore;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

/**
 *
 */
public class LoomSuperadminHandlerTest extends LoomServiceTestBase {
  private static TenantProvisionerService tenantProvisionerService;

  @BeforeClass
  public static void setupTestClass() {
    tenantProvisionerService = injector.getInstance(TenantProvisionerService.class);
  }

  @Before
  public void setupSuperadminHandlerTest() throws SQLException, IOException {
    // base tests will write some tenants that we don't want.
    ((SQLTenantStore) tenantStore).clearData();
    ((SQLProvisionerStore) provisionerStore).clearData();
    tenantProvisionerService.writeProvisioner(
      new Provisioner("p1", "host", 12345, 100, ImmutableMap.<String, Integer>of(), ImmutableMap.<String, Integer>of())
    );
  }

  @Test
  public void testNonSuperadminReturnsError() throws Exception {
    assertResponseStatus(doGet("/v1/tenants", ADMIN_HEADERS), HttpResponseStatus.NOT_FOUND);
    assertResponseStatus(doGet("/v1/tenants/123", ADMIN_HEADERS), HttpResponseStatus.NOT_FOUND);
    assertResponseStatus(doPost("/v1/tenants", "", ADMIN_HEADERS), HttpResponseStatus.NOT_FOUND);
    assertResponseStatus(doPut("/v1/tenants/123", "", ADMIN_HEADERS), HttpResponseStatus.NOT_FOUND);
  }

  @Test
  public void testCreateTenant() throws Exception {
    Tenant requestedTenant = new Tenant("companyX", null, 10, 100, 1000);
    HttpResponse response = doPost("/v1/tenants", gson.toJson(requestedTenant), SUPERADMIN_HEADERS);

    // perform create request
    assertResponseStatus(response, HttpResponseStatus.OK);
    Reader reader = new InputStreamReader(response.getEntity().getContent());
    JsonObject responseObj = gson.fromJson(reader, JsonObject.class);
    String id = responseObj.get("id").getAsString();

    // make sure tenant was actually written
    Tenant tenant = tenantStore.getTenant(id);
    Assert.assertEquals(requestedTenant.getName(), tenant.getName());
    Assert.assertEquals(requestedTenant.getWorkers(), tenant.getWorkers());
    Assert.assertEquals(requestedTenant.getMaxClusters(), tenant.getMaxClusters());
    Assert.assertEquals(requestedTenant.getMaxNodes(), tenant.getMaxNodes());
  }

  @Test
  public void testCreateTenantWithTooManyWorkersReturnsConflict() throws Exception {
    Tenant requestedTenant = new Tenant("companyX", null, 10000, 100, 1000);
    HttpResponse response = doPost("/v1/tenants", gson.toJson(requestedTenant), SUPERADMIN_HEADERS);

    // perform create request
    assertResponseStatus(response, HttpResponseStatus.CONFLICT);
  }

  @Test
  public void testWriteTenant() throws Exception {
    // write tenant to store
    String id = UUID.randomUUID().toString();
    Tenant actualTenant = new Tenant("companyX", id, 10, 100, 1000);
    tenantStore.writeTenant(actualTenant);

    // perform request to write tenant
    Tenant updatedTenant = new Tenant("companyX", id, 10, 100, 500);
    HttpResponse response = doPut("/v1/tenants/" + id, gson.toJson(updatedTenant), SUPERADMIN_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);

    Assert.assertEquals(updatedTenant, tenantStore.getTenant(updatedTenant.getId()));
  }

  @Test
  public void testWriteTenantWithTooManyWorkersReturnsConflict() throws Exception {
    // write tenant to store
    String id = UUID.randomUUID().toString();
    Tenant actualTenant = new Tenant("companyX", id, 10, 100, 1000);
    tenantStore.writeTenant(actualTenant);

    // perform request to write tenant
    Tenant updatedTenant = new Tenant("companyX", id, 100000, 100, 500);
    HttpResponse response = doPut("/v1/tenants/" + id, gson.toJson(updatedTenant), SUPERADMIN_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.CONFLICT);
  }

  @Test
  public void testDeleteTenant() throws Exception {
    String id = UUID.randomUUID().toString();
    tenantProvisionerService.writeTenant(new Tenant("companyX", id, 0, 100, 1000));

    // perform request to delete tenant
    HttpResponse response = doDelete("/v1/tenants/" + id, SUPERADMIN_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);

    Assert.assertNull(tenantStore.getTenant(id));
  }

  @Test
  public void testDeleteTenantWithNonzeroWorkersFails() throws Exception {
    String id = UUID.randomUUID().toString();
    tenantProvisionerService.writeTenant(new Tenant("companyX", id, 10, 100, 1000));
    tenantProvisionerService.rebalanceTenantWorkers(id);

    // perform request to delete tenant
    assertResponseStatus(doDelete("/v1/tenants/" + id, SUPERADMIN_HEADERS), HttpResponseStatus.CONFLICT);
  }

  @Test
  public void testGetTenant() throws Exception {
    // write tenant to store
    String id = UUID.randomUUID().toString();
    Tenant actualTenant = new Tenant("companyX", id, 10, 100, 1000);
    tenantStore.writeTenant(actualTenant);

    // perform request to get tenant
    HttpResponse response = doGet("/v1/tenants/" + id, SUPERADMIN_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    Reader reader = new InputStreamReader(response.getEntity().getContent());
    Assert.assertEquals(actualTenant, gson.fromJson(reader, Tenant.class));
  }

  @Test
  public void testGetAllTenants() throws Exception {
    // write tenants to store
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    Tenant expectedTenant1 = new Tenant("companyX", id1, 10, 100, 1000);
    Tenant expectedTenant2 = new Tenant("companyY", id2, 500, 1000, 10000);
    tenantStore.writeTenant(expectedTenant1);
    tenantStore.writeTenant(expectedTenant2);

    // perform request to get tenant
    HttpResponse response = doGet("/v1/tenants", SUPERADMIN_HEADERS);
    assertResponseStatus(response, HttpResponseStatus.OK);
    Reader reader = new InputStreamReader(response.getEntity().getContent());
    Set<Tenant> actualTenants = gson.fromJson(reader, new TypeToken<Set<Tenant>>() {}.getType());
    Assert.assertEquals(ImmutableSet.of(Tenant.DEFAULT_SUPERADMIN, expectedTenant1, expectedTenant2), actualTenants);
  }

  @Test
  public void testBadRequestReturns400() throws Exception {
    // test malformed requests
    assertResponseStatus(doPost("/v1/tenants", "{}", SUPERADMIN_HEADERS), HttpResponseStatus.BAD_REQUEST);
    assertResponseStatus(doPost("/v1/tenants", "", SUPERADMIN_HEADERS), HttpResponseStatus.BAD_REQUEST);

    // id in object does not match id in path
    Tenant tenant = new Tenant("name", "id123", 10, 10, 10);
    assertResponseStatus(doPut("/v1/tenants/10", gson.toJson(tenant), SUPERADMIN_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);

    // missing id in object
    tenant = new Tenant("name", null, 10, 10, 10);
    assertResponseStatus(doPut("/v1/tenants/10", gson.toJson(tenant), SUPERADMIN_HEADERS),
                         HttpResponseStatus.BAD_REQUEST);
  }

  @Test
  public void testMissingObjectReturn404() throws Exception {
    assertResponseStatus(doGet("/v1/tenants/123", SUPERADMIN_HEADERS), HttpResponseStatus.NOT_FOUND);
  }

  @Test
  public void testAddTenantWithDefaults() {
    // TODO: implement once bootstrapping with defaults is implemented
  }

  @Test
  public void testProviderTypes() throws Exception {
    testNonPostRestAPIs("providertypes", Entities.ProviderTypeExample.JOYENT_JSON,
                        Entities.ProviderTypeExample.RACKSPACE_JSON, SUPERADMIN_HEADERS);
  }

  @Test
  public void testAutomatorTypes() throws Exception {
    testNonPostRestAPIs("automatortypes", Entities.AutomatorTypeExample.CHEF_JSON,
                        Entities.AutomatorTypeExample.SHELL_JSON, SUPERADMIN_HEADERS);
  }

  @Test
  public void testEditProviderTypesMustBeSuperadmin() throws Exception {
    tenantStore.writeTenant(new Tenant("tenant", ADMIN_ACCOUNT.getTenantId(), 500, 1000, 10000));
    ProviderType type = Entities.ProviderTypeExample.RACKSPACE;
    assertResponseStatus(doPut("/v1/loom/providertypes/" + type.getName(), gson.toJson(type), ADMIN_HEADERS),
                         HttpResponseStatus.FORBIDDEN);
    assertResponseStatus(doDelete("/v1/loom/providertypes/" + type.getName(), ADMIN_HEADERS),
                         HttpResponseStatus.FORBIDDEN);
  }

  @Test
  public void testEditAutomatorTypesMustBeSuperadmin() throws Exception {
    tenantStore.writeTenant(new Tenant("tenant", ADMIN_ACCOUNT.getTenantId(), 500, 1000, 10000));
    AutomatorType type = Entities.AutomatorTypeExample.CHEF;
    assertResponseStatus(doPut("/v1/loom/automatortypes/" + type.getName(), gson.toJson(type), ADMIN_HEADERS),
                         HttpResponseStatus.FORBIDDEN);
    assertResponseStatus(doDelete("/v1/loom/automatortypes/" + type.getName(), ADMIN_HEADERS),
                         HttpResponseStatus.FORBIDDEN);
  }

  private void testNonPostRestAPIs(String entityType, JsonObject entity1, JsonObject entity2,
                                   Header[] headers) throws Exception {
    String base = "/v1/loom/" + entityType;
    String entity1Path = base + "/" + entity1.get("name").getAsString();
    String entity2Path = base + "/" + entity2.get("name").getAsString();
    // should start off with no entities
    assertResponseStatus(doGet(entity1Path, headers), HttpResponseStatus.NOT_FOUND);

    // add entity through PUT
    assertResponseStatus(doPut(entity1Path, entity1.toString(), headers), HttpResponseStatus.OK);
    // check we can get it
    HttpResponse response = doGet(entity1Path, headers);
    assertResponseStatus(response, HttpResponseStatus.OK);
    Reader reader = new InputStreamReader(response.getEntity().getContent(), Charsets.UTF_8);
    JsonObject result = new Gson().fromJson(reader, JsonObject.class);
    Assert.assertEquals(entity1, result);

    // add second entity through PUT
    assertResponseStatus(doPut(entity2Path, entity2.toString(), headers), HttpResponseStatus.OK);
    // check we can get it
    response = doGet(entity2Path, headers);
    assertResponseStatus(response, HttpResponseStatus.OK);
    reader = new InputStreamReader(response.getEntity().getContent(), Charsets.UTF_8);
    result = new Gson().fromJson(reader, JsonObject.class);
    Assert.assertEquals(entity2, result);

    // get both entities
    response = doGet(base, headers);
    assertResponseStatus(response, HttpResponseStatus.OK);
    reader = new InputStreamReader(response.getEntity().getContent(), Charsets.UTF_8);
    JsonArray results = new Gson().fromJson(reader, JsonArray.class);

    Assert.assertEquals(2, results.size());
    JsonObject first = results.get(0).getAsJsonObject();
    JsonObject second = results.get(1).getAsJsonObject();
    if (first.get("name").getAsString().equals(entity1.get("name").getAsString())) {
      Assert.assertEquals(entity1, first);
      Assert.assertEquals(entity2, second);
    } else {
      Assert.assertEquals(entity2, first);
      Assert.assertEquals(entity1, second);
    }

    assertResponseStatus(doDelete(entity1Path, headers), HttpResponseStatus.OK);
    assertResponseStatus(doDelete(entity2Path, headers), HttpResponseStatus.OK);
    // check both were deleted
    assertResponseStatus(doGet(entity1Path, headers), HttpResponseStatus.NOT_FOUND);
    assertResponseStatus(doGet(entity2Path, headers), HttpResponseStatus.NOT_FOUND);
  }
}