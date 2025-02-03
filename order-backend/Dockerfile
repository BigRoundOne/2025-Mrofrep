ARG GITHUB_USER

FROM maven:3.8.5-openjdk-17-slim AS build

WORKDIR /app

COPY pom.xml /app/
COPY loadgen/pom.xml /app/loadgen/
COPY order-api/pom.xml /app/order-api/
COPY order-backend/pom.xml /app/order-backend/
COPY common/pom.xml /app/common/

RUN mvn dependency:resolve-plugins dependency:go-offline -B
RUN curl -L -o /app/opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar

WORKDIR /app
COPY . /app
RUN mvn clean install -DskipTests -B

FROM openjdk:17.0.1-jdk-slim

ARG GITHUB_USER
ARG DT_API_TOKEN
ARG DT_ENVIRONMENT_HOST

# environment variables to setup tracing
ENV OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true
ENV OTEL_SERVICE_NAME=order-backend-${GITHUB_USER}
ENV OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
ENV OTEL_PROPAGATORS=tracecontext

ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=http/protobuf
ENV OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=https://${DT_ENVIRONMENT_HOST}/api/v2/otlp/v1/traces
# ENV OTEL_EXPORTER_OTLP_TRACES_HEADERS=Authorization="Api-Token ${DT_API_TOKEN}"

# environment variables to setup metrics
ENV OTEL_METRICS_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=DELTA
ENV OTEL_EXPORTER_OTLP_METRICS_PROTOCOL=http/protobuf
ENV OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=https://${DT_ENVIRONMENT_HOST}/api/v2/otlp/v1/metrics
ENV OTEL_EXPORTER_OTLP_METRICS_HEADERS=Authorization="Api-Token ${DT_API_TOKEN}"

# environment variable for collector
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://opentelemetry-collector:4318

# environment variables to setup logs
#ENV OTEL_LOGS_EXPORTER=otlp
#ENV OTEL_EXPORTER_OTLP_LOGS_PROTOCOL=http/protobuf
##--required if NOT using collector--
#ENV OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=https://${DT_ENVIRONMENT_HOST}/api/v2/otlp/v1/logs
#ENV OTEL_EXPORTER_OTLP_LOGS_HEADERS=Authorization="Api-Token ${DT_API_TOKEN}"

WORKDIR /app
COPY --from=build /app/order-backend/target/order-backend-1.0-SNAPSHOT-all.jar order-backend.jar
COPY --from=build /app/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

ENV OTEL_INSTRUMENTATION_JETTY_ENABLED=false
ENV OTEL_INSTRUMENTATION_SERVLET_ENABLED=false

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "order-backend.jar"]
