package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.service.CalendarService;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.google.api.services.calendar.model.Event;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private TaskService taskService;

    @MockBean
    private CalendarService calendarService;

    private final String validToken = "valid_token";
    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void POST_syncAllActiveTasks_valid_returns200() throws Exception {
        // assuming token maps to userId = 1
        doNothing().when(calendarService).syncAllActiveTasksToUserCalendar(anyLong());
        // Also stub the userService mock
        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);

        mockMvc.perform(post("/calendar/sync")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isOk());

        verify(calendarService).syncAllActiveTasksToUserCalendar(1L);
    }

    @Test
    void GET_userCalendarEvents_valid_returns200() throws Exception {
        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);
        when(calendarService.getUserGoogleCalendarEvents(5, 1L))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/calendar/events")
                .param("limit", "5")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isOk());

        verify(calendarService).getUserGoogleCalendarEvents(5, 1L);
    }

    @Test
    void GET_handleOAuthCallback_valid_returns200() throws Exception {
        doNothing().when(calendarService).handleOAuthCallback("authcode", 1L);

        mockMvc.perform(get("/calendar/Callback")
                .param("code", "authcode")
                .param("state", "1"))
            .andExpect(status().isOk());

        verify(calendarService).handleOAuthCallback("authcode", 1L);
    }

    @Test
    void GET_generateAuthUrl_valid_returns200WithUrl() throws Exception {
        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);
        when(calendarService.generateAuthUrl(1L)).thenReturn("http://auth.url");

        mockMvc.perform(get("/calendar/auth-url")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isOk())
            .andExpect(content().string("http://auth.url"));

        verify(calendarService).generateAuthUrl(1L);
    }

    @Test
    void GET_generateAuthUrl_throwsException_returns500() throws Exception {
        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);
        when(calendarService.generateAuthUrl(1L))
            .thenThrow(new RuntimeException("Failed"));

        mockMvc.perform(get("/calendar/auth-url")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isInternalServerError());

        verify(calendarService).generateAuthUrl(1L);
    }

    @Test
    void GET_userCalendarEvents_throwsUnauthorized_returns401() throws Exception {
        // Simulate token validation failure
        when(userService.validateToken(validToken)).thenReturn(false);

        mockMvc.perform(get("/calendar/events")
                .param("userId", "1")
                .param("maxResults", "5")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isUnauthorized());

        // Verify that the calendar service method was not called, as the token validation failed
        verify(calendarService, never()).getUserGoogleCalendarEvents(anyInt(), anyLong());
    }   

    @Test
    void GET_eventById_valid_returns200() throws Exception {
        Event mockEvent = new Event();
        mockEvent.setId("eventId123");
        mockEvent.setSummary("Test Event");

        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);
        when(calendarService.getEventById("eventId123", 1L)).thenReturn(mockEvent);

        mockMvc.perform(get("/calendar/events/eventId123")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("eventId123"))
            .andExpect(jsonPath("$.summary").value("Test Event"));

        verify(calendarService).getEventById("eventId123", 1L);
    }

    @Test
    void GET_eventById_invalidEvent_returns404() throws Exception {
        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);
        when(calendarService.getEventById(anyString(), anyLong()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        mockMvc.perform(get("/calendar/events/invalidEventId")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isNotFound());

        verify(calendarService).getEventById("invalidEventId", 1L);
    }

    @Test
    void GET_combinedCalendar_valid_returns200() throws Exception {
        // Mock Google Calendar event data
        Map<String, Object> googleEventMap = new HashMap<>();
        googleEventMap.put("id", "event1");
        googleEventMap.put("summary", "Google Event");

        // Mock Task-as-event map
        Map<String, Object> taskEventMap = new HashMap<>();
        taskEventMap.put("id", "task-1");
        taskEventMap.put("summary", "[TASK] Task 1");
        taskEventMap.put("description", "[TASK] Task 1");

        List<Map<String, Object>> combinedEvents = List.of(googleEventMap, taskEventMap);

        // Mock service calls
        when(userService.validateToken(validToken)).thenReturn(true);
        when(userService.findIDforToken(validToken)).thenReturn(1L);
        when(calendarService.getCombinedEvents(1L, true, 10)).thenReturn(combinedEvents);

        // Perform the GET request to the combined calendar endpoint
        mockMvc.perform(get("/calendar/combined")
                .param("limit", "10")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].summary").value("Google Event"))
            .andExpect(jsonPath("$[1].summary").value("[TASK] Task 1"))
            .andExpect(jsonPath("$[1].description").value("[TASK] Task 1"));

        // Verify service delegation
        verify(calendarService).getCombinedEvents(1L, true, 10);
        verify(calendarService, never()).getUserGoogleCalendarEvents(anyInt(), anyLong());
    }


    @Test
    void GET_combinedCalendar_throwsUnauthorized_returns401() throws Exception {
        // Simulate token validation failure
        when(userService.validateToken(validToken)).thenReturn(false);

        mockMvc.perform(get("/calendar/combined")
                .param("limit", "10")
                .header("Authorization", bearer(validToken)))
            .andExpect(status().isUnauthorized());

        // Verify that the calendar and task service methods were not called, as the token validation failed
        verify(calendarService, never()).getUserGoogleCalendarEvents(anyInt(), anyLong());
        verify(taskService, never()).getFilteredTasks(anyBoolean(), any());
    }
}
