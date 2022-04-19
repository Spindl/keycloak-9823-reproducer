package com.nts.keycloak.reproducer;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import javax.ws.rs.WebApplicationException;
import java.util.List;

public class RoleTweaker implements Runnable {

    private final Keycloak keycloakClient;
    private final String realmName;
    private final String userName;
    private final String roleName;
    private boolean stop = false;

    public RoleTweaker(Keycloak keycloakClient, String realmName, String userName, String roleName) {
        this.keycloakClient = keycloakClient;
        this.realmName = realmName;
        this.userName = userName;
        this.roleName = roleName;
    }

    @Override
    public void run() {

        while (!stop) {
            try {
                RealmResource realm = keycloakClient.realm(realmName);
                String userId = realm.users().search(userName).stream().findFirst().map(UserRepresentation::getId).orElse(null);
                if (null != userId) {
                    UserResource user = realm.users().get(userId);
                    RoleMappingResource userRoles = user.roles();
                    RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
                    userRoles.getAll();
                    userRoles.realmLevel().add(List.of(role));
                    System.out.println("Successfully assigned additional role to user");

                    // Wait a while before removing the role again
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    user.roles().realmLevel().remove(List.of(role));
                    System.out.println("Successfully removed additional role from user");

                    // Wait a while before starting again
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("Could not find user");
                }
            } catch (WebApplicationException ex) {
                System.out.println("Failed to change role assignments: " + ex.getMessage());
            }
        }
        System.out.println("Role assignments stopped");
    }

    public void stop() {
        this.stop = true;
    }
}
