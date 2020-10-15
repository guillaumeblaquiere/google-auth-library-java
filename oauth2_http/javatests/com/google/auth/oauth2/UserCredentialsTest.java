/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.auth.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.GenericJson;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.util.Clock;
import com.google.auth.TestUtils;
import com.google.auth.http.AuthHttpConstants;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentialsTest.MockHttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentialsTest.MockTokenServerTransportFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link UserCredentials}. */
@RunWith(JUnit4.class)
public class UserCredentialsTest extends BaseSerializationTest {

  private static final String CLIENT_SECRET = "jakuaL9YyieakhECKL2SwZcu";
  private static final String CLIENT_ID = "ya29.1.AADtN_UtlxN3PuGAxrN2XQnZTVRvDyVWnYq4I6dws";
  private static final String REFRESH_TOKEN = "1/Tl6awhpFjkMkSJoj1xsli0H2eL5YsMgU_NKPY2TyGWY";
  private static final String ACCESS_TOKEN = "1/MkSJoj1xsli0AccessToken_NKPY2";
  private static final String QUOTA_PROJECT = "sample-quota-project-id";
  private static final String SERVICE_ACCOUNT_EMAIL = "my-service-account@my-project.iam.gserviceaccount.com";
  private static final Collection<String> SCOPES = Collections.singletonList("dummy.scope");
  private static final URI CALL_URI = URI.create("http://googleapis.com/testapi/v1/foo");

  @Test(expected = IllegalStateException.class)
  public void constructor_accessAndRefreshTokenNull_throws() {
    UserCredentials.newBuilder().setClientId(CLIENT_ID).setClientSecret(CLIENT_SECRET).build();
  }

  @Test
  public void constructor() {
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setQuotaProjectId(QUOTA_PROJECT)
            .build();
    assertEquals(CLIENT_ID, credentials.getClientId());
    assertEquals(CLIENT_SECRET, credentials.getClientSecret());
    assertEquals(REFRESH_TOKEN, credentials.getRefreshToken());
    assertEquals(QUOTA_PROJECT, credentials.getQuotaProjectId());
  }

  @Test
  public void createScoped_same() {
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .build();
    assertSame(userCredentials, userCredentials.createScoped(SCOPES));
  }

  @Test
  public void createScopedRequired_false() {
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .build();
    assertFalse(userCredentials.createScopedRequired());
  }

