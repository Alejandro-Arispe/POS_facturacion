package com.pos.common.seed;

import java.util.stream.Stream;

import org.springframework.boot.builder.SpringApplicationBuilder;

import com.pos.PosBackendSpringApplication;

/** Comando de una sola ejecucion; usa la misma configuracion de BD que el backend. */
public class SeedDemoCommand {

    public static void main(String[] args) {
        String[] arguments = Stream.concat(Stream.of("--server.port=0"), Stream.of(args))
                .toArray(String[]::new);
        try (var context = new SpringApplicationBuilder(PosBackendSpringApplication.class)
                .profiles("seed-demo").run(arguments)) {
            String email = context.getEnvironment().getProperty("app.demo-seed.email", "mococe5860@ofags.com");
            var result = context.getBean(DemoAccountSeeder.class).seed(email);
            System.out.printf("[SEED] %s: %s (id=%d), cuenta %s.%n",
                    result.created() ? "Datos creados" : "Ya existian los datos; se omite",
                    result.companyName(), result.companyId(), email);
        }
    }
}
