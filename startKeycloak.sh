docker run -d \
    --rm \
    -p 8180:8080 \
    --name keycloak \
    -e KEYCLOAK_USER=admin \
    -e KEYCLOAK_PASSWORD=admin \
    -e KEYCLOAK_LOGLEVEL=DEBUG \
    --no-healthcheck \
    jboss/keycloak