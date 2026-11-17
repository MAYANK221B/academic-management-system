# Acadence backend

Java 21, Spring Boot 4.1.1, and Maven. Generated programmatically with Spring Initializr using the Web and Validation selections, with Spring Boot Test for tests.

## Files

- `pom.xml`: Java version, Web MVC, Validation, Boot Test, and executable JAR build configuration.
- `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`: Maven Wrapper for Unix and Windows. The generated script-only wrapper downloads Maven without needing a wrapper JAR.
- `src/main/java/com/acadence/AcadenceApplication.java`: application entry point.
- `src/main/java/com/acadence/HealthController.java`: `GET /api/health`, returning HTTP 200 and `{"status":"UP"}` as JSON.
- `src/main/resources/application.properties`: application name and port 8080.
- `src/test/java/com/acadence/AcadenceApplicationTests.java`: application startup and HTTP health endpoint tests using a random port.
- `.gitignore`, `.gitattributes`, `HELP.md`: generated development settings and Initializr reference links.
- `target/`: generated test reports and executable JAR after building.

## Run in IntelliJ IDEA

The outer `acadence` directory initially contained a plain Java module. The backend must be imported as a Maven module so IntelliJ can resolve Spring dependencies and recognize its source folders.

1. Right-click `acadence-backend/pom.xml` and select **Add as Maven Project** (if already linked, use **Reload All Maven Projects** in the Maven tool window).
2. Use Java 21 for the Project SDK and Maven importer/runner JDK.
3. After Maven import finishes, choose the shared **Acadence** run configuration, or run the `main` method in `AcadenceApplication.java`.

The outer project's `.idea/misc.xml` now registers the backend POM, and `.run/Acadence.run.xml` defines the application run configuration. If IntelliJ does not pick up the external settings changes immediately, reopen the project and reload Maven.

The shell's default Java is 17, so terminal runs need the Java 21 `JAVA_HOME` below. A globally installed Maven is unnecessary because the wrapper is included.

## Build and run from the terminal

From `acadence-backend`, set `JAVA_HOME` to your Java 21 JDK. These commands keep Maven downloads inside this project:

```powershell
$env:JAVA_HOME = 'C:\Users\mayan\.jdks\ms-21.0.12.1'
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.maven-user-home'
.\mvnw.cmd "-Dmaven.repo.local=$env:MAVEN_USER_HOME\repository" clean verify
& "$env:JAVA_HOME\bin\java.exe" -jar target/acadence-backend-0.0.1-SNAPSHOT.jar
```

Visit `http://localhost:8080/api/health`.

On Unix, use `./mvnw clean verify` with a Java 21 `JAVA_HOME`, then `java -jar target/acadence-backend-0.0.1-SNAPSHOT.jar`.

## Generation notes

Initializr returned parent version `4.1.1.RELEASE`, which could not be resolved from Maven Central. The POM uses the published `4.1.1` version instead. Spring Boot 4 uses `spring-boot-starter-webmvc` for the Web selection.

No database, Hibernate ORM, security, Kafka, or AI dependencies are configured. Spring Validation brings Hibernate Validator, the Bean Validation implementation; it does not add Hibernate ORM or database persistence.
