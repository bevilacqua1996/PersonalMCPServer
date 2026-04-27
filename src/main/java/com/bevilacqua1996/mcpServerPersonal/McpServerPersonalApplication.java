package com.bevilacqua1996.mcpServerPersonal;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class McpServerPersonalApplication implements QuarkusApplication {

    public static void main(String[] args) {
        Quarkus.run(McpServerPersonalApplication.class, args);
    }

    @Override
    public int run(String... args) {
        Quarkus.waitForExit();
        return 0;
    }
}
