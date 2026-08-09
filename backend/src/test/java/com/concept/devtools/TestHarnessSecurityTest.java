package com.concept.devtools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With dev-mode off (production), the /test/reset dev seeder must refuse with a
 * real 403 — not be masked as a 200 by the global exception handler. Locks the
 * fix to GlobalExceptionHandler that stopped it rewriting every error to 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.dev-mode=false",
        // A real (non-default) secret so JwtUtils doesn't refuse to start with dev-mode off.
        "app.jwt-secret=test-only-but-not-the-insecure-default-secret-0123456789"
})
public class TestHarnessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testResetIsForbiddenWhenDevModeOff() throws Exception {
        mockMvc.perform(get("/test/reset"))
                .andExpect(status().isForbidden());
    }
}
