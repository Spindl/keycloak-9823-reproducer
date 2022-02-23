package com.nts.keycloak.reproducer;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

public class Main {

    public static void main(String[] args) {
        Keycloak keycloak = KeycloakBuilder.builder().build();

        new Thread(Main::periodicallyQueryUserSessions).start();
    }

    private static void periodicallyQueryUserSessions() {

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}