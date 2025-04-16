package ch.uzh.ifi.hase.soprafs24.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ch.uzh.ifi.hase.soprafs24.entity.Task;

@Service
public class CalendarService {
    private static final Logger logger = LoggerFactory.getLogger(CalendarService.class);
    private static final String APPLICATION_NAME = "TaskAway Calendar";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.home") + "/.taskaway_tokens";
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    @Value("${redirect.uri}")
    private String redirectUri;

    private GoogleAuthorizationCodeFlow flow;

    @Autowired
    private TaskService taskService;

    static {
        File tokenDir = new File(TOKENS_DIRECTORY_PATH);
        if (!tokenDir.exists()) {
            tokenDir.mkdirs();
            tokenDir.setReadable(true, true);
            tokenDir.setWritable(true, true);
            tokenDir.setExecutable(true, true);
        }
    }

    public List<Event> getUserGoogleCalendarEvents(int maxResults, Long userId) throws IOException {
        try {
            Calendar calendar = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    getUserCredentials(userId))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            DateTime now = new DateTime(System.currentTimeMillis());
            Events events = calendar.events().list("primary")
                    .setMaxResults(maxResults)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            return events.getItems();
        } catch (GeneralSecurityException e) {
            throw new IOException("Error accessing Google Calendar for user.", e);
        }
    }

    public void syncTaskWithGoogleCalendar(Long userId, Map<String, Object> taskEvent) {
        try {
            Calendar userCalendar = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    getUserCredentials(userId))
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            if ("task".equals(taskEvent.get("source"))) {
                Event event = new Event()
                        .setSummary((String) taskEvent.get("summary"))
                        .setDescription((String) taskEvent.get("description"))
                        .setStart(new EventDateTime().setDateTime(new DateTime((String) ((Map<?, ?>) taskEvent.get("start")).get("dateTime"))))
                        .setEnd(new EventDateTime().setDateTime(new DateTime((String) ((Map<?, ?>) taskEvent.get("end")).get("dateTime"))));

                if ("active".equals(taskEvent.get("status"))) {
                    Event inserted = userCalendar.events().insert("primary", event).execute();
                    System.out.println("Inserted Google event ID: " + inserted.getId());
                    // Store inserted.getId() in DB as needed
                } else if ("inactive".equals(taskEvent.get("status"))) {
                    String googleEventId = (String) taskEvent.get("googleEventId");
                    if (googleEventId != null) {
                        userCalendar.events().delete("primary", googleEventId).execute();
                    }
                }
            }
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error syncing task with Google Calendar for user {}: {}", userId, e.getMessage(), e);        }
    }

    public void syncAllActiveTasksToUserCalendar(Long userId) throws IOException {
        List<Task> tasks = taskService.getFilteredTasks(true, null);

        for (Task task : tasks) {
            Map<String, Object> taskEvent = new HashMap<>();
            taskEvent.put("id", "task-" + task.getId());
            taskEvent.put("summary", "[TASK] " + task.getName());
            taskEvent.put("description", task.getDescription());
            taskEvent.put("start", Map.of("dateTime", toISOString(task.getDeadline())));
            taskEvent.put("end", Map.of("dateTime", toISOString(task.getDeadline())));
            taskEvent.put("status", "active");

            syncTaskWithGoogleCalendar(userId, taskEvent);
        }
    }

    public String generateAuthUrl(Long userId) throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, getClientSecrets(), SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(userId.toString())
                .build();
    }

    public void handleOAuthCallback(String code, Long userId) throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        if (flow == null) {
            flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, getClientSecrets(), SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();
        }

        var tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        Credential credential = flow.createAndStoreCredential(tokenResponse, userId.toString());
        System.out.println("Google OAuth token saved for user: " + userId);
    }

    private GoogleClientSecrets getClientSecrets() throws IOException {
        String json = System.getenv("GOOGLE_CALENDAR_CREDENTIALS");

        if (json != null && !json.isEmpty()) {
            InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }

        throw new FileNotFoundException("Environment variable GOOGLE_CALENDAR_CREDENTIALS is not set.");
    }

    public Credential getUserCredentials(Long userId) throws IOException, GeneralSecurityException {
        NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, getClientSecrets(), SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();

        Credential credential = flow.loadCredential(userId.toString());
        if (credential == null) {
            throw new IOException("No credentials found for user: " + userId);
        }
        return credential;
    }

    public String toISOString(Date date) {
        return date != null ? date.toInstant().toString() : null;
    }

    private Calendar getCalendarServiceForUser(Long userId) throws IOException, GeneralSecurityException {
      final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();      
      Credential credential = getUserCredentials(userId);  // Loads user's stored credentials
      return new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
              .setApplicationName(APPLICATION_NAME)
              .build();
    }
  

    public Event getEventById(String eventId, Long userId) throws IOException, GeneralSecurityException {
      // Load the credential for the user (make sure you handle this part correctly!)
      Calendar calendar = getCalendarServiceForUser(userId);  // Your method for loading user's authorized Calendar service
  
      return calendar.events().get("primary", eventId).execute();
  }
}
