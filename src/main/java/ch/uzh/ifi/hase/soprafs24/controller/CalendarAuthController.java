package ch.uzh.ifi.hase.soprafs24.controller;

import ch.uzh.ifi.hase.soprafs24.service.CalendarService;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.entity.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.servlet.view.RedirectView;


@RestController
public class CalendarAuthController {

    private final CalendarService calendarService;
    private final UserService userService;

    @Autowired
    public CalendarAuthController(CalendarService calendarService, UserService userService) {
        this.calendarService = calendarService;
        this.userService = userService;
    }
    

    @GetMapping("/today")
    public ResponseEntity<String> getTodayDate() {
        String today = LocalDate.now().toString(); // e.g., "2025-05-16"
        return ResponseEntity.ok(today);
    }

    @GetMapping("/calendar/auth-url")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> getGoogleAuthUrl(@RequestHeader("Authorization") String authorizationHeader) throws Exception {
        String token = validateAuthorizationHeader(authorizationHeader);
        if (!userService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Invalid token.");
        }
        // Get authenticated user ID
        Long userId = userService.findIDforToken(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        try {
            String authURL = calendarService.generateAuthUrl(userId);
            return Collections.singletonMap("authUrl", authURL);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Google Calendar integration is not enabled.");
        }
    }

    @GetMapping("/Callback")
    public RedirectView handleGoogleCallback(@RequestParam("code") String code, @RequestParam("state") String userIdStr) throws Exception {

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state/user ID.");
        }
        calendarService.handleOAuthCallback(code, userId);

        User user = userService.getUserById(userId); // <-- you may need to implement this
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        Long teamId = user.getTeamId(); // Assuming User has a getTeamId() method
        
        // Redirect to frontend calendar page
        return new RedirectView(calendarService.getRedirectURL(teamId));
    }

    private String validateAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty() || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing or invalid Authorization header.");
        }
        return authorizationHeader.substring(7);  // Remove "Bearer " prefix
    }
}
