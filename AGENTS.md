# Repository Guidelines

## Project Structure & Module Organization

This is a Java 17, Spring Boot 3.5, Spring Cloud Maven reactor. Shared dependency versions live in `yudao-dependencies`; reusable infrastructure and starters live in `yudao-framework`. `yudao-gateway` is the API gateway, while `yudao-server` assembles selected modules for monolithic development.

Business areas use `yudao-module-<domain>/`, normally split into an `-api` project for cross-service contracts and a `-server` project for controllers, services, persistence, and tests. Production code is under `src/main/java` and `src/main/resources`; tests mirror packages under `src/test/java`, with fixtures in `src/test/resources`. Database scripts are grouped by vendor in `sql/`. `yudao-ui/` contains links to separately maintained frontend repositories rather than their source.

## Build, Test, and Development Commands

- `mvn clean install -DskipTests` compiles and installs the complete reactor without running tests.
- `mvn test` runs all unit tests.
- `mvn -pl yudao-module-system/yudao-module-system-server -am test` tests one server module and required dependencies; replace the module path as needed.
- `mvn -pl yudao-server -am package -DskipTests` builds the aggregate executable JAR.
- `mvn -pl yudao-server spring-boot:run -Dspring-boot.run.profiles=local` starts the aggregate server after dependencies are installed. Local profiles expect services such as MySQL, Redis, and Nacos; initialize the database from `sql/mysql/`.

## Coding Style & Naming Conventions

Follow the existing Alibaba Java style: four-space indentation, UTF-8, one public type per file, and package names rooted at `cn.iocoder.yudao`. Match established suffixes: `*Controller`, `*Service`/`*ServiceImpl`, `*Mapper`, `*DO`, `*ReqVO`, and `*RespVO`. Keep API contracts in `-api` and implementations in `-server`. Use Lombok and MapStruct consistently with neighboring code. No repository-wide formatter is configured, so format with the IDE and avoid unrelated whitespace changes.

## Testing Guidelines

Tests use JUnit 5, Mockito, Spring Test, and H2-backed base classes from `yudao-spring-boot-starter-test`. Name classes `*Test.java` and methods descriptively, commonly `testOperation_condition`. Extend `BaseDbUnitTest` or `BaseMockitoUnitTest` where appropriate, and keep module fixtures in `application-unit-test.yaml` plus `sql/create_tables.sql` and `sql/clean.sql`. Add tests for changed success paths, validation, and failure behavior.

## Commit & Pull Request Guidelines

Recent history favors Conventional Commit prefixes such as `feat:`, `fix:`, and `chore:`, optionally scoped (`feat(ai): ...`); concise Chinese or English subjects are accepted. Keep commits focused on one module or concern. Pull requests should explain behavior and affected modules, link relevant issues, list verification commands, and note configuration or schema changes. Include screenshots for UI changes and request/response examples for API contract changes. Never commit credentials; use local profiles or environment overrides.
