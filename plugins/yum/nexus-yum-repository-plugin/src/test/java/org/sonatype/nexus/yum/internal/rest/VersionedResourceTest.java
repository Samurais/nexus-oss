/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2013 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.yum.internal.rest;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.repository.HostedRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.repository.RepositoryKind;
import org.sonatype.nexus.yum.Yum;
import org.sonatype.nexus.yum.YumRegistry;
import org.sonatype.nexus.yum.internal.support.YumNexusTestSupport;

import com.google.code.tempusfugit.temporal.Condition;
import com.noelios.restlet.http.HttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.StringRepresentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VersionedResourceTest
    extends YumNexusTestSupport
{

  private static final String VERSION = "2.2-1";

  private static final String TESTREPO = "conflict-artifact";

  private static final String ALIAS = "myAlias";

  @Inject
  private VersionedResource resource;

  @Inject
  private YumRegistry yumRegistry;

  private MavenRepository repository;

  @Before
  public void registerRepository()
      throws Exception
  {
    if (repository == null) {
      repository = createRepository(TESTREPO);
    }
    final Yum yum = yumRegistry.register(repository);
    yum.addAlias(ALIAS, VERSION);
    waitFor(new Condition()
    {
      @Override
      public boolean isSatisfied() {
        return yumRegistry.get(TESTREPO).getVersions().size() == 5;
      }
    });
  }

  @Test(expected = ResourceException.class)
  public void shouldThrowNotFound()
      throws Exception
  {
    resource.get(null, createRequest("/", "blabla", "80.02.2"), null, null);
  }

  @Test(expected = ResourceException.class)
  public void shouldThrowNotFoundForVersion()
      throws Exception
  {
    resource.get(null, createRequest("/", TESTREPO, "not-present"), null, null);
  }

  @Test
  public void shouldRedirectIndex()
      throws Exception
  {
    Request request = createRequest("", TESTREPO, VERSION);
    Response response = createResponse(request);
    resource.get(null, request, response, null);
    Assert.assertEquals(Status.REDIRECTION_PERMANENT, response.getStatus());
    Assert.assertEquals("http://localhost:8081/nexus/service/local/yum/repos/" + TESTREPO + "/" + VERSION + "/",
        response.getLocationRef().getIdentifier());
  }

  @Test
  public void shouldNotFindFilesOutsideTheRepository()
      throws Exception
  {
    Request request = createRequest("/any-rpm.rpm", TESTREPO, VERSION);
    FileRepresentation representation = (FileRepresentation) resource.get(null, request, null, null);
    Assert.assertFalse(representation.getFile().exists());
  }

  @Test(expected = ResourceException.class)
  public void shouldThrowExceptionForAccessToParentDirectory()
      throws Exception
  {
    Request request = createRequest("/../any-rpm.rpm", TESTREPO, VERSION);
    resource.get(null, request, null, null);
  }

  private void shouldGenerateDirectoryIndexForVersionAndRepo(final String version, final String repo)
      throws ResourceException
  {
    Request request = createRequest("/", repo, version);
    StringRepresentation representation = (StringRepresentation) resource.get(null, request, null, null);
    Assert.assertEquals(MediaType.TEXT_HTML, representation.getMediaType());
    Assert.assertTrue(representation.getText().contains("repodata/"));
  }

  private Response createResponse(Request request) {
    return new HttpResponse(null, request);
  }

  private Request createRequest(String urlSuffix, String repository, String version) {
    Request request =
        new Request(Method.GET, "http://localhost:8081/nexus/service/local/yum/repos/" + repository + "/"
            + version + urlSuffix);
    request.setAttributes(createAttributes(repository, version));
    return request;
  }

  private Map<String, Object> createAttributes(String repository, String version) {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("repository", repository);
    map.put("version", version);
    return map;
  }

  public MavenRepository createRepository(String id) {
    final MavenRepository repo = mock(MavenRepository.class);
    when(repo.getId()).thenReturn(id);
    when(repo.getLocalUrl()).thenReturn(rpmsDir().toURI().toASCIIString());
    when(repo.getProviderRole()).thenReturn(Repository.class.getName());
    when(repo.getProviderHint()).thenReturn("maven2");
    final RepositoryKind repositoryKind = mock(RepositoryKind.class);
    when(repo.getRepositoryKind()).thenReturn(repositoryKind);
    when(repositoryKind.isFacetAvailable(HostedRepository.class)).thenReturn(true);
    return repo;
  }
}
