package ch.uzh.ifi.hase.soprafs24.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;
import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.exceptions.CalendarAuthorizationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class CalendarService {
    private static final Logger logger = LoggerFactory.getLogger(CalendarService.class);
    private static final String APPLICATION_NAME = "TaskAway Calendar";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String PRIMARY = "primary";  // Constant for "primary"
    private static final String OFFLINE = "offline";  // Constant for "offline"
    private static final String DATETIME_FIELD = "dateTime";
    private static final String STATUS_FIELD = "status"; // Constant for "status"
    private static final String START = "start"; // Constant for "start"
    private static final String END = "end"; // Constant for "end"
    private static final String SOURCE = "source"; // Constant for "source"
    private static final String SUMMARY = "summary"; // Constant for "summary"
    private static final String DESCRIPTION = "description"; // Constant for "description"

    @Value("${REDIRECT_URI:redirect.uri}")
    private String redirectUri;

    private TaskService taskService;
    private final GoogleTokenRepository googleTokenRepository;

    public CalendarService(@Lazy TaskService taskService, GoogleTokenRepository googleTokenRepository) {
        this.taskService = taskService;
        this.googleTokenRepository = googleTokenRepository;
    }

    protected GoogleAuthorizationCodeFlow getFlow() throws IOException, GeneralSecurityException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                getClientSecrets(),
                Collections.singletonList(CalendarScopes.CALENDAR))
                .setAccessType(OFFLINE)
                .build();
    }

    protected String getRedirectUri() {
        return redirectUri;
    }

    public List<Event> getUserGoogleCalendarEvents(String startDate, String endDate, Long userId) throws IOException {
        try {
            Calendar calendar = getCalendarServiceForUser(userId);
            DateTime start = new DateTime(startDate + "T00:00:00Z");
            DateTime end = new DateTime(endDate + "T23:59:59Z");
            Events events = calendar.events().list(PRIMARY)
                    .setTimeMin(start)
                    .setTimeMax(end)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            return events.getItems();
        } catch (GeneralSecurityException e) {
            throw new IOException("Error accessing Google Calendar for user.", e);
        }
    }

    public void syncSingleTask(Task task, Long userId) {
        try {
            Calendar cal = getCalendarServiceForUser(userId);

            Event event = new Event()
                    .setSummary("[TASK] " + task.getName())
                    .setDescription(task.getDescription())
                    .setStart(new EventDateTime()
                            .setDateTime(new DateTime(toISOString(task.getDeadline()))))
                    .setEnd(new EventDateTime()
                            .setDateTime(new DateTime(toISOString(task.getDeadline()))));

            if (Boolean.TRUE.equals(task.getActiveStatus())) {
                if (task.getGoogleEventId() == null) {
                    Event inserted = cal.events()
                                         .insert(PRIMARY, event)
                                         .execute();
                    task.setGoogleEventId(inserted.getId());
                } else {
                    cal.events()
                       .update(PRIMARY, task.getGoogleEventId(), event)
                       .execute();
                }
            } else if (task.getGoogleEventId() != null) {
                cal.events().delete(PRIMARY, task.getGoogleEventId()).execute();
                task.setGoogleEventId(null);
            }

        } catch (IOException | GeneralSecurityException ex) {
            logger.error("Google Calendar sync failed for task {} / user {}: {}", task.getId(), userId, ex.getMessage());
            throw new RuntimeException("Failed to sync with Google Calendar", ex);
        }
    }

    public void syncTaskWithGoogleCalendar(Long userId, Map<String, Object> taskEvent) {
        try {
            Calendar userCalendar = getCalendarServiceForUser(userId);

            if ("task".equals(taskEvent.get(SOURCE))) {
                Event event = new Event()
                        .setSummary((String) taskEvent.get(SUMMARY))
                        .setDescription((String) taskEvent.get(DESCRIPTION))
                        .setStart(new EventDateTime().setDateTime(new DateTime((String) ((Map<?, ?>) taskEvent.get(START)).get(DATETIME_FIELD))))
                        .setEnd(new EventDateTime().setDateTime(new DateTime((String) ((Map<?, ?>) taskEvent.get(END)).get(DATETIME_FIELD))));

                if ("active".equals(taskEvent.get(STATUS_FIELD))) {
                    Event inserted = userCalendar.events().insert(PRIMARY, event).execute();
                    logger.info("Inserted Google event ID: {}", inserted.getId());
                } else if ("inactive".equals(taskEvent.get(STATUS_FIELD))) {
                    String googleEventId = (String) taskEvent.get("googleEventId");
                    if (googleEventId != null) {
                        userCalendar.events().delete(PRIMARY, googleEventId).execute();
                    }
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error syncing task with Google Calendar for user {}: {}", userId, e.getMessage(), e);
        }
    }

    public void syncAllActiveTasksToUserCalendar(Long userId) {
        taskService.getFilteredTasks(true, null)
                   .forEach(t -> {
                       syncSingleTask(t, userId);
                       taskService.saveTask(t);   // Save task to DB
                   });
    }

    public String generateAuthUrl(Long userId) throws CalendarAuthorizationException {
        try {
            GoogleAuthorizationCodeFlow flow = getFlow();

            return flow.newAuthorizationUrl()
                    .setRedirectUri(getRedirectUri())
                    .setState(userId.toString())
                    .build();
        } catch (Exception e) {
            throw new CalendarAuthorizationException("Failed to generate authorization URL for user: " + userId, e);
        }
    }

    public void handleOAuthCallback(String code, Long userId) throws Exception {
        GoogleAuthorizationCodeFlow flow = getFlow();

        var tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        // Store token in the database
        GoogleToken googleToken = googleTokenRepository.findById(userId)
                .orElseGet(() -> {
                    GoogleToken newToken = new GoogleToken();
                    newToken.setId(userId);
                    return newToken;
                });

        googleToken.setAccessToken(tokenResponse.getAccessToken());
        googleToken.setRefreshToken(tokenResponse.getRefreshToken());
        googleToken.setExpirationTime(System.currentTimeMillis() + (tokenResponse.getExpiresInSeconds() * 1000));

        googleTokenRepository.save(googleToken);
        logger.info("Google OAuth token saved for user: {}", userId);
    }

    private GoogleClientSecrets getClientSecrets() throws IOException {
        String json = System.getenv("GOOGLE_CALENDAR_CREDENTIALS");

        if (json != null && !json.isEmpty()) {
            InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        throw new FileNotFoundException("Environment variable GOOGLE_CALENDAR_CREDENTIALS is not set.");
    }

    public Credential getUserCredentials(Long userId) throws IOException {
        // Retrieve the token from DB
        GoogleToken googleToken = googleTokenRepository.findById(userId)
                .orElseThrow(() -> new IOException("No credentials found for user: " + userId));

        // Create a Credential object from the stored token information
        return new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(googleToken.getAccessToken())
                .setRefreshToken(googleToken.getRefreshToken())
                .setExpirationTimeMilliseconds(googleToken.getExpirationTime());
    }

    public Credential refreshUserCredentials(Long userId) throws IOException {
        GoogleToken googleToken = googleTokenRepository.findById(userId)
                .orElseThrow(() -> new IOException("No credentials found for user: " + userId));
    
        // If the token is expired, refresh it
        if (System.currentTimeMillis() > googleToken.getExpirationTime()) {
            // Create the Credential object from the current token
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(googleToken.getAccessToken())
                    .setRefreshToken(googleToken.getRefreshToken())
                    .setExpirationTimeMilliseconds(googleToken.getExpirationTime());
    
            // Refresh the token
            if (credential.refreshToken()) {
                googleToken.setAccessToken(credential.getAccessToken());
                googleToken.setExpirationTime(System.currentTimeMillis() + (credential.getExpiresInSeconds() * 1000));
                googleTokenRepository.save(googleToken);
            }
    
            return credential;  // Return the updated Credential
        }
    
        // Return existing credentials if they are not expired
        return new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(googleToken.getAccessToken())
                .setRefreshToken(googleToken.getRefreshToken())
                .setExpirationTimeMilliseconds(googleToken.getExpirationTime());
    }

    @VisibleForTesting
    public Calendar getCalendarServiceForUser(Long userId) throws IOException, GeneralSecurityException {
        Credential cred = getUserCredentials(userId);
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                cred)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public Event getEventById(String eventId, Long userId) throws IOException, GeneralSecurityException {
        Calendar calendar = getCalendarServiceForUser(userId);
        return calendar.events().get(PRIMARY, eventId).execute();
    }

    public String toISOString(Date date) {
        // Utility method to convert Date to ISO string
        return new DateTime(date).toStringRfc3339();
    }

    public List<Map<String, Object>> getCombinedEvents(Long userId, boolean activeOnly, String startDate, String endDate) throws IOException {
        List<Map<String, Object>> combinedEvents = new ArrayList<>();
    
        // Fetch Google Calendar events
        List<Event> googleEvents = getUserGoogleCalendarEvents(startDate, endDate, userId);
        for (Event ge : googleEvents) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ge.getId());
            map.put(SUMMARY, ge.getSummary());
            map.put(DESCRIPTION, ge.getDescription());
            map.put(START, ge.getStart());
            map.put(END, ge.getEnd());
            map.put(SOURCE, "google");
            combinedEvents.add(map);
        }
    
        // Fetch tasks (filtered by active status if needed)
        List<Task> tasks = taskService.getFilteredTasks(activeOnly, null);  // Ensure taskService handles 'activeOnly'
        for (Task task : tasks) {
            if (!isWithinRange(task.getDeadline(), startDate, endDate)) {
                continue; // skip tasks outside of date range
            }
            Map<String, Object> taskEvent = new HashMap<>();
            taskEvent.put("id", "task-" + task.getId());
            taskEvent.put(SUMMARY, "[TASK] " + task.getName());
            taskEvent.put(DESCRIPTION, task.getDescription());
    
            // Convert task deadline to Google Calendar-compatible format
            taskEvent.put(START, Map.of(DATETIME_FIELD, toISOString(task.getDeadline())));
            taskEvent.put(END, Map.of(DATETIME_FIELD, toISOString(task.getDeadline())));
    
            // Optional: Add task color
            taskEvent.put("colorId", task.getColor() != null ? task.getColor().toString() : null);
            taskEvent.put(SOURCE, "task");
            taskEvent.put(STATUS_FIELD, "active");
    
            combinedEvents.add(taskEvent);
        }
        return combinedEvents;
    }

    private boolean isWithinRange(Date deadline, String startDateStr, String endDateStr) {
        try {
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            Date start = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            // Use end of day for endDate (23:59:59.999)
            Date end = Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

            return !deadline.before(start) && deadline.before(end); 
            // Note: using '< end' because 'end' is actually start of next day
        } catch (Exception e) {
            return false;
        }
    }
    
}