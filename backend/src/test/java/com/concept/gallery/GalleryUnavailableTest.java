package com.concept.gallery;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.concept.media.MediaStore;
import com.concept.media.UnavailableMediaStore;

/**
 * A deployed server with no bucket: the gallery says photos are not set up, and
 * an upload is refused before anything is read or decoded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
class GalleryUnavailableTest {

    @TestConfiguration
    static class NoBucket {
        @Bean
        @Primary
        MediaStore unavailable() {
            return new UnavailableMediaStore();
        }
    }

    @Autowired private MockMvc mvc;

    @Test
    void theStatusSaysPhotosAreNotSetUp() throws Exception {
        mvc.perform(get("/api/gallery/status").with(user("anyone@example.com").roles("PARENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value(UnavailableMediaStore.NOT_SET_UP));
    }

    @Test
    void anUploadIsRefusedWithThatReason() throws Exception {
        mvc.perform(post("/api/gallery/albums/" + UUID.randomUUID() + "/photos")
                        .with(user("teacher@example.com").roles("TEACHER"))
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(new byte[] {1, 2, 3}))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(UnavailableMediaStore.NOT_SET_UP));
    }
}
