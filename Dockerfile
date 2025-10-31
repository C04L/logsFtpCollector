FROM ghcr.io/graalvm/jdk-community:25 AS builder

WORKDIR /build
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src

# Build the application using the Java 23 environment
RUN ./gradlew build --no-daemon

# Use the same Java 23 base image for the final runtime
FROM ghcr.io/graalvm/jdk-community:25

WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar
RUN mkdir -p /app/data

ENTRYPOINT ["java", "-jar", "app.jar"]