# discovery-server

Registro **Eureka** de Sanos y Salvos (Spring Boot 4, Java 17). Los microservicios (`ms-mascotas`, `ms-alertas`, `ms-chat`) y el BFF se registran aquí. Toda su configuración vive en el Config Server (`config/discovery-server.yml`).

> **Estado real:** hoy el BFF **no** enruta usando Eureka: arma las URLs con los nombres de `servicios.rutas` (DNS de Docker/ECS). Los servicios se registran y el registro se puede consultar, pero si Eureka cae no cambia el enrutamiento. Mantenerlo es una decisión de arquitectura pendiente (usar `DiscoveryClient`/`lb://` o retirar el servidor).

## Seguridad

- **Autenticación básica opcional** (`SecurityConfig`): con `EUREKA_AUTH_PASSWORD` definida todo exige credenciales salvo `/actuator/health`; sin ella queda abierto y lo avisa en el log (solo desarrollo local). Los clientes la mandan en la URL del registro: `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://usuario:clave@discovery-server:8761/eureka/`.
- El puerto 8761 se publica solo en `127.0.0.1`; `/actuator/health` no muestra detalles sin autenticación.
- El contenedor corre sin root y la imagen tiene `HEALTHCHECK` HTTP contra `/actuator/health`.
- Importa su configuración del Config Server de forma **obligatoria**: si no responde, el servicio falla al arrancar y Docker lo reinicia, en vez de levantar con los valores por defecto (puerto 8080, sin registro) pareciendo sano.

## Variables

| Variable | Descripción |
|---|---|
| `SPRING_CONFIG_IMPORT` | `configserver:http://config-server:8888` (obligatorio en el compose) |
| `EUREKA_HOSTNAME` | Hostname del registro; sin valor por defecto (`discovery-server` en el compose) |
| `EUREKA_AUTH_USER` / `EUREKA_AUTH_PASSWORD` | Credenciales del registro; con la clave vacía queda abierto |
| `EUREKA_SELF_PRESERVATION`, `EUREKA_EVICTION_INTERVAL_MS`, `EUREKA_RENEWAL_PERCENT_THRESHOLD` | Por defecto, valores de desarrollo; en AWS activar la auto-preservación |

Es **una sola instancia con el registro en memoria**: tras un reinicio queda vacío hasta que los clientes vuelven a registrarse (lo hacen solos en unos segundos).

## Pruebas

```bash
./mvnw test
```

8 pruebas con un servidor Eureka real en un puerto aleatorio y sin Config Server: registrar una instancia por `POST /eureka/apps/{app}` y leerla de `GET /eureka/apps`, darla de baja, que `/actuator/health` no muestre detalles, que solo se expongan `health` e `info` y, con autenticación activa, que sin credenciales no se pueda listar, registrar ni dar de baja.
