# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Split the fat jar into layers (deps / loader / app) so each can be copied separately
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# Detect the JPMS modules this app actually needs, then link a trimmed custom JRE
RUN jdeps \
      --ignore-missing-deps \
      -q \
      --recursive \
      --multi-release 21 \
      --print-module-deps \
      --class-path "extracted/dependencies/BOOT-INF/lib/*" \
      extracted/application/BOOT-INF/classes > /tmp/modules.txt \
    && cat /tmp/modules.txt

RUN jlink \
      --no-header-files \
      --no-man-pages \
      --strip-debug \
      --compress=2 \
      --module-path "$JAVA_HOME/jmods" \
      --add-modules "$(cat /tmp/modules.txt),jdk.crypto.ec" \
      --output /customjre

# Bare Alpine base instead of a general-purpose JRE image — the custom JRE above
# already carries everything the app needs, so no JDK/JRE package is installed here.
FROM alpine:3.20
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

ENV JAVA_HOME=/opt/customjre
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=build /customjre $JAVA_HOME

COPY --from=build --chown=app:app /app/extracted/dependencies/ ./
COPY --from=build --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /app/extracted/application/ ./

USER app

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
