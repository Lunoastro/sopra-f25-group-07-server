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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;
import ch.uzh.ifi.hase.soprafs24.config.JpaDataStoreFactory;
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

    @Value("${REDIRECT_URI:redirect.uri}")
    private String redirectUri;

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
        
        // Default for local development only
        redirectUri = "http://localhost:8080/Callback";
        logger.info("Using default redirect URI: {}", redirectUri);
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
            logger.error("Error accessing Google Calendar for user {}: {}", userId, e.getMessage(), e);
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
                            .setDateTime(new DateTime(toISOString(task.getStartDate()))))
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
        GoogleAuthorizationCodeFlow flow = getFlow();

        var tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(getRedirectUri())
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

    public Credential getUserCredentials(Long userId) throws IOException, GeneralSecurityException {
        GoogleToken googleToken = googleTokenRepository.findById(userId)
            .orElseThrow(() -> new IOException("No credentials found for user: " + userId));

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