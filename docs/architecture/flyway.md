# Flyway For Spring Services

## Maven

Add both dependencies to every Spring service that owns PostgreSQL tables:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Spring Boot dependency management supplies compatible versions.

## Configuration

Example for `duriancare-auth-service`:

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/duriancare}
    username: ${DATABASE_USERNAME:duriancare}
    password: ${DATABASE_PASSWORD}
    hikari:
      schema: auth
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        default_schema: auth
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: auth
    default-schema: auth
    create-schemas: true
    validate-on-migrate: true
    clean-disabled: true
```

## Maven Source Tree

```text
duriancare-auth-service/
└── src/
    └── main/
        └── resources/
            └── db/
                └── migration/
                    ├── V1__init_auth_schema.sql
                    ├── V2__create_profiles.sql
                    └── V3__add_user_indexes.sql
```

Naming format is `V<version>__<description>.sql`. Never edit a migration that
has already been applied; create the next version.

Each service must use its own location and schema. Do not place migrations in
the root Maven aggregator and do not let one service migrate another service's
schema.
