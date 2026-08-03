package com.piped.taskmaster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskMasterE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("E2E Test: Renders home page '/' with layout, JS and CSS directives, and pre-populated task list")
    void testHomePageRendering() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TaskMaster - Piped Template Engine")))
                .andExpect(content().string(containsString("Task Master")))
                .andExpect(content().string(containsString("<style>")))
                .andExpect(content().string(containsString(".app-title { font-weight: 700; }")))
                .andExpect(content().string(containsString("</style>")))
                .andExpect(content().string(containsString("<script>")))
                .andExpect(content().string(containsString("console.log(\"TaskMaster E2E Ready\");")))
                .andExpect(content().string(containsString("</script>")))
                .andExpect(content().string(containsString("Explore Piped Template Engine features")));
    }

    @Test
    @DisplayName("E2E Test: POST /tasks adds a new task and returns updated fragment")
    void testAddTaskEndToEnd() throws Exception {
        mockMvc.perform(post("/tasks").param("title", "Write E2E Integration Tests"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Write E2E Integration Tests")));
    }

    @Test
    @DisplayName("E2E Test: GET /demo file-based route renders successfully")
    void testFileBasedRouteEndToEnd() throws Exception {
        mockMvc.perform(get("/demo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PTE Modern Features Demo")));
    }
}
