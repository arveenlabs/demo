---
name: Spring Boot Maven env vars
description: Why ${ENV_VAR} in application.properties doesn't resolve OS secrets when run via mvn spring-boot:run on Replit, and how to fix it.
---

# Spring Boot Maven Plugin — Env Var Resolution

## The Rule
Do NOT use `${ENV_VAR}` placeholders in `application.properties` to read Replit Secrets when running via `mvn spring-boot:run`. Use `System.getenv("ENV_VAR")` inside a `@Configuration` bean instead.

**Why:** The Spring Boot Maven plugin runs the app inside Maven's JVM. Even though the OS env var IS available to the workflow shell process (confirmed via `printenv`), Spring's `SystemEnvironmentPropertySource` / `PropertyPlaceholderHelper` fails to resolve the placeholder — the exact cause is unknown (possibly a classloader or property-source ordering quirk in Spring Boot 4.x + Maven plugin 4.x). The env var IS reachable via `System.getenv()` directly in Java code.

**How to apply:** For any secret needed at startup (MongoDB URI, JWT secret, etc.), read it programmatically:

```java
@Configuration
public class MongoConfig {
    @Bean
    public MongoClient mongoClient() {
        String uri = System.getenv("SPRING_DATA_MONGODB_URI");
        if (uri == null || uri.isBlank()) uri = "mongodb://localhost:27017/lms_app";
        return MongoClients.create(uri);
    }
}
```

Also: do NOT add `<environmentVariables>` to the Maven plugin config using `${env.VAR}` — Maven resolves these at configure time and may pass an empty string, which overrides the real env var in the child JVM.

**Confirmed working:** `MongoConfig.java` using `System.getenv()` → Atlas replica set connected, DataSeeder ran successfully.
