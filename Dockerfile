FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -o package -DskipTests \
    && java -Djarmode=tools -jar target/artemis-*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S artemis && adduser -S artemis -G artemis
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER artemis
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
