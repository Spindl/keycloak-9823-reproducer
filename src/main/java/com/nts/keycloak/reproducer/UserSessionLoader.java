package com.nts.keycloak.reproducer;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;

import javax.ws.rs.WebApplicationException;
import java.util.List;

public class UserSessionLoader implements Runnable {

    private final Keycloak keycloakClient;
    private final String realmName;
    private final String clientId;
    private boolean stop = false;

    public UserSessionLoader(Keycloak keycloakClient, String realmName, String clientId) {
        this.keycloakClient = keycloakClient;
        this.realmName = realmName;
        this.clientId = clientId;
    }

    @Override
    public void run() {

        while (!stop) {
            try {
                // Get the realm
                RealmResource realm = keycloakClient.realm(this.realmName);
                // Get the client
                ClientRepresentation client = realm.clients().findByClientId(clientId).stream().findFirst().orElse(null);

                if (null != client) {
                    // Get the sessions
                    List<UserSessionRepresentation> userSessions = realm.clients().get(client.getId()).getUserSessions(0, Integer.MAX_VALUE);
                    System.out.printf("Successfully loaded %d sessions%n", userSessions.size());
                } else {
                    System.out.printf("Could not find client %s%n", clientId);
                }

                // Wait a while before doing it again
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } catch (WebApplicationException ex) {
                System.err.println("Failed to load sessions: " + ex.getMessage());
            }
        }
        System.out.println("Session loading stopped");
    }

    public void stop() {
        this.stop = true;
    }
}
