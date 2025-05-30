package ch.uzh.ifi.hase.soprafs24.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;
import ch.uzh.ifi.hase.soprafs24.config.JpaDataStoreFactory;
import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.exceptions.CalendarAuthorizationException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
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
    private static final String ENDDATE = "endDate";
    private static final String LOCATION = "location";

    @Value("${REDIRECT_URI:}")
    private String redirectUri;

    @Value("${frontend.url}")
    private String frontendUrl;

    private TaskService taskService;
    private final GoogleTokenRepository googleTokenRepository;

    @Autowired
    private JpaDataStoreFactory jpaDataStoreFactory;

    public CalendarService(@Lazy TaskService taskService, GoogleTokenRepository googleTokenRepository) {
        this.taskService = taskService;
        this.googleTokenRepository = googleTokenRepository;
    }

    protected GoogleAuthorizationCodeFlow getFlow() throws IOException, GeneralSecurityException {
        String json = System.getenv("GOOGLE_CALENDAR_CREDENTIALS");
        GoogleClientSecrets clientSecrets;

        if (json == null || json.isEmpty()) {
            logger.warn("GOOGLE_CALENDAR_CREDENTIALS environment variable is not set. Using local_credentials.json as fallback.");

            try (InputStream in = new FileInputStream("src/main/resources/local_credentials.json")) {
                clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            } catch (FileNotFoundException e) {
                logger.error("local_credentials.json not found. Google Calendar integration will not work.", e);
                throw new IllegalStateException("Missing both GOOGLE_CALENDAR_CREDENTIALS and local_credentials.json", e);
            }
        } else {
            clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                    new InputStreamReader(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        }

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        
        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                Collections.singletonList(CalendarScopes.CALENDAR))
                .setAccessType(OFFLINE)
                .setDataStoreFactory(jpaDataStoreFactory)
                .setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
                .build();
    }

    protected String getRedirectUri() {
        // First check if we already have a value set
        if (redirectUri != null && !redirectUri.isEmpty()) {
            return redirectUri;
        }
        
        // Try to get from environment variable
        String envRedirectUri = System.getenv("REDIRECT_URI");
        if (envRedirectUri != null && !envRedirectUri.isEmpty()) {
            redirectUri = envRedirectUri;
            return redirectUri;
        }
        
        // Try to get from system property
        String propRedirectUri = System.getProperty("redirect.uri");
        if (propRedirectUri != null && !propRedirectUri.isEmpty()) {
            redirectUri = propRedirectUri;
            return redirectUri;
        }
        logger.warn("Using local redirect_uri as fallback.");

        // Default for local development only
        redirectUri = "http://localhost:8080/Callback";
        return redirectUri;
    }

    public List<Map<String, Object>> getUserGoogleCalendarEvents(String startDate, String endDate, Long userId) throws IOException {
        try {
            Calendar calendar = getCalendarServiceForUser(userId);

            DateTime start = buildStartDateTime(startDate);
            DateTime end = buildExclusiveEndDateTime(endDate);

            List<Event> events = fetchEvents(calendar, start, end);

            LocalDate exclusiveEnd = LocalDate.parse(endDate).plusDays(1);

            return events.stream()
                    .filter(event -> isEventWithinRange(event, end, exclusiveEnd))
                    .map(this::simplifyEvent)
                    .collect(Collectors.toList());

        } catch (GeneralSecurityException e) {
            logger.error("Error accessing Google Calendar for user {}: {}", userId, e.getMessage(), e);
            throw new IOException("Error accessing Google Calendar for user.", e);
        }
    }

    private DateTime buildStartDateTime(String startDate) {
        return new DateTime(startDate + "T00:00:00Z");
    }

    private DateTime buildExclusiveEndDateTime(String endDate) {
        LocalDate endLocalDate = LocalDate.parse(endDate);
        LocalDate exclusiveEnd = endLocalDate.plusDays(1);
        return new DateTime(exclusiveEnd.toString() + "T00:00:00Z");
    }

    private List<Event> fetchEvents(Calendar calendar, DateTime start, DateTime end) throws IOException {
        Events events = calendar.events().list(PRIMARY)
                .setTimeMin(start)
                .setTimeMax(end)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        return events.getItems();
    }

    private boolean isEventWithinRange(Event event, DateTime end, LocalDate exclusiveEnd) {
        if (event.getStart().getDateTime() != null) {
            // Timed event
            return event.getStart().getDateTime().getValue() < end.getValue();
        } else if (event.getStart().getDate() != null) {
            // All-day event
            LocalDate eventStartDate = LocalDate.parse(event.getStart().getDate().toStringRfc3339());
            return eventStartDate.isBefore(exclusiveEnd);
        }
        return true; // fallback keep if neither set
    }

    private Map<String, Object> simplifyEvent(Event event) {
        Map<String, Object> simplified = new HashMap<>();
        simplified.put("id", event.getId());
        simplified.put("name", event.getSummary());
        simplified.put(DESCRIPTION, event.getDescription());
        simplified.put(ENDDATE, calculateDisplayEndDate(event));
        simplified.put(LOCATION, event.getLocation());
        return simplified;
    }

    private String calculateDisplayEndDate(Event event) {
        if (event.getStart() == null || event.getEnd() == null) {
            return null;
        }

        // All-day event
        if (event.getStart().getDate() != null && event.getEnd().getDate() != null) {
            LocalDate adjusted = LocalDate.parse(event.getEnd().getDate().toStringRfc3339()).minusDays(1);
            return adjusted.toString();
        }

        // Timed event
        if (event.getStart().getDateTime() != null && event.getEnd().getDateTime() != null) {
            DateTime endDateTime = event.getEnd().getDateTime();
            Instant endInstant = Instant.ofEpochMilli(endDateTime.getValue());
            ZonedDateTime zonedEnd = endInstant.atZone(ZoneId.systemDefault());

            // If ends exactly at midnight, use start date instead
            if (zonedEnd.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                Instant startInstant = Instant.ofEpochMilli(event.getStart().getDateTime().getValue());
                ZonedDateTime zonedStart = startInstant.atZone(ZoneId.systemDefault());
                return zonedStart.toLocalDate().toString();
            } else {
                return zonedEnd.toLocalDate().toString();
            }
        }
        return null;
    }


    public void syncSingleTask(Task task, Long userId) {
        try {
            Calendar cal = getCalendarServiceForUser(userId);

            LocalDate startDate = task.getStartDate().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
            LocalDate deadlineDate = task.getDeadline().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

            DateTime startDateTime = new DateTime(startDate.toString());
            DateTime endDateTime = new DateTime(deadlineDate.plusDays(1).toString());

            Event event = new Event()
                .setSummary("[TASK] " + task.getName())
                .setDescription(task.getDescription())
                .setStart(new EventDateTime().setDate(startDateTime))
                .setEnd(new EventDateTime().setDate(endDateTime));

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
            taskService.saveTask(task);

        } catch (IOException | GeneralSecurityException ex) {
            logger.warn("Google Calendar sync failed for task {} / user {}: {}", task.getId(), userId, ex.getMessage(), ex);        }
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
                    .setAccessType(OFFLINE)   // request refresh token
                    .set("prompt", "consent")
                    .build();
        } catch (Exception e) {
            logger.error("Failed to generate authorization URL for user {}: {}", userId, e.getMessage(), e);
            throw new CalendarAuthorizationException("Failed to generate authorization URL for user: " + userId, e);
        }
    }

    public void handleOAuthCallback(String code, Long userId) throws Exception {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Authorization code must not be null or empty");
        }
        
        GoogleAuthorizationCodeFlow flow = getFlow();

        var tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(getRedirectUri())
                .execute();

        // Store token in the database
        GoogleToken googleToken = googleTokenRepository.findGoogleTokenById(userId);
        if (googleToken == null) {
            googleToken = new GoogleToken();
            googleToken.setId(userId);
        }
        
        String refreshToken = tokenResponse.getRefreshToken();
        if (refreshToken != null) {
            googleToken.setRefreshToken(refreshToken);
        }

        if (tokenResponse.getAccessToken() == null) {
            logger.warn("Access token was null for user: {}", userId);
            throw new IllegalStateException("Access token must not be null.");
        }


        googleToken.setAccessToken(tokenResponse.getAccessToken());
        googleToken.setExpirationTime(System.currentTimeMillis() + (tokenResponse.getExpiresInSeconds() * 1000));

        googleTokenRepository.save(googleToken);

        logger.info("Google OAuth token saved for user: {}", userId);
    }

    public Credential getUserCredentials(Long userId) throws IOException, GeneralSecurityException {
        GoogleToken googleToken = googleTokenRepository.findGoogleTokenById(userId);
        if (googleToken == null) {
            logger.warn("No Google token found for user: {}", userId);
            throw new IOException("No credentials found for user: " + userId);
        }

        GoogleAuthorizationCodeFlow flow = getFlow();

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
            .setTransport(flow.getTransport())
            .setJsonFactory(flow.getJsonFactory())
            .setTokenServerEncodedUrl(flow.getTokenServerEncodedUrl())
            .setClientAuthentication(flow.getClientAuthentication())
            .build();

        // Set tokens from DB
        credential.setAccessToken(googleToken.getAccessToken());
        credential.setRefreshToken(googleToken.getRefreshToken());
        credential.setExpirationTimeMilliseconds(googleToken.getExpirationTime());

        // Check if access token is missing or about to expire
        if (credential.getAccessToken() == null || (credential.getExpirationTimeMilliseconds() != null && credential.getExpirationTimeMilliseconds() <= System.currentTimeMillis() + 60000)) {
            logger.info("Access token expired or missing for user {}, refreshing token...", userId);
            boolean refreshed = credential.refreshToken();
            if (!refreshed) {
                throw new IOException("Failed to refresh token for user: " + userId);
            }

            // Update refreshed tokens in DB
            googleToken.setAccessToken(credential.getAccessToken());
            googleToken.setExpirationTime(credential.getExpirationTimeMilliseconds());
            googleTokenRepository.save(googleToken);
        }

        return credential;
    }


    @VisibleForTesting
    public Calendar getCalendarServiceForUser(Long userId) throws IOException, GeneralSecurityException {
        Credential cred = getUserCredentials(userId);
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        return new Calendar.Builder(httpTransport, JSON_FACTORY, cred)
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
        List<Map<String, Object>> googleEvents = getUserGoogleCalendarEvents(startDate, endDate, userId);
        for (Map<String, Object> ge : googleEvents) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", ge.get("id"));
            map.put("name", ge.get("name"));
            map.put(DESCRIPTION, ge.get(DESCRIPTION));
            map.put(ENDDATE, ge.get(ENDDATE));
            map.put(LOCATION, ge.get(LOCATION));
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
            taskEvent.put("name", "[TASK] " + task.getName());
            taskEvent.put(DESCRIPTION, task.getDescription());
            taskEvent.put(ENDDATE, toDateOnly(task.getDeadline() != null ? task.getDeadline().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null));  // helper function to format deadline
            taskEvent.put(LOCATION, null);
            taskEvent.put("colorId", task.getColor() != null ? task.getColor().toString() : null);
            taskEvent.put(SOURCE, "task");
            taskEvent.put(STATUS_FIELD, "active");
    
            combinedEvents.add(taskEvent);
        }
        return combinedEvents;
    }

    private boolean isWithinRange(Date deadline, String startDateStr, String endDateStr) {
        try {
            if (deadline == null) {
                return false;
            }

            LocalDate deadlineDate = deadline.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate = LocalDate.parse(endDateStr);

            return ( !deadlineDate.isBefore(startDate) && !deadlineDate.isAfter(endDate) );
        } catch (Exception e) {
            return false;
        }
    }


    public boolean isGoogleTokenValid(GoogleToken googleToken) {
        return googleToken != null && googleToken.getAccessToken() != null && !googleToken.getAccessToken().isEmpty();
    }

    public String getRedirectURL(Long teamId) {
        if (teamId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found for user.");
        }
        return frontendUrl + teamId;
    }

    private String toDateOnly(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toLocalDate().toString(); // format: yyyy-MM-dd
    }

    
}