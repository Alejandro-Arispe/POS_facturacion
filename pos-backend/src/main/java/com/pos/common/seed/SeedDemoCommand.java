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
            var env = context.getEnvironment();
            String email = env.getProperty("app.demo-seed.email", "admin@pos-demo.com");
            String password = env.getProperty("app.demo-seed.password", "Demo2026!");
            var seeder = context.getBean(DemoAccountSeeder.class);
            seeder.ensureUser(email, password, "Administrador Demo", "DEMO-ADMIN-1");
            var result = seeder.seed(email);
            var team = seeder.seedTeam(result.companyId(), password);
            System.out.printf("[SEED] %s: %s (id=%d), cuenta %s.%n",
                    result.created() ? "Datos creados" : "Ya existian los datos; se omite",
                    result.companyName(), result.companyId(), email);
            System.out.printf("[SEED] %-20s %-32s %s%n", "Administrador", email, password);
            team.forEach(login -> System.out.printf("[SEED] %-20s %-32s %s  (%s)%n",
                    login.role(), login.email(), login.password(), login.scope()));
        }
    }
}
