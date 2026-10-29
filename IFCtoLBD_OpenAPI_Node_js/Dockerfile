FROM eclipse-temurin:21-jre-jammy AS java-runtime

FROM node:22-bookworm-slim

ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    HOST=0.0.0.0 \
    PORT=8080 \
    NODE_ENV=production \
    IFCTOLBD_API_CONVERTER_JAR=/app/lib/ifctolbd-converter.jar

COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
WORKDIR /app

COPY IFCtoLBD_OpenAPI_Node_js/package.json IFCtoLBD_OpenAPI_Node_js/package-lock.json ./
RUN npm ci --omit=dev

COPY IFCtoLBD_OpenAPI_Node_js/src ./src
COPY IFCtoLBD_MCP/lib/ifctolbd-converter.jar ./lib/ifctolbd-converter.jar
COPY LICENSE THIRD_PARTY_PROVENANCE.md ./legal/
COPY IFCtoLBD_OpenAPI_Node_js/THIRD_PARTY.md ./legal/THIRD_PARTY_NODE.md

USER node
EXPOSE 8080
CMD ["node", "src/server.js"]
