package de.intension.keycloak.test;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeAll;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
public abstract class KeycloakDevTestBase
{

    protected static final String         TEST_REALM    = "test-realm";
    protected static KeycloakDevContainer keycloak;

    protected static Keycloak             keycloakClient;

    protected ObjectMapper                mapper        = new ObjectMapper();

    private static final String           BEARER_PREFIX = "Bearer ";

    @BeforeAll
    public static void beforeClass()
    {
        keycloak = new KeycloakDevContainer("keycloak-test-container");
        keycloak.withReuse(true);
        keycloak.withExposedPorts(8080, 8787);
        keycloak.withFixedExposedPort(8787, 8787);
        keycloak.withRealmImportFile(getRealmConfig());
        keycloak.withClassFolderChangeTrackingEnabled(true);
        keycloak.start();

        keycloakClient = Keycloak.getInstance(keycloak.getAuthServerUrl(), "master", keycloak.getAdminUsername(), keycloak.getAdminPassword(), "admin-cli");

        keycloakClient.realm(TEST_REALM);
    }

    protected static String getRealmConfig()
    {
        return "realm-export.json";
    }

    protected String getAdminToken(String email)
        throws IOException, InterruptedException
    {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "password");
        params.put("client_id", "admin-cli");
        params.put("username", email);
        params.put("password", "test");
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + keycloak.getHttpPort() + "/auth/realms/" + TEST_REALM + "/protocol/openid-connect/token"))
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .POST(buildFormDataFromMap(params))
            .build();
        HttpResponse<String> response = client.send(request,
                                                    HttpResponse.BodyHandlers.ofString());
        return retrieveToken(response.body());
    }

    private HttpRequest.BodyPublisher buildFormDataFromMap(Map<String, String> params)
    {
        var builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

    protected HttpResponse<String> post(String url, String body, String token)
        throws IOException, InterruptedException
    {
        HttpClient javaClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(AUTHORIZATION, BEARER_PREFIX + token)
            .header(CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return javaClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> delete(String url, String body, String token)
        throws IOException, InterruptedException
    {
        HttpClient javaClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(AUTHORIZATION, BEARER_PREFIX + token)
            .header(CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .method(HttpMethod.DELETE, HttpRequest.BodyPublishers.ofString(body))
            .build();
        return javaClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String url, String token)
        throws IOException, InterruptedException
    {
        HttpClient javaClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header(AUTHORIZATION, BEARER_PREFIX + token)
            .header(CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .GET()
            .build();
        return javaClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected UserRepresentation getUserByEmail(String email)
        throws IOException, InterruptedException
    {
        HttpResponse<String> userResponse = get("http://localhost:" + keycloak.getHttpPort() + "/auth/admin/realms/" + TEST_REALM + "/users?email=" + email
                + "&exact=true",
                                                getUserWithRoleToken());
        List<UserRepresentation> users = mapper.readValue(userResponse.body(), new TypeReference<List<UserRepresentation>>() {});
        assertThat(users, hasSize(1));
        return users.get(0);
    }

    protected abstract String getUserWithRoleToken();

    private String retrieveToken(String responseBody)
        throws IOException
    {
        return mapper.readValue(responseBody, AccessTokenResponse.class).getToken();
    }
}
