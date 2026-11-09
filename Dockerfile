FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /build/target/videoquiz-1.0.0.jar /app/videoquiz.jar
ENV SPRING_PROFILES_ACTIVE=prod,cloud
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/videoquiz.jar"]
