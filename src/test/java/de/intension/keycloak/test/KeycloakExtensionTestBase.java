package de.intension.keycloak.test;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
public class KeycloakExtensionTestBase
{

    protected static final String         TEST_REALM = "test-realm";
    protected static KeycloakDevContainer keycloak;

    protected static Keycloak             keycloakClient;

    protected ObjectMapper                mapper     = new ObjectMapper();

    @BeforeAll
    @SuppressWarnings("resource")
    public static void beforeClass()
    {
        keycloak = new KeycloakDevContainer().withExtension("keycloak-test-container");
        keycloak.withReuse(true);
        keycloak.withExposedPorts(8080, 8787);
        keycloak.withFixedExposedPort(8787, 8787);
        keycloak.withClassFolderChangeTrackingEnabled(true);
        keycloak.start();

        keycloakClient = Keycloak.getInstance(keycloak.getAuthServerUrl(), "master", keycloak.getAdminUsername(), keycloak.getAdminPassword(), "admin-cli");

        keycloakClient.realm(TEST_REALM);
    }

    protected String getAdminToken(String email)
        throws Exception
    {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "password");
        params.put("client_id", "admin-cli");
        params.put("username", email);
        params.put("password", "test");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + keycloak.getHttpPort() + "/auth/realms/" + TEST_REALM + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(buildFormDataFromMap(params))
            .build();
        HttpResponse<String> response = client.send(request,
                                                    HttpResponse.BodyHandlers.ofString());
        return retrieveToken(response.body());
    }

    protected String getUserWithoutRoleToken()
        throws Exception
    {
        return getAdminToken("justuser@sprylab.de");
    }

    protected String getUserWithRoleToken()
        throws Exception
    {
        return getAdminToken("tim@sprylab.de");
    }

    private HttpRequest.BodyPublisher buildFormDataFromMap(Map<String, String> params)
    {
        var builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

    protected HttpResponse<String> post(String url, String body, String token)
        throws Exception
    {
        HttpClient javaClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return javaClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> delete(String url, String body, String token)
        throws Exception
    {
        HttpClient javaClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
            .build();
        return javaClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String url, String token)
        throws Exception
    {
        HttpClient javaClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .GET()
            .build();
        return javaClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected UserRepresentation getUserByEmail(String email)
        throws Exception
    {
        HttpResponse<String> userResponse = get("http://localhost:" + keycloak.getHttpPort() + "/auth/admin/realms/" + TEST_REALM + "/users?email=" + email
                + "&exact=true",
                                                getUserWithRoleToken());
        List<UserRepresentation> users = mapper.readValue(userResponse.body(), new TypeReference<List<UserRepresentation>>() {});
        assertThat(users, hasSize(1));
        return users.get(0);
    }

    private String retrieveToken(String responseBody)
        throws Exception
    {
        return mapper.readValue(responseBody, AccessTokenResponse.class).getToken();
    }
}
