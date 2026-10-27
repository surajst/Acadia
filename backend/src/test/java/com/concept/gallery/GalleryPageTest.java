package com.concept.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Photos page: signed-in only, one page for every role, and staff controls
 * offered only to staff. What each person may actually do is the API's call
 * (GalleryApiTest); this is about the page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
class GalleryPageTest {

    @Autowired private MockMvc mvc;

    @Test
    void someoneNotSignedInIsSentToLogIn() throws Exception {
        String location = mvc.perform(get("/web/gallery"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).contains("/login");
    }

    @Test
    void aParentGetsThePageWithoutStaffControls() throws Exception {
        String html = mvc.perform(get("/web/gallery").with(user("parent@example.com").roles("PARENT")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-staff=\"false\"").doesNotContain("id=\"newAlbumForm\"");
    }

    @Test
    void aTeacherGetsTheNewAlbumForm() throws Exception {
        String html = mvc.perform(get("/web/gallery").with(user("teacher@example.com").roles("TEACHER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-staff=\"true\"").contains("id=\"newAlbumForm\"");
    }
}
