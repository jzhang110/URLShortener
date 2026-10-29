# syntax=docker/dockerfile:1

# Base images use specific version tags. Tags are mutable, so this is not an immutable pin;
# for reproducible production builds, pin by digest (image@sha256:...) and let Dependabot bump it.

# ---- build stage: full JDK + Maven wrapper, discarded after the jar is produced ----
FROM eclipse-temurin:24-jdk-alpine-3.22 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Tests are run by `./mvnw verify` before building the image, not inside it.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests \
 && cp target/url-shortener-*.jar /workspace/app.jar

# ---- runtime stage: JRE only, non-root, no build tooling ----
FROM eclipse-temurin:24-jre-alpine-3.22

RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app

# Owned by root and not writable by the runtime user.
COPY --from=build /workspace/app.jar /app/app.jar

USER app
EXPOSE 8080

# Size the heap from the container memory limit; fail fast instead of limping on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
