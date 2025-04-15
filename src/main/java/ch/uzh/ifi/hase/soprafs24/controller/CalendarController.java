package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.service.CalendarService;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.entity.Task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.api.services.calendar.model.Event;

@RestController
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final UserService userService;
    private final TaskService taskService;

    @Autowired
    public CalendarController(CalendarService calendarService, TaskService taskService, UserService userService) {
        this.calendarService = calendarService;
        this.taskService = taskService;
        this.userService = userService;
    }
    

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    public String syncAllActiveTasks(@RequestHeader("Authorization") String authorizationHeader) throws IOException {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        // Sync active tasks to Google Calendar
        calendarService.syncAllActiveTasksToUserCalendar(userId);

        return "All active tasks synced to Google Calendar successfully!";
    }


    @GetMapping("/auth-url")
    @ResponseStatus(HttpStatus.OK)
    public String getGoogleAuthUrl(@RequestHeader("Authorization") String authorizationHeader) throws Exception {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }

        return calendarService.generateAuthUrl(userId);
    }

    @GetMapping("/Callback")
    @ResponseStatus(HttpStatus.OK)
    public String handleGoogleCallback(@RequestParam("code") String code, @RequestParam("state") String userIdStr) throws Exception {
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state/user ID.");
        }
        calendarService.handleOAuthCallback(code, userId);
        return "Google Calendar connected successfully!";
    }

    @GetMapping("/events")
    @ResponseStatus(HttpStatus.OK)
    public List<Event> getUpcomingEvents(@RequestParam(defaultValue = "10") int limit, @RequestHeader("Authorization") String authorizationHeader) throws IOException {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        return calendarService.getUserGoogleCalendarEvents(limit, userId);
    }

    @GetMapping("/events/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Event getEventById(@PathVariable String id, @RequestHeader("Authorization") String authorizationHeader) throws IOException {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        return calendarService.getEventById(id);
    }

    @GetMapping("/combined")
    @ResponseStatus(HttpStatus.OK)
    public List<Map<String, Object>> getCombinedCalendar(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "20") int limit, 
            @RequestHeader("Authorization") String authorizationHeader) throws IOException {

        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }

        List<Map<String, Object>> combinedEvents = new ArrayList<>();

        List<Event> googleEvents = calendarService.getUserGoogleCalendarEvents(limit, userId);
        for (Event ge : googleEvents) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ge.getId());
            map.put("summary", ge.getSummary());
            map.put("description", ge.getDescription());
            map.put("start", ge.getStart());
            map.put("end", ge.getEnd());
            map.put("source", "google");
            combinedEvents.add(map);
        }

        List<Task> tasks = taskService.getFilteredTasks(activeOnly, null);

        for (Task task : tasks) {
            Map<String, Object> taskEvent = new HashMap<>();
            taskEvent.put("id", "task-" + task.getId());
            taskEvent.put("summary", "[TASK] " + task.getName());
            taskEvent.put("description", task.getDescription());
            taskEvent.put("start", Map.of("dateTime", calendarService.toISOString(task.getDeadline())));
            taskEvent.put("end", Map.of("dateTime", calendarService.toISOString(task.getDeadline())));
            taskEvent.put("colorId", task.getColor() != null ? task.getColor().toString() : null);
            taskEvent.put("source", "task");
            taskEvent.put("status", "active");

            combinedEvents.add(taskEvent);
        }

        return combinedEvents;
    }

    private String validateAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty() || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing or invalid Authorization header.");
        }
        return authorizationHeader.substring(7);  // Remove "Bearer " prefix
    }
}
