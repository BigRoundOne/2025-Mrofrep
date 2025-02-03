ARG DT_CODE_MODULE
ARG GITHUB_USER

FROM maven:3.8.5-openjdk-17-slim AS build

WORKDIR /app

COPY pom.xml /app/
COPY loadgen/pom.xml /app/loadgen/
COPY order-api/pom.xml /app/order-api/
COPY order-backend/pom.xml /app/order-backend/
COPY common/pom.xml /app/common/

RUN mvn dependency:resolve-plugins dependency:go-offline -B

ARG GITHUB_USER

WORKDIR /app
COPY . /app
RUN mvn clean install -DskipTests -B

FROM ${DT_CODE_MODULE} AS codemodules

FROM openjdk:17.0.1-jdk-slim

ARG GITHUB_USER
ARG DT_API_TOKEN
ARG DT_ENVIRONMENT_HOST
COPY --from=codemodules / /
ENV LD_PRELOAD=/opt/dynatrace/oneagent/agent/lib64/liboneagentproc.so

# environment variable for collector
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://opentelemetry-collector:4318

# environment variables to setup tracing
ENV OTEL_SERVICE_NAME=order-api-${GITHUB_USER}
ENV OTEL_PROPAGATORS=tracecontext
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true

# environment variables to setup metrics
ENV OTEL_METRICS_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=DELTA
ENV OTEL_EXPORTER_OTLP_METRICS_PROTOCOL=http/protobuf
# ENV OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=https://${DT_ENVIRONMENT_HOST}/api/v2/otlp/v1/metrics
# ENV OTEL_EXPORTER_OTLP_METRICS_HEADERS="Authorization=Api-Token ${DT_API_TOKEN}"

ARG GITHUB_USER
WORKDIR /app
ARG GITHUB_USER
COPY --from=build /app/order-api/target/order-api-1.0-SNAPSHOT-all.jar order-api-${GITHUB_USER}.jar
ENTRYPOINT ["sh", "-c", "java -jar order-api-${GITHUB_USER}.jar"]
