# Headless HO MCP server (core.HeadlessMain). Built by `./gradlew docker`, with the
# createDistribution output (build/install/XXX) as the build context. See README.md, "Docker".
FROM eclipse-temurin:17-jre

RUN useradd --system --home-dir /data ho && mkdir /data && chown ho /data

# prediction/ and truststore.jks resolve from the working directory, so the app dir is the workdir;
# users.json, db/ and logs/ go to the /data volume via ho.dbParentFolder.
# HO writes timestamps as JVM-local wall-clock time (HODateTime.toDbTimestamp), so the zone must be
# the one the database was written in - Europe/Berlin for a DB moved over from desktop HO. With UTC
# every existing timestamp reads hours off and TrainingWeekManager finds two "next" training weeks.
ENV TZ=Europe/Berlin
COPY . /opt/ho
WORKDIR /opt/ho
USER ho
VOLUME /data
EXPOSE 8765

# ho.mcpBind: the host cannot reach the container's loopback. Publish with -p 127.0.0.1:8765:8765
# so the server stays host-local - it has no authentication. JAVA_OPTS for extras (-Dho.teamId=...).
ENTRYPOINT ["sh", "-c", "exec java -Djava.awt.headless=true -Dho.dbParentFolder=/data -Dho.mcpBind=0.0.0.0 $JAVA_OPTS -cp /opt/ho/HO.jar core.HeadlessMain \"$@\"", "--"]
