package com.ufc.server;

import com.ufc.server.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServerApplicationTests extends PostgresTestcontainer {

    @Test
    void contextLoads() {}
}
