package com.sanosysalvos.discovery_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.config.import=")
class DiscoveryServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
