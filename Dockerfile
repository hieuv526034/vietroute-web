# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Keep Maven and dependency downloads cached when only application code changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw --batch-mode --no-transfer-progress -Pproduction dependency:go-offline

# Production never copies src/test into the build context or compiles it.
COPY src/main/ src/main/
RUN sh ./mvnw --batch-mode --no-transfer-progress -Pproduction package

FROM tomcat:10.1-jre21-temurin-jammy AS runtime
ENV PORT=10000 \
    TZ=Asia/Ho_Chi_Minh \
    CATALINA_OPTS="-Xms64m -Xmx256m -XX:+UseSerialGC -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Ho_Chi_Minh"

RUN groupadd --gid 10001 vietroute \
    && useradd --uid 10001 --gid vietroute --no-create-home --shell /usr/sbin/nologin vietroute \
    && rm -rf /usr/local/tomcat/webapps.dist \
    && cp /usr/local/tomcat/conf/server.xml /usr/local/tomcat/conf/server.xml.template \
    && chown -R vietroute:vietroute /usr/local/tomcat
COPY --from=build --chown=vietroute:vietroute /workspace/target/vietroute.war /usr/local/tomcat/webapps/ROOT.war
COPY --chown=vietroute:vietroute docker/entrypoint.sh /usr/local/bin/vietroute-entrypoint.sh
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["sh", "/usr/local/bin/vietroute-entrypoint.sh"]
