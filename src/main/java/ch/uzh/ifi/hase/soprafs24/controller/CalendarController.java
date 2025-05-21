package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;
import ch.uzh.ifi.hase.soprafs24.service.CalendarService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.TeamService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.List;
import com.google.api.services.calendar.model.Event;
import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;


@RestController
public class CalendarController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CalendarController.class);

    private final CalendarService calendarService;
    private final UserService userService;
    private final TeamService teamService;
    private final GoogleTokenRepository googleTokenRepository;
    

    @Autowired
    public CalendarController(CalendarService calendarService, UserService userService, TeamService teamService, GoogleTokenRepository googleTokenRepository) {
        this.calendarService = calendarService;
        this.userService = userService;
        this.teamService = teamService;
        this.googleTokenRepository = googleTokenRepository;
    }
    

    @PostMapping("/calendar/sync")
    @ResponseStatus(HttpStatus.OK)
    public String syncAllActiveTasks(@RequestHeader("Authorization") String authorizationHeader) {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Validate if the team is paused
        teamService.validateTeamPaused(token);
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        requireSyncedGoogleAccount(userId);
        // Sync active tasks to Google Calendar
        calendarService.syncAllActiveTasksToUserCalendar(userId);

        return "All active tasks synced to Google Calendar successfully!";
    }

    @GetMapping("/calendar/events")
    @ResponseStatus(HttpStatus.OK)
    public List<Map<String, Object>> getEventsInRange(@RequestParam String startDate, // Format: "YYYY-MM-DD"
                                        @RequestParam String endDate,  // Format: "YYYY-MM-DD"
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
        // Check if user has a linked Google token
        requireSyncedGoogleAccount(userId);

        // Optionally validate the Google token here if you want to check expiration or validity before calling calendar service
        GoogleToken googleToken = googleTokenRepository.findGoogleTokenById(userId);
        
        logger.info("Received startDate: {}", startDate);
        logger.info("Received endDate: {}", endDate);
        logger.info("Received userId: {}", userId);
        logger.info("Received googleToken: {}", googleToken);
        
        return calendarService.getUserGoogleCalendarEvents(startDate, endDate, userId);
    }

    @GetMapping("/calendar/events/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Event getEventById(@PathVariable String id, @RequestHeader("Authorization") String authorizationHeader) throws IOException, GeneralSecurityException {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        requireSyncedGoogleAccount(userId);
        return calendarService.getEventById(id, userId);
    }

    @GetMapping("/calendar/combined")
    @ResponseStatus(HttpStatus.OK)
    public List<Map<String, Object>> getCombinedCalendar(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam String startDate, // Format: "YYYY-MM-DD"
            @RequestParam String endDate,  // Format: "YYYY-MM-DD"
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
        requireSyncedGoogleAccount(userId);

        return calendarService.getCombinedEvents(userId, activeOnly, startDate, endDate);
    }


    private String validateAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty() || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing or invalid Authorization header.");
        }
        return authorizationHeader.substring(7);  // Remove "Bearer " prefix
    }

    private void requireSyncedGoogleAccount(Long userId) {
        GoogleToken token = googleTokenRepository.findGoogleTokenById(userId);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Google Calendar not synced for user");
        }  
        if (!calendarService.isGoogleTokenValid(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Google token expired or invalid. Please reconnect your Google account.");
        }
    }
}