  @Test
  public void fromJson_hasAccessToken() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transportFactory.transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);
    GenericJson json = writeUserJson(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, null);

    GoogleCredentials credentials = UserCredentials.fromJson(json, transportFactory);

    Map<String, List<String>> metadata = credentials.getRequestMetadata(CALL_URI);
    TestUtils.assertContainsBearerToken(metadata, ACCESS_TOKEN);
  }

  @Test
  public void fromJson_hasQuotaProjectId() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transportFactory.transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);
    GenericJson json = writeUserJson(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, QUOTA_PROJECT);

    GoogleCredentials credentials = UserCredentials.fromJson(json, transportFactory);

    Map<String, List<String>> metadata = credentials.getRequestMetadata(CALL_URI);
    assertTrue(metadata.containsKey(GoogleCredentials.QUOTA_PROJECT_ID_HEADER_KEY));
    assertEquals(
        metadata.get(GoogleCredentials.QUOTA_PROJECT_ID_HEADER_KEY),
        Collections.singletonList(QUOTA_PROJECT));
  }

  @Test
  public void getRequestMetadata_initialToken_hasAccessToken() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .build();

    Map<String, List<String>> metadata = userCredentials.getRequestMetadata(CALL_URI);

    TestUtils.assertContainsBearerToken(metadata, ACCESS_TOKEN);
  }

  @Test
  public void getRequestMetadata_initialTokenRefreshed_throws() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .build();

    try {
      userCredentials.refresh();
      fail("Should not be able to refresh without refresh token.");
    } catch (IllegalStateException expected) {
      // Expected
    }
  }

  @Test
  public void getRequestMetadata_fromRefreshToken_hasAccessToken() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transportFactory.transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setHttpTransportFactory(transportFactory)
            .build();

    Map<String, List<String>> metadata = userCredentials.getRequestMetadata(CALL_URI);

    TestUtils.assertContainsBearerToken(metadata, ACCESS_TOKEN);
  }

  @Test
  public void getRequestMetadata_customTokenServer_hasAccessToken() throws IOException {
    final URI TOKEN_SERVER = URI.create("https://foo.com/bar");
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transportFactory.transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);
    transportFactory.transport.setTokenServerUri(TOKEN_SERVER);
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(TOKEN_SERVER)
            .build();

    Map<String, List<String>> metadata = userCredentials.getRequestMetadata(CALL_URI);

    TestUtils.assertContainsBearerToken(metadata, ACCESS_TOKEN);
  }

  @Test
  public void equals_true() throws IOException {
    final URI tokenServer = URI.create("https://foo.com/bar");
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(tokenServer)
            .setQuotaProjectId(QUOTA_PROJECT)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(tokenServer)
            .setQuotaProjectId(QUOTA_PROJECT)
            .build();
    assertTrue(credentials.equals(otherCredentials));
    assertTrue(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_clientId() throws IOException {
    final URI tokenServer1 = URI.create("https://foo1.com/bar");
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId("other client id")
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_clientSecret() throws IOException {
    final URI tokenServer1 = URI.create("https://foo1.com/bar");
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret("other client secret")
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_refreshToken() throws IOException {
    final URI tokenServer1 = URI.create("https://foo1.com/bar");
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    OAuth2Credentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    OAuth2Credentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken("otherRefreshToken")
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_accessToken() throws IOException {
    final URI tokenServer1 = URI.create("https://foo1.com/bar");
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    AccessToken otherAccessToken = new AccessToken("otherAccessToken", null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(otherAccessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_transportFactory() throws IOException {
    final URI tokenServer1 = URI.create("https://foo1.com/bar");
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    MockTokenServerTransportFactory serverTransportFactory = new MockTokenServerTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(serverTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_tokenServer() throws IOException {
    final URI tokenServer1 = URI.create("https://foo1.com/bar");
    final URI tokenServer2 = URI.create("https://foo2.com/bar");
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer1)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setTokenServerUri(tokenServer2)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void equals_false_quotaProjectId() throws IOException {
    final String quotaProject1 = "sample-id-1";
    final String quotaProject2 = "sample-id-2";
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    MockHttpTransportFactory httpTransportFactory = new MockHttpTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setQuotaProjectId(quotaProject1)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(httpTransportFactory)
            .setQuotaProjectId(quotaProject2)
            .build();
    assertFalse(credentials.equals(otherCredentials));
    assertFalse(otherCredentials.equals(credentials));
  }

  @Test
  public void toString_containsFields() throws IOException {
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    final URI tokenServer = URI.create("https://foo.com/bar");
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(tokenServer)
            .setQuotaProjectId(QUOTA_PROJECT)
            .build();

    String expectedToString =
        String.format(
            "UserCredentials{requestMetadata=%s, temporaryAccess=%s, clientId=%s, refreshToken=%s, "
                + "tokenServerUri=%s, transportFactoryClassName=%s, quotaProjectId=%s}",
            ImmutableMap.of(
                AuthHttpConstants.AUTHORIZATION,
                ImmutableList.of(OAuth2Utils.BEARER_PREFIX + accessToken.getTokenValue())),
            accessToken.toString(),
            CLIENT_ID,
            REFRESH_TOKEN,
            tokenServer,
            MockTokenServerTransportFactory.class.getName(),
            QUOTA_PROJECT);
    assertEquals(expectedToString, credentials.toString());
  }

  @Test
  public void hashCode_equals() throws IOException {
    final URI tokenServer = URI.create("https://foo.com/bar");
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(tokenServer)
            .setQuotaProjectId(QUOTA_PROJECT)
            .build();
    UserCredentials otherCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(tokenServer)
            .setQuotaProjectId(QUOTA_PROJECT)
            .build();
    assertEquals(credentials.hashCode(), otherCredentials.hashCode());
  }

  @Test
  public void serialize() throws IOException, ClassNotFoundException {
    final URI tokenServer = URI.create("https://foo.com/bar");
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    AccessToken accessToken = new AccessToken(ACCESS_TOKEN, null);
    UserCredentials credentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .setAccessToken(accessToken)
            .setHttpTransportFactory(transportFactory)
            .setTokenServerUri(tokenServer)
            .build();
    UserCredentials deserializedCredentials = serializeAndDeserialize(credentials);
    assertEquals(credentials, deserializedCredentials);
    assertEquals(credentials.hashCode(), deserializedCredentials.hashCode());
    assertEquals(credentials.toString(), deserializedCredentials.toString());
    assertSame(deserializedCredentials.clock, Clock.SYSTEM);
  }

  @Test
  public void fromStream_nullTransport_throws() throws IOException {
    InputStream stream = new ByteArrayInputStream("foo".getBytes());
    try {
      UserCredentials.fromStream(stream, null);
      fail("Should throw if HttpTransportFactory is null");
    } catch (NullPointerException expected) {
      // Expected
    }
  }

  @Test
  public void fromStream_nullStream_throws() throws IOException {
    MockHttpTransportFactory transportFactory = new MockHttpTransportFactory();
    try {
      UserCredentials.fromStream(null, transportFactory);
      fail("Should throw if InputStream is null");
    } catch (NullPointerException expected) {
      // Expected
    }
  }

  @Test
  public void fromStream_user_providesToken() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    transportFactory.transport.addClient(CLIENT_ID, CLIENT_SECRET);
    transportFactory.transport.addRefreshToken(REFRESH_TOKEN, ACCESS_TOKEN);
    InputStream userStream =
        writeUserStream(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, QUOTA_PROJECT);

    UserCredentials credentials = UserCredentials.fromStream(userStream, transportFactory);

    assertNotNull(credentials);
    Map<String, List<String>> metadata = credentials.getRequestMetadata(CALL_URI);
    TestUtils.assertContainsBearerToken(metadata, ACCESS_TOKEN);
  }

  @Test
  public void fromStream_userNoClientId_throws() throws IOException {
    InputStream userStream = writeUserStream(null, CLIENT_SECRET, REFRESH_TOKEN, QUOTA_PROJECT);

    testFromStreamException(userStream, "client_id");
  }

  @Test
  public void fromStream_userNoClientSecret_throws() throws IOException {
    InputStream userStream = writeUserStream(CLIENT_ID, null, REFRESH_TOKEN, QUOTA_PROJECT);

    testFromStreamException(userStream, "client_secret");
  }

  @Test
  public void fromStream_userNoRefreshToken_throws() throws IOException {
    InputStream userStream = writeUserStream(CLIENT_ID, CLIENT_SECRET, null, QUOTA_PROJECT);

    testFromStreamException(userStream, "refresh_token");
  }

  @Test
  public void saveUserCredentials_saved_throws() throws IOException {
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .build();
    File file = File.createTempFile("GOOGLE_APPLICATION_CREDENTIALS", null, null);
    file.deleteOnExit();

    String filePath = file.getAbsolutePath();
    userCredentials.save(filePath);
  }

  @Test
  public void saveAndRestoreUserCredential_saveAndRestored_throws() throws IOException {
    UserCredentials userCredentials =
        UserCredentials.newBuilder()
            .setClientId(CLIENT_ID)
            .setClientSecret(CLIENT_SECRET)
            .setRefreshToken(REFRESH_TOKEN)
            .build();

    File file = File.createTempFile("GOOGLE_APPLICATION_CREDENTIALS", null, null);
    file.deleteOnExit();

    String filePath = file.getAbsolutePath();

    userCredentials.save(filePath);

    FileInputStream inputStream = new FileInputStream(new File(filePath));

    UserCredentials restoredCredentials = UserCredentials.fromStream(inputStream);

    assertEquals(userCredentials.getClientId(), restoredCredentials.getClientId());
    assertEquals(userCredentials.getClientSecret(), restoredCredentials.getClientSecret());
    assertEquals(userCredentials.getRefreshToken(), restoredCredentials.getRefreshToken());
  }

  @Test
  public void getComputeEngineDefaultServiceAccountEmail_correct() throws IOException {
    HttpTransportFactory transportFactory =
            new HttpTransportFactory() {
              @Override
              public HttpTransport create() {
                return new MockHttpTransport() {
                  @Override
                  public LowLevelHttpRequest buildRequest(String method, String url)
                          throws IOException {
                    return new MockLowLevelHttpRequest() {
                      @Override
                      public LowLevelHttpResponse execute() throws IOException {
                        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                        response.setStatusCode(200);
                        response.setContentType("application/json");
                        response.setContent("{\"projectNumber\":\"4226\"}");
                        return response;
                      }
                    };
                  }
                };
              }
            };

    UserCredentials userCredentials =
            UserCredentials.newBuilder()
                    .setClientId(CLIENT_ID)
                    .setClientSecret(CLIENT_SECRET)
                    .setRefreshToken(REFRESH_TOKEN)
                    .build();
    String saEmail = userCredentials.getComputeEngineDefaultServiceAccountEmail(QUOTA_PROJECT, transportFactory.create().createRequestFactory());
    assertEquals("4226-compute@developer.gserviceaccount.com", saEmail);
  }

  @Test
  public void getServiceAccountEmail_throws() throws IOException {
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    UserCredentials userCredentials =
            UserCredentials.newBuilder()
                    .setClientId(CLIENT_ID)
                    .setClientSecret(CLIENT_SECRET)
                    .setRefreshToken(REFRESH_TOKEN)
                    .build();
    try {
      userCredentials.getServiceAccountEmail(transportFactory.create().createRequestFactory());
      fail("Should throw if quota project is null");
    } catch (IOException expected) {
      // Expected
    }
  }

  @Test
  public void getServiceAccountEmail_correct() throws IOException {
    HttpTransportFactory transportFactory =
            new HttpTransportFactory() {
              @Override
              public HttpTransport create() {
                return new MockHttpTransport() {
                  @Override
                  public LowLevelHttpRequest buildRequest(String method, String url)
                          throws IOException {
                    return new MockLowLevelHttpRequest() {
                      @Override
                      public LowLevelHttpResponse execute() throws IOException {
                        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                        response.setStatusCode(200);
                        response.setContentType("application/json");
                        response.setContent("{\"projectNumber\":\"4226\"}");
                        return response;
                      }
                    };
                  }
                };
              }
            };

    UserCredentials userCredentials =
            UserCredentials.newBuilder()
                    .setClientId(CLIENT_ID)
                    .setClientSecret(CLIENT_SECRET)
                    .setRefreshToken(REFRESH_TOKEN)
                    .setQuotaProjectId(QUOTA_PROJECT)
                    .build();
    String saEmail = userCredentials.getServiceAccountEmail(transportFactory.create().createRequestFactory());
    assertEquals("4226-compute@developer.gserviceaccount.com", saEmail);
  }

  @Test
  public void getServiceAccountEmail_envVar_correct() throws IOException {
    TestUserCredentials userCredentials = new TestUserCredentials();

    userCredentials.getVariables().put("SERVICE_ACCOUNT_APPLICATION_CREDENTIALS",SERVICE_ACCOUNT_EMAIL);

    String saEmail = userCredentials.getServiceAccountEmail(null);
    assertEquals(SERVICE_ACCOUNT_EMAIL, saEmail);
  }


  static GenericJson writeUserJson(
      String clientId, String clientSecret, String refreshToken, String quotaProjectId) {
    GenericJson json = new GenericJson();
    if (clientId != null) {
      json.put("client_id", clientId);
    }
    if (clientSecret != null) {
      json.put("client_secret", clientSecret);
    }
    if (refreshToken != null) {
      json.put("refresh_token", refreshToken);
    }
    if (quotaProjectId != null) {
      json.put("quota_project_id", quotaProjectId);
    }
    json.put("type", GoogleCredentials.USER_FILE_TYPE);
    return json;
  }

  static InputStream writeUserStream(
      String clientId, String clientSecret, String refreshToken, String quotaProjectId)
      throws IOException {
    GenericJson json = writeUserJson(clientId, clientSecret, refreshToken, quotaProjectId);
    return TestUtils.jsonToInputStream(json);
  }

  private static void testFromStreamException(InputStream stream, String expectedMessageContent) {
    try {
      UserCredentials.fromStream(stream);
      fail(
          String.format(
              "Should throw exception with message containing '%s'", expectedMessageContent));
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains(expectedMessageContent));
    }
  }

  private static class TestUserCredentials extends UserCredentials {

    private final Map<String, String> variables = new HashMap<>();

    public TestUserCredentials() {
    }

    public Map<String, String> getVariables() {
      return variables;
    }

    String getEnv(String key){
      return variables.get(key);
    }

  }

/*
  @Test
  public void idTokenWithAudience_correct() throws IOException {
    String accessToken1 = "1/MkSJoj1xsli0AccessToken_NKPY2";
    MockTokenServerTransportFactory transportFactory = new MockTokenServerTransportFactory();
    MockTokenServerTransport transport = transportFactory.transport;
    ServiceAccountCredentials credentials =
            ServiceAccountCredentials.fromPkcs8(
                    CLIENT_ID,
                    CLIENT_EMAIL,
                    PRIVATE_KEY_PKCS8,
                    PRIVATE_KEY_ID,
                    SCOPES,
                    transportFactory,
                    null);

    transport.addServiceAccount(CLIENT_EMAIL, accessToken1);
    TestUtils.assertContainsBearerToken(credentials.getRequestMetadata(CALL_URI), accessToken1);

    String targetAudience = "https://foo.bar";
    IdTokenCredentials tokenCredential =
            IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(credentials)
                    .setTargetAudience(targetAudience)
                    .build();
    tokenCredential.refresh();
    assertEquals(DEFAULT_ID_TOKEN, tokenCredential.getAccessToken().getTokenValue());
    assertEquals(DEFAULT_ID_TOKEN, tokenCredential.getIdToken().getTokenValue());
    assertEquals(
            targetAudience,
            (String) tokenCredential.getIdToken().getJsonWebSignature().getPayload().getAudience());
  }
*/



  /*
    @Override
  public IdToken idTokenWithAudience(String targetAudience, List<Option> options) throws IOException {

    if (targetAudience == null || targetAudience.equals("")) {
      throw new IOException("TargetAudience can't be null or empty");
    }

    HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory(new HttpCredentialsAdapter(this));

    //First time, check if the instance attribute has been initialized or not
    if (serviceAccountCredentialEmail == null){
      serviceAccountCredentialEmail = getServiceAccountEmail(requestFactory);
    }

    GenericData requestBody = new GenericData();
    requestBody.put("audience",targetAudience);
    requestBody.put("includeEmail", true);
    JsonHttpContent jsonRequestBody = new JsonHttpContent(JSON_FACTORY,requestBody);

    HttpRequest request = requestFactory.buildPostRequest(
            new GenericUrl(SERVICE_ACCOUNT_CREDENTIALS_API +
                    serviceAccountCredentialEmail+":generateIdToken"),jsonRequestBody);
    request.setParser(new JsonObjectParser(JSON_FACTORY));
    HttpResponse httpResponse = request.execute();
    GenericData responseData = httpResponse.parseAs(GenericData.class);

    return IdToken.create(responseData.get("token").toString());
  }

  private String getServiceAccountEmail(HttpRequestFactory requestFactory) throws IOException {
    LOGGER.warning("ID Token generation with audience and based on user credential is not possible. \n" +
            "A service account must be used. You can define it in the environment variable\n" +
            SERVICE_ACCOUNT_APPLICATION_CREDENTIALS + "\nIf not set, default Compute Engine default " +
            "service account will be used\nYou need to have the role 'Service Account Token Creator' " +
            "on the service account.");

    // Get the service account in the Environment Variables
    String saEmail = System.getenv(SERVICE_ACCOUNT_APPLICATION_CREDENTIALS);

    // If missing, use the compute engine default service account by default
    if (saEmail == null || saEmail.equals("")){
      // If quotaProjectId is null, you can't determine the current project
      if (quotaProjectId == null){
        throw new IOException(
                "QuotaProjectId can't be null to determine the default service account to use\n" +
                        "Use 'gcloud auth application-default set-quota-project' to set it");
      }

      // Inform the user that no defined service account is found. Use the Compute Engine Default service account
      saEmail = getComputeEngineDefaultServiceAccountEmail(getQuotaProjectId(),requestFactory);
    }
    LOGGER.info("The service account with email '" + saEmail + "' is used");
    return saEmail;
  }


    private String getComputeEngineDefaultServiceAccountEmail(String projectId, HttpRequestFactory requestFactory) throws IOException {
    String url = RESOURCE_MANAGER_API + "projects/" + projectId;

    HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(url));
    request.setParser(new JsonObjectParser(JSON_FACTORY));
    HttpResponse httpResponse = request.execute();
    GenericData responseData = httpResponse.parseAs(GenericData.class);

    return responseData.get("projectNumber").toString() + DEFAULT_COMPUTE_ENGINE_SERVICE_ACCOUNT_SUFFIX;
  }

   */
}
