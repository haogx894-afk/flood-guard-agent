FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn

RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -B -DskipTests package \
    && JAR_FILE="$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1)" \
    && cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

RUN useradd -r -u 10001 appuser \
    && mkdir -p /data/knowledge-documents \
    && chown -R appuser:appuser /app /data

COPY --from=build /workspace/app.jar /app/app.jar

USER appuser

EXPOSE 8123

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar /app/app.jar"]
