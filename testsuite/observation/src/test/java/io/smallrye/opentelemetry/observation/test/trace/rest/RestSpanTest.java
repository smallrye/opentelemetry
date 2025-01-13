package io.smallrye.opentelemetry.observation.test.trace.rest;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_LOCAL_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_QUERY;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static io.restassured.RestAssured.given;
import static io.smallrye.opentelemetry.observation.test.AttributeKeysStability.get;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URL;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import io.smallrye.opentelemetry.test.InMemoryExporter;

@ExtendWith(ArquillianExtension.class)
class RestSpanTest {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class);
    }

    @ArquillianResource
    URL url;
    @Inject
    InMemoryExporter spanExporter;

    @BeforeEach
    void setUp() {
        spanExporter.reset();
    }

    @Test
    void span() {
        given().get("/span").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(1, spanItems.size());
        SpanData span = spanItems.get(0);
        assertEquals(SERVER, span.getKind());
        assertEquals(HttpMethod.GET + " " + url.getPath() + "span", span.getName());
        assertEquals(HTTP_OK, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
        assertEquals(HttpMethod.GET, span.getAttributes().get(HTTP_REQUEST_METHOD));
        assertEquals("http", span.getAttributes().get(NETWORK_PROTOCOL_NAME));
        assertEquals("UNKNOWN", span.getAttributes().get(NETWORK_PROTOCOL_VERSION));

        assertEquals("tck", span.getResource().getAttribute(SERVICE_NAME));
        assertEquals("1.0", span.getResource().getAttribute(SERVICE_VERSION));

        InstrumentationScopeInfo libraryInfo = span.getInstrumentationScopeInfo();
        assertEquals(OpenTelemetryConfig.INSTRUMENTATION_NAME, libraryInfo.getName());
        //        assertEquals(OpenTelemetryConfig.INSTRUMENTATION_VERSION, libraryInfo.getVersion());  FIXME
    }

    @Test
    void spanName() {
        given().get("/span/1").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(1, spanItems.size());
        SpanData span = spanItems.get(0);
        assertEquals(SERVER, span.getKind());
        assertEquals(HttpMethod.GET + " " + url.getPath() + "span/{name}", span.getName());
        assertEquals(HTTP_OK, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
        assertEquals(HttpMethod.GET, span.getAttributes().get(HTTP_REQUEST_METHOD));
    }

    @Test
    void spanNameWithoutQueryString() {
        given().get("/span/1?id=1").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(1, spanItems.size());
        SpanData span = spanItems.get(0);
        assertEquals(SERVER, span.getKind());
        assertEquals(HttpMethod.GET + " " + url.getPath() + "span/{name}", span.getName());
        assertEquals(HTTP_OK, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
        assertEquals(HttpMethod.GET, span.getAttributes().get(HTTP_REQUEST_METHOD));
        assertEquals(url.getPath() + "span/1", get(span, URL_PATH));
        assertEquals("id=1", get(span, URL_QUERY));
        assertEquals(url.getPath() + "span/{name}", span.getAttributes().get(HTTP_ROUTE));
    }

    @Test
    void spanPost() {
        given().body("payload").post("/span").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(1, spanItems.size());
        SpanData span = spanItems.get(0);
        assertEquals(SERVER, span.getKind());
        assertEquals(HttpMethod.POST + " " + url.getPath() + "span", span.getName());

        // Common Attributes
        assertEquals(HttpMethod.POST, span.getAttributes().get(HTTP_REQUEST_METHOD));
        assertEquals(HTTP_OK, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
        assertNotNull(span.getAttributes().get(USER_AGENT_ORIGINAL));
        assertNull(span.getAttributes().get(NETWORK_LOCAL_ADDRESS));
        assertNull(span.getAttributes().get(NETWORK_LOCAL_PORT));

        // Server Attributes
        assertEquals("http", span.getAttributes().get(URL_SCHEME));
        assertEquals(url.getPath() + "span", span.getAttributes().get(URL_PATH));
        assertEquals(url.getPath() + "span", span.getAttributes().get(HTTP_ROUTE));
        assertNull(span.getAttributes().get(CLIENT_ADDRESS));
        assertEquals(url.getHost(), span.getAttributes().get(SERVER_ADDRESS));
        assertEquals(url.getPort(), span.getAttributes().get(SERVER_PORT));
    }

    @Test
    void subResource() {
        given().get("/sub/1").then().statusCode(HTTP_OK);

        List<SpanData> spanItems = spanExporter.getFinishedSpanItems(1);
        assertEquals(1, spanItems.size());
        SpanData span = spanItems.get(0);
        assertEquals(SERVER, span.getKind());
        assertEquals(HttpMethod.GET, span.getName());
        assertEquals(HTTP_OK, span.getAttributes().get(HTTP_RESPONSE_STATUS_CODE));
        assertEquals(HttpMethod.GET, span.getAttributes().get(HTTP_REQUEST_METHOD));
    }

    @Path("/")
    public static class SpanResource {
        @GET
        @Path("/span")
        public Response span() {
            return Response.ok().build();
        }

        @GET
        @Path("/span/{name}")
        public Response spanName(@PathParam(value = "name") String name) {
            return Response.ok().build();
        }

        @POST
        @Path("/span")
        public Response spanPost(String payload) {
            return Response.ok(payload).build();
        }

        @Path("/sub/{id}")
        public SubResource subResource(@PathParam("id") String id) {
            return new SubResource(id);
        }
    }

    public static class SubResource {
        private final String id;

        public SubResource(final String id) {
            this.id = id;
        }

        @GET
        public Response get() {
            return Response.ok().build();
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }
}
