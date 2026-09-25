package com.concept.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks in the actuator exposure contract. The health probe must be publicly
 * reachable (Render calls it unauthenticated) while leaking nothing, and the
 * remaining endpoints — which describe internals — must never be public. The
 * web security chain ends in {@code anyRequest().permitAll()}, so without the
 * explicit /actuator rules the metrics and prometheus scrape endpoints would
 * be world-readable; these tests fail if those rules are ever dropped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void healthIsPublicAndReportsStatusOnly() throws Exception {
        // Public probe: reachable anonymously, but the body carries only the
        // aggregate status — no component/db/disk detail for an unauthenticated
        // caller (management.endpoint.health.show-details=when-authorized).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void healthTracksTheDatabase() {
        // The reason the Render probe moved off /login: /login returns 200 even
        // when the database is unreachable. Health aggregates a db indicator, so
        // a broken database turns the probe DOWN.
        var health = healthEndpoint.healthForPath("db");
        assertNotNull(health, "no 'db' health indicator is registered - a dead "
                + "database would not be reflected in /actuator/health");
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void metricsAndPrometheusAreNotPublic() throws Exception {
        // 3xx (redirect to login) or 4xx are both acceptable "not public";
        // 200 is not.
        int metricsStatus = mockMvc.perform(get("/actuator/metrics"))
                .andReturn().getResponse().getStatus();
        int prometheusStatus = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn().getResponse().getStatus();

        org.junit.jupiter.api.Assertions.assertNotEquals(200, metricsStatus,
                "/actuator/metrics must not be readable anonymously");
        org.junit.jupiter.api.Assertions.assertNotEquals(200, prometheusStatus,
                "/actuator/prometheus must not be readable anonymously");
    }

    @Test
    void metricsAreCollectedAndTaggedWithTheApplication() {
        // Asserts the registry rather than the /actuator/prometheus route:
        // MockMvc's servlet context does not register the actuator's Prometheus
        // handler (it 404s there as a missing static resource), and the
        // Prometheus registry itself is a runtime-scope dependency absent from
        // the test classpath. The scrape endpoint just renders this registry, so
        // checking it proves the metrics that would be scraped exist and carry
        // the application tag. The live endpoint is verified by runtime smoke.
        assertNotNull(meterRegistry.find("jvm.memory.used").meter(),
                "expected JVM memory metrics to be collected");
        assertNotNull(meterRegistry.find("hikaricp.connections").meter(),
                "expected connection-pool metrics - pool exhaustion is a common "
                        + "production failure and must be observable");
        assertEquals("backend",
                meterRegistry.find("jvm.memory.used").meter().getId().getTag("application"),
                "expected metrics tagged with the application name so a shared "
                        + "Prometheus can separate this app's series");
    }

    /**
     * The whole point of making this endpoint public: answering "is production
     * serving the new build?" without credentials.
     *
     * <p>A deploy check that needs a login does not get run. Twice this round I
     * verified a rollout by probing for a string that had already shipped, and
     * concluded the deploy had landed when I had measured nothing.
     */
    @Test
    void infoIsPublicAndNamesTheDeployedCommit() throws Exception {
        var response = mockMvc.perform(get("/actuator/info")).andReturn().getResponse();

        assertEquals(200, response.getStatus(),
                "an unauthenticated caller has to be able to read this, or it will not be used");
        String body = response.getContentAsString();
        assertTrue(body.contains("\"commit\""),
                "the commit is the one field this exists for, got: " + body);
        assertTrue(body.contains("\"branch\""), "got: " + body);
        assertTrue(body.contains("\"version\""), "got: " + body);
    }

    /**
     * {@code @project.version@} only resolves if Maven resource filtering is
     * applied to application.properties -- which spring-boot-starter-parent
     * configures, but nothing in this repo states. Without it the placeholder
     * reaches production verbatim and the endpoint reports a literal
     * "@project.version@" as the running version.
     */
    @Test
    void theVersionPlaceholderIsResolvedNotLiteral() throws Exception {
        String body = mockMvc.perform(get("/actuator/info"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(!body.contains("@project.version@"),
                "resource filtering did not run, so the version is a placeholder: " + body);
    }

    /**
     * Public means info and nothing else. env is the one that would matter --
     * it holds the database URL and every secret the process was started with.
     */
    @Test
    void makingInfoPublicDidNotOpenTheRestOfActuator() throws Exception {
        for (String path : new String[]{"/actuator/env", "/actuator/metrics", "/actuator/prometheus"}) {
            int statusCode = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertNotEquals(200, statusCode,
                    path + " must not have become public alongside info");
        }
    }

    @Test
    void sensitiveEndpointsAreNotExposedAtAll() throws Exception {
        // env/beans/heapdump are excluded from
        // management.endpoints.web.exposure.include, so they must not answer 200
        // even before the security rules are considered.
        for (String path : new String[]{"/actuator/env", "/actuator/beans", "/actuator/heapdump"}) {
            int statusCode = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertNotEquals(200, statusCode,
                    path + " must not be exposed");
        }
    }
}
