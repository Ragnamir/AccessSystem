package com.example.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.webui.service.CheckpointService;
import com.example.webui.service.DenialService;
import com.example.webui.service.EventService;
import com.example.webui.service.PageResult;
import com.example.webui.service.UserService;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckpointService checkpointService;

    @MockBean
    private UserService userService;

    @MockBean
    private EventService eventService;

    @MockBean
    private DenialService denialService;

    @Test
    void shouldReturnIndexPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void shouldReturnCheckpointsPage() throws Exception {
        when(checkpointService.findAll(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(Collections.emptyList(), 0L, 0, 20));

        mockMvc.perform(get("/checkpoints"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkpoints"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void shouldReturnUsersPage() throws Exception {
        when(userService.findAllWithState(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(Collections.emptyList(), 0L, 0, 20));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("users"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void shouldReturnEventsPage() throws Exception {
        when(eventService.findRecent(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(Collections.emptyList(), 0L, 0, 20));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(view().name("events"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void shouldReturnDenialsPage() throws Exception {
        when(denialService.findAll(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(Collections.emptyList(), 0L, 0, 20));

        mockMvc.perform(get("/denials"))
                .andExpect(status().isOk())
                .andExpect(view().name("denials"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void shouldSupportPaginationParameters() throws Exception {
        when(checkpointService.findAll(1, 10))
                .thenReturn(new PageResult<>(Collections.emptyList(), 0L, 1, 10));

        mockMvc.perform(get("/checkpoints?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkpoints"));
    }
}

