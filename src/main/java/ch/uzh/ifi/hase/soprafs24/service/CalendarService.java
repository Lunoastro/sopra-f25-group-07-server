package ch.uzh.ifi.hase.soprafs24.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
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
import java.security.GeneralSecurityException;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ch.uzh.ifi.hase.soprafs24.entity.Task; // Adjust the package path if necessary

/* class to demonstrate use of Calendar events list API */
@Service
public class CalendarService {
  //Application name.
  private static final String APPLICATION_NAME = "TaskAway Calendar";
  
  //Global instance of the JSON factory.
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  
  //Directory to store authorization tokens for this application.
  private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.home") + "/.taskaway_tokens";

  static {
    File tokenDir = new File(TOKENS_DIRECTORY_PATH);
    if (!tokenDir.exists()) {
        tokenDir.mkdirs();
        tokenDir.setReadable(true, true);   // only owner can read
        tokenDir.setWritable(true, true);   // only owner can write
        tokenDir.setExecutable(true, true); // needed to access the directory
    }
  }
  
  //Global instance of the scopes required by this quickstart.
  //If modifying these scopes, delete your previously saved tokens/ folder.
  private static final List<String> SCOPES =
      Collections.singletonList(CalendarScopes.CALENDAR);

  private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

  private static final String REDIRECT_URI = "http://localhost:8080/calendar/callback"; // update with prod URL if needed

  private GoogleAuthorizationCodeFlow flow; // reused later

  @Autowired
  private TaskService taskService;
  private final Calendar defaultCalendarService;

  public CalendarService() throws GeneralSecurityException, IOException {
      final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      this.defaultCalendarService = new Calendar.Builder(
              HTTP_TRANSPORT, JSON_FACTORY, getDefaultCredentials(HTTP_TRANSPORT))
              .setApplicationName(APPLICATION_NAME)
              .build();
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

  public Event getEventById(String eventId) throws IOException {
    return defaultCalendarService.events().get("primary", eventId).execute();
  }

  public void syncTaskWithGoogleCalendar(Long userId, Map<String, Object> taskEvent) {
    try {
        Calendar userCalendar = new Calendar.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            getUserCredentials(userId)
        ).setApplicationName(APPLICATION_NAME).build();

        if ("task".equals(taskEvent.get("source"))) {
            Event event = new Event()
                    .setSummary((String) taskEvent.get("summary"))
                    .setDescription((String) taskEvent.get("description"))
                    .setStart(new EventDateTime().setDateTime(new DateTime((String) ((Map<?, ?>) taskEvent.get("start")).get("dateTime"))))
                    .setEnd(new EventDateTime().setDateTime(new DateTime((String) ((Map<?, ?>) taskEvent.get("end")).get("dateTime"))));

            if ("active".equals(taskEvent.get("status"))) {
                Event inserted = userCalendar.events().insert("primary", event).execute();
                System.out.println("Inserted Google event ID: " + inserted.getId());
                // Store the Google event ID linked to the task in the database
            } else if ("inactive".equals(taskEvent.get("status"))) {
                String googleEventId = (String) taskEvent.get("googleEventId"); // stored in the DB
                if (googleEventId != null) {
                    userCalendar.events().delete("primary", googleEventId).execute();
                }
            }
        }
    } catch (IOException | GeneralSecurityException e) {
        e.printStackTrace();
    }
}

  public void syncAllActiveTasksToUserCalendar(Long userId) throws IOException {
    // Retrieve all active tasks
    List<Task> tasks = taskService.getFilteredTasks(true, null);
    
    // Sync each task to Google Calendar for the user
    for (Task task : tasks) {
        Map<String, Object> taskEvent = new HashMap<>();
        taskEvent.put("id", "task-" + task.getId());
        taskEvent.put("summary", "[TASK] " + task.getName());
        taskEvent.put("description", task.getDescription());
        taskEvent.put("start", Map.of("dateTime", toISOString(task.getDeadline())));
        taskEvent.put("end", Map.of("dateTime", toISOString(task.getDeadline())));
        taskEvent.put("status", "active");

        // Sync the task with the user's Google Calendar
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
            .setRedirectUri(REDIRECT_URI)
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
            .setRedirectUri(REDIRECT_URI)
            .execute();

    Credential credential = flow.createAndStoreCredential(tokenResponse, userId.toString());
    System.out.println("Google OAuth token saved for user: " + userId);
  }

  private GoogleClientSecrets getClientSecrets() throws IOException {
    InputStream in = CalendarService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
    if (in == null) {
        throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
    }

    return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
  }

  public Calendar getDefaultUserGoogleCalendar() throws GeneralSecurityException, IOException {
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Calendar.Builder(
          HTTP_TRANSPORT, JSON_FACTORY, getDefaultCredentials(HTTP_TRANSPORT))
          .setApplicationName(APPLICATION_NAME)
          .build();
  }

  public Credential getUserCredentials(Long userId) throws IOException, GeneralSecurityException {
    NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH));
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, getClientSecrets(), SCOPES)
            .setDataStoreFactory(dataStoreFactory)
            .setAccessType("offline")
            .build();
    
    // Use userId to store and retrieve user-specific credentials
    Credential credential = flow.loadCredential(userId.toString());
    if (credential == null) {
        throw new IOException("No credentials found for user: " + userId);
    }
    return credential;
  }
  
  private static Credential getDefaultCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
    InputStream in = CalendarService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
    if (in == null) {
        throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
    }

    GoogleClientSecrets clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(8888).build()).authorize("user");
}

  public String toISOString(Date date) {
    return date != null ? date.toInstant().toString() : null;
  }
}