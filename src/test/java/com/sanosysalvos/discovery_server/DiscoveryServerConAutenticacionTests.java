package com.sanosysalvos.discovery_server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/* Con EUREKA_AUTH_PASSWORD definida, solo el health es público: no se puede listar, registrar ni dar de baja sin clave */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SPRING_CONFIG_IMPORT=optional:configserver:",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.fail-fast=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "discovery.auth.user=servicio-eureka",
        "discovery.auth.password=clave-de-prueba-con-mas-de-treinta-caracteres" })
class DiscoveryServerConAutenticacionTests {

    private static final String USUARIO = "servicio-eureka";
    private static final String CLAVE = "clave-de-prueba-con-mas-de-treinta-caracteres";

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpRequest.Builder en(String ruta, String usuario, String clave) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .header("Accept", "application/json");
        if (usuario != null) {
            String credencial = Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
            pedido.header("Authorization", "Basic " + credencial);
        }
        return pedido;
    }

    private HttpResponse<String> enviar(HttpRequest pedido) throws IOException, InterruptedException {
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sinCredencialesNoSePuedeListarNiRegistrarNiDarDeBaja() throws Exception {
        assertThat(enviar(en("/eureka/apps", null, null).GET().build()).statusCode()).isEqualTo(401);
        assertThat(enviar(en("/eureka/apps/FALSA", null, null).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(DiscoveryServerApplicationTests.INSTANCIA)).build()).statusCode())
                .isEqualTo(401);
        assertThat(enviar(en("/eureka/apps/MS-X/x-1", null, null).DELETE().build()).statusCode()).isEqualTo(401);
        assertThat(enviar(en("/", null, null).GET().build()).statusCode()).isEqualTo(401);
    }

    @Test
    void conCredencialesCorrectasSePuedeRegistrarYConsultar() throws Exception {
        HttpResponse<String> alta = enviar(en("/eureka/apps/MS-PRUEBA", USUARIO, CLAVE).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(DiscoveryServerApplicationTests.INSTANCIA)).build());
        assertThat(alta.statusCode()).isEqualTo(204);

        assertThat(enviar(en("/eureka/apps/MS-PRUEBA", USUARIO, CLAVE).GET().build()).body()).contains("MS-PRUEBA");
    }

    @Test
    void conCredencialesIncorrectasSeRechaza() throws Exception {
        assertThat(enviar(en("/eureka/apps", USUARIO, "otra-clave").GET().build()).statusCode()).isEqualTo(401);
        assertThat(enviar(en("/eureka/apps", "otro-usuario", CLAVE).GET().build()).statusCode()).isEqualTo(401);
    }

    @Test
    void elHealthSigueAbiertoParaElHealthcheckPeroSinDetalles() throws Exception {
        HttpResponse<String> respuesta = enviar(en("/actuator/health", null, null).GET().build());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"status\":\"UP\"").doesNotContain("diskSpace");
    }
}
