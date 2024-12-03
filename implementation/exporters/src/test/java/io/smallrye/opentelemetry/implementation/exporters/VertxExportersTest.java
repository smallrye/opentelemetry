package io.smallrye.opentelemetry.implementation.exporters;

import static io.smallrye.opentelemetry.implementation.exporters.Constants.OTEL_EXPORTER_OTLP_ENDPOINT;
import static io.smallrye.opentelemetry.implementation.exporters.Constants.OTEL_EXPORTER_OTLP_TRACES_PROTOCOL;
import static io.smallrye.opentelemetry.implementation.exporters.Constants.PROTOCOL_GRPC;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.smallrye.opentelemetry.api.OpenTelemetryBuilderGetter;

/**
 * This test will exercise the configuration and use of the Vertx-based exporters defined in this module. The test will
 * spin up a testcontainers-based OpenTelemetry Collector, configured to receive trace data via gRPC or http/protobuf.
 * For each protocol, there will be a test that will create the OpenTelemetry instance with the appropriate configuration,
 * then create a tracer, a span, and one event. Finally, rather than configuring the Collector to export the traces to
 * an external aggregation system, the test will simply pull the logs from the container and verify that the trace was
 * received and logged correctly.
 */
public class VertxExportersTest {
    private static OpenTelemetryCollectorContainer otelCollector;

    @BeforeAll
    public static void setup() {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available.");

        if (otelCollector == null) {
            otelCollector = new OpenTelemetryCollectorContainer();

            otelCollector.start();
        }
    }

    @AfterAll
    public static void tearDown() {
        if (otelCollector != null) {
            otelCollector.stop();
            otelCollector = null;
        }
    }

    @Test
    public void testGrpcExporter() {
        testExporterByProtocol("grpc");
    }

    @Test
    public void testHttpExporter() {
        testExporterByProtocol("http/protobuf");
    }

    private void testExporterByProtocol(String protocol) {
        String endpoint = PROTOCOL_GRPC.equals(protocol) ? otelCollector.getOtlpGrpcEndpoint()
                : otelCollector.getOtlpHttpEndpoint();
        Map<String, String> config = Map.of(
                "otel.traces.exporter", "otlp",
                OTEL_EXPORTER_OTLP_TRACES_PROTOCOL, protocol,
                "otel.bsp.schedule.delay", "1",
                OTEL_EXPORTER_OTLP_ENDPOINT, endpoint);
        final String tracerName = "smallrye.opentelemetry.test." + protocol;
        final String spanName = protocol + " test trace";
        final String eventName = protocol + " test event";

        OpenTelemetry openTelemetry = new OpenTelemetryBuilderGetter().apply(() -> config).build().getOpenTelemetrySdk();
        Tracer tracer = openTelemetry.getTracer(tracerName);
        Span span = tracer.spanBuilder(spanName).startSpan();
        span.addEvent(eventName);
        span.end();

        boolean found = false;
        int count = 0;
        while (!found && count < 10) {
            String logs = otelCollector.getLogs();
            found = logs.contains(tracerName) &&
                    logs.contains(spanName) &&
                    logs.contains(eventName);
            if (!found) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                count++;
            }

        }

        Assertions.assertTrue(found, "Trace data not found.");
    }

    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
