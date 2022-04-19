package com.nts.keycloak.reproducer;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

public class Main {

    public static final int FAILED_LOGIN_THRESHOLD = 3;
    public static final int SUCCESSFUL_LOGIN_THRESHOLD = 15;

    public static final String ADMIN_USER = "admin";
    public static final String REALM_NAME = "Reproducer-9823";
    public static final String USER = "Tester";
    public static final String INIT_ROLE = "INIT_ROLE";
    public static final String CLIENT_NAME = "test-client";
    public static final String ADDITIONAL_ROLE = "ADDITIONAL_ROLE";


    public static void main(String[] args) {
        Client httpClient = ClientBuilder.newBuilder().build();
        Keycloak keycloak =
                KeycloakBuilder.builder().serverUrl("http://localhost:8180/auth").realm("master").username(ADMIN_USER).password(ADMIN_USER)
                        .clientId("admin-cli").build();

        keycloak.serverInfo().getInfo();

        UserSessionLoader sessionLoader = new UserSessionLoader(keycloak, REALM_NAME, CLIENT_NAME);
        RoleTweaker roleTweaker = new RoleTweaker(keycloak, REALM_NAME, USER, ADDITIONAL_ROLE);

        try {
            // Setup test data
            setupRealmUserAndRole(keycloak);

            // Start periodically querying the sessions in the background
            System.out.println("Starting session loading in the background");
            new Thread(sessionLoader).start();
            // And also start to periodically change the role assignments of the user
            System.out.println("Starting role changes in the background");
            new Thread(roleTweaker).start();

            // Try to login until it fails two times, then stop
            int succeededLogins = 0;
            int failedLogins = 0;

            while (failedLogins < FAILED_LOGIN_THRESHOLD && succeededLogins < SUCCESSFUL_LOGIN_THRESHOLD) {
                StringBuilder msg = new StringBuilder("Trying to login...");
                if (login(httpClient, REALM_NAME, CLIENT_NAME, USER, USER)) {
                    failedLogins = 0;
                    succeededLogins++;
                    msg.append("SUCCESS");
                } else {
                    succeededLogins = 0;
                    failedLogins++;
                    msg.append("FAILED");
                }
                msg.append(String.format(" (%d successful, %d failed)", succeededLogins, failedLogins));
                System.out.println(msg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            sessionLoader.stop();
            roleTweaker.stop();
            cleanup(keycloak);
        }
    }

    private static boolean login(Client httpClient, String realm, String client, String user, String password) {
        WebTarget loginTarget = httpClient.target(String.format("http://localhost:8180/auth/realms/%s/protocol/openid-connect/token", realm));

        Form loginForm = new Form();
        loginForm.param("grant_type", "password");
        loginForm.param("client_id", client);
        loginForm.param("username", user);
        loginForm.param("password", password);

        Response response = loginTarget.request().post(Entity.form(loginForm));
        if (response.getStatus() != 200) {
            System.err.println(String.format("Failed to login with status code %d: %s", response.getStatus(), response.readEntity(String.class)));
            return false;
        }

        return true;
    }


    private static void setupRealmUserAndRole(Keycloak keycloak) {
        System.out.println("Setting up test data...");

        // Set up realm
        RealmRepresentation realmRepresentation = new RealmRepresentation();
        realmRepresentation.setRealm(REALM_NAME);
        realmRepresentation.setEnabled(true);
        keycloak.realms().create(realmRepresentation);
        RealmResource realm = keycloak.realm(REALM_NAME);
        System.out.println("✓ Realm");

        // Set up user
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(USER);
        userRepresentation.setEnabled(true);
        userRepresentation.setFirstName(USER);
        userRepresentation.setLastName(USER);
        userRepresentation.setEmail(String.format("%s@%s.com", USER, USER));
        userRepresentation.setEmailVerified(true);
        Response response = realm.users().create(userRepresentation);
        String userId = response.getHeaderString("Location").replaceAll(".*/(.*)$", "$1");
        response.close();
        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
        credentialRepresentation.setTemporary(false);
        credentialRepresentation.setValue(USER);
        realm.users().get(userId).resetPassword(credentialRepresentation);
        System.out.println("✓ User");

        // Set up client
        ClientRepresentation clientRepresentation = new ClientRepresentation();
        clientRepresentation.setClientId(CLIENT_NAME);
        clientRepresentation.setName(CLIENT_NAME);
        clientRepresentation.setDirectAccessGrantsEnabled(true);
        clientRepresentation.setRedirectUris(Collections.singletonList("*"));
        clientRepresentation.setImplicitFlowEnabled(true);
        clientRepresentation.setPublicClient(true);
        realm.clients().create(clientRepresentation);
        System.out.println("✓ Client");

        // Set up initial role
        RoleRepresentation roleRepresentation = new RoleRepresentation();
        roleRepresentation.setName(INIT_ROLE);
        roleRepresentation.setId(INIT_ROLE);
        realm.roles().create(roleRepresentation);
        RoleResource role = realm.roles().get(INIT_ROLE);
        realm.users().get(userId).roles().realmLevel().add(List.of(role.toRepresentation()));
        System.out.println("✓ INIT_ROLE");

        // Create additional role
        RoleRepresentation additionalRoleRepresentation = new RoleRepresentation();
        additionalRoleRepresentation.setName(ADDITIONAL_ROLE);
        additionalRoleRepresentation.setId(ADDITIONAL_ROLE);
        realm.roles().create(additionalRoleRepresentation);
        System.out.println("✓ ADDITIONAL_ROLE");
        System.out.println("Setup done");
    }

    private static void cleanup(Keycloak keycloak) {
        System.out.println("Cleaning up test data...");
        keycloak.realm(REALM_NAME).remove();
        System.out.println("✓ Realm");
        System.out.println("Cleanup done");
    }
}