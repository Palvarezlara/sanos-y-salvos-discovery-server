package com.sanosysalvos.discovery_server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * Servidor Eureka real en un puerto aleatorio, sin autenticación configurada (desarrollo local). Sin config-server:
 * se pasan a mano los valores que normalmente llegan de discovery-server.yml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SPRING_CONFIG_IMPORT=optional:configserver:",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.fail-fast=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false" })
class DiscoveryServerApplicationTests {

    static final String INSTANCIA = """
            {"instance":{"instanceId":"prueba-1","hostName":"localhost","app":"MS-PRUEBA","ipAddr":"127.0.0.1",
            "status":"UP","port":{"$":8080,"@enabled":"true"},
            "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
            """;

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> enviar(HttpRequest.Builder pedido) throws IOException, InterruptedException {
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder en(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).header("Accept", "application/json");
    }

    @Test
    void unServicioSeRegistraYApareceEnElRegistro() throws Exception {
        HttpResponse<String> alta = enviar(en("/eureka/apps/MS-PRUEBA").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INSTANCIA)));
        assertThat(alta.statusCode()).isEqualTo(204);

        HttpResponse<String> consulta = enviar(en("/eureka/apps/MS-PRUEBA").GET());

        assertThat(consulta.statusCode()).isEqualTo(200);
        assertThat(consulta.body()).contains("MS-PRUEBA", "prueba-1", "127.0.0.1");
    }

    @Test
    void unaInstanciaSePuedeDarDeBaja() throws Exception {
        enviar(en("/eureka/apps/MS-BAJA").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INSTANCIA.replace("MS-PRUEBA", "MS-BAJA"))));

        HttpResponse<String> baja = enviar(en("/eureka/apps/MS-BAJA/prueba-1").DELETE());

        assertThat(baja.statusCode()).isEqualTo(200);
        assertThat(enviar(en("/eureka/apps/MS-BAJA").GET()).statusCode()).isEqualTo(404);
    }

    @Test
    void elHealthNoMuestraDetallesSinAutenticar() throws Exception {
        HttpResponse<String> respuesta = enviar(en("/actuator/health").GET());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"status\":\"UP\"").doesNotContain("diskSpace");
    }

    @Test
    void soloSeExponenHealthEInfo() throws Exception {
        String indice = enviar(en("/actuator").GET()).body();

        assertThat(indice).doesNotContain("\"env\"", "\"beans\"", "\"heapdump\"", "\"configprops\"");
    }
}
