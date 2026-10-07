# build stage
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
# overrides the youtube-source version in pom.xml, used by deploy/update.sh to test candidates
ARG YT_SOURCE_VERSION=
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests ${YT_SOURCE_VERSION:+-Dyoutube.source.version=$YT_SOURCE_VERSION} \
 && cp target/jankbot-*-jar-with-dependencies.jar /jankbot.jar

# runtime stage
FROM eclipse-temurin:25-jre
RUN useradd --system --create-home jankbot
COPY --from=build /jankbot.jar /app/jankbot.jar
ARG YT_SOURCE_VERSION=
LABEL jankbot.youtube-source-version=$YT_SOURCE_VERSION
USER jankbot
WORKDIR /app
ENV JANKBOT_CONFIG=/config/config.json \
    JANKBOT_STATUS_FILE=/tmp/jankbot-status
# healthy when the status file is fresh and discord is connected
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD test -n "$(find /tmp/jankbot-status -mmin -2)" && grep -q '^status=CONNECTED' /tmp/jankbot-status
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-cp", "/app/jankbot.jar"]
CMD ["janktastic.jankbot.JankBot"]
