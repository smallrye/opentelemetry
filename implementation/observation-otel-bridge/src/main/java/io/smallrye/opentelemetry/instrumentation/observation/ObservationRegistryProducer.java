package io.smallrye.opentelemetry.instrumentation.observation;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.smallrye.opentelemetry.instrumentation.observation.handler.OpenTelemetryObservationHandler;
import io.smallrye.opentelemetry.instrumentation.observation.handler.PropagatingReceiverTracingObservationHandler;
import io.smallrye.opentelemetry.instrumentation.observation.handler.PropagatingSenderTracingObservationHandler;

@Singleton
public class ObservationRegistryProducer {
    @Inject
    Tracer tracer;

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    OpenTelemetryObservationHandler openTelemetryObservationHandler;

    @Inject
    MeterRegistry registry;

    @Produces
    @Singleton
    public ObservationRegistry registry() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();

        observationRegistry.observationConfig()
                //        .observationFilter(new CloudObservationFilter())  // Where global filters go
                //        .observationConvention(new GlobalTaxObservationConvention())  Where global conventions go
                .observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(
                        new PropagatingSenderTracingObservationHandler(tracer,
                                openTelemetry.getPropagators().getTextMapPropagator()),
                        new PropagatingReceiverTracingObservationHandler(tracer,
                                openTelemetry.getPropagators().getTextMapPropagator()),
                        //   new TracingAwareMeterObservationHandler(tracer) // For exemplars... Maybe not be needed
                        openTelemetryObservationHandler))
                .observationHandler(new DefaultMeterObservationHandler(registry));
        //      .observationHandler(new PrintOutHandler())  // Can be implemented for debugging. Other handlers for future frameworks can also be added.
        return observationRegistry;
    }
}
