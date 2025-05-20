package ch.uzh.ifi.hase.soprafs24.service.Calendar;

import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;
import ch.uzh.ifi.hase.soprafs24.service.CalendarService;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CalendarServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private GoogleAuthorizationCodeFlow mockFlow;

    @Mock
    private GoogleTokenRepository tokenRepository;

    CalendarService calendarService;
    CalendarService spyService;

    // Subclass of CalendarService that overrides protected getFlow()
    class TestableCalendarService extends CalendarService {

        private final GoogleAuthorizationCodeFlow flow;
        private final String redirectUri;

        public TestableCalendarService(TaskService taskService, 
                                    GoogleTokenRepository tokenRepository, 
                                    GoogleAuthorizationCodeFlow flow,
                                    String redirectUri) {
            super(taskService, tokenRepository);
            this.flow = flow;
            this.redirectUri = redirectUri;
        }

        @Override
        protected GoogleAuthorizationCodeFlow getFlow() {
            return flow;
        }

        @Override
        protected String getRedirectUri() {
            return redirectUri;
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reset(taskService, tokenRepository);

        System.setProperty("GOOGLE_CALENDAR_CREDENTIALS", "{\"installed\":{\"client_id\":\"dummy\",\"client_secret\":\"dummy\"}}");

        // Use the subclass that injects mockFlow to override getFlow()
        calendarService = new TestableCalendarService(taskService, tokenRepository, mockFlow, "http://localhost/redirect");
        spyService = Mockito.spy(calendarService);
    }

    @Test
    void testGetUserGoogleCalendarEvents_success() throws Exception {
        Long userId = 1L;
        String startDate = "2025-05-01";
        String endDate = "2025-05-10";

        GoogleToken token = new GoogleToken();
        token.setId(userId);
        token.setAccessToken("access");
        token.setRefreshToken("refresh");
        token.setExpirationTime(System.currentTimeMillis() + 10000);

        when(tokenRepository.findById(userId)).thenReturn(Optional.of(token));

        // Mock Google Calendar API chain calls
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.List eventsList = mock(Calendar.Events.List.class);

        when(calendar.events()).thenReturn(events);
        when(events.list("primary")).thenReturn(eventsList);

        // Mock chained setters to return the same object
        when(eventsList.setTimeMin(any())).thenReturn(eventsList);
        when(eventsList.setTimeMax(any())).thenReturn(eventsList);
        when(eventsList.setOrderBy("startTime")).thenReturn(eventsList);
        when(eventsList.setSingleEvents(true)).thenReturn(eventsList);

        // Mock the execute method to return a real Events object with your test event
        Event event = new Event()
            .setSummary("Test Event")
            .setStart(new EventDateTime().setDate(new com.google.api.client.util.DateTime("2025-05-01")))
            .setEnd(new EventDateTime().setDate(new com.google.api.client.util.DateTime("2025-05-10")));
        Events eventsResponse = new Events();
        eventsResponse.setItems(Collections.singletonList(event));

        when(eventsList.execute()).thenReturn(eventsResponse);

        // Spy and override the getCalendarServiceForUser to return mocked calendar
        doReturn(calendar).when(spyService).getCalendarServiceForUser(userId);

        List<Event> result = spyService.getUserGoogleCalendarEvents(startDate, endDate, userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Event", result.get(0).getSummary());
    }

    @Test
    void testSyncSingleTask_insertNewEvent() throws Exception {
        Long userId = 1L;
        
        // Create and setup task with all necessary dates initialized
        Task task = new Task();
        task.setId(1L);
        task.setName("Test Task");
        task.setDescription("Description");
        task.setDeadline(new Date()); 
        task.setStartDate(new Date());
        
        task.setActiveStatus(true);
        task.setGoogleEventId(null);
        
        // Mock Calendar API components
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Insert insert = mock(Calendar.Events.Insert.class);
        
        Event insertedEvent = new Event().setId("event123");
        
        when(calendar.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.execute()).thenReturn(insertedEvent);
        
        // Spy your actual CalendarService instance (assumed to be spyService)
        doReturn(calendar).when(spyService).getCalendarServiceForUser(userId);
        doReturn(mock(com.google.api.client.auth.oauth2.Credential.class)).when(spyService).getUserCredentials(userId);
        
        // Call method under test
        spyService.syncSingleTask(task, userId);
        
        // Assert that task's googleEventId was updated after sync
        assertEquals("event123", task.getGoogleEventId());
    }

    @Test
    void testGetUserGoogleCalendarEvents_tokenNotFound() {
        Long userId = 99L;
        when(tokenRepository.findById(userId)).thenReturn(Optional.empty());

        java.io.IOException ex = assertThrows(java.io.IOException.class, () ->
            spyService.getUserGoogleCalendarEvents("2025-05-01", "2025-05-10", userId)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("credentials"));
    }

    @Test
    void testSyncSingleTask_calendarApiFails() throws Exception {
        Long userId = 1L;
        Task task = new Task();
        task.setId(1L);
        task.setName("Task");
        task.setValue(10);
        task.setStartDate(new Date());
        task.setDeadline(new Date());
        task.setActiveStatus(true);

        // Mocks for Calendar API
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Insert insert = mock(Calendar.Events.Insert.class);

        when(calendar.events()).thenReturn(events);
        when(events.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.execute()).thenThrow(new RuntimeException("API error"));

        doReturn(calendar).when(spyService).getCalendarServiceForUser(userId);
        doReturn(mock(Credential.class)).when(spyService).getUserCredentials(userId);

        // Expect exception since production code throws it
        Exception exception = assertThrows(RuntimeException.class, () -> {
            spyService.syncSingleTask(task, userId);
        });
        assertEquals("API error", exception.getMessage());
    }




    @Test
    void testGenerateAuthUrl_flowFails() {
        GoogleAuthorizationCodeFlow brokenFlow = mock(GoogleAuthorizationCodeFlow.class);
        when(brokenFlow.newAuthorizationUrl()).thenThrow(new RuntimeException("Broken"));

        CalendarService service = new CalendarService(taskService, tokenRepository);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.generateAuthUrl(123L));
        assertTrue(ex.getMessage().contains("Failed to generate"));
    }

    @Test
    void testSyncTaskWithGoogleCalendar_insert() throws Exception {
        Long userId = 1L;
        Map<String, Object> taskEvent = new HashMap<>();
        taskEvent.put("source", "task");
        taskEvent.put("summary", "Task Summary");
        taskEvent.put("description", "Task Description");
        taskEvent.put("status", "active");

        Map<String, String> start = Map.of("dateTime", "2025-05-01T00:00:00Z");
        Map<String, String> end = Map.of("dateTime", "2025-05-01T01:00:00Z");
        taskEvent.put("start", start);
        taskEvent.put("end", end);

        Calendar calendar = mock(Calendar.class);
        Calendar.Events calEvents = mock(Calendar.Events.class);
        Calendar.Events.Insert insert = mock(Calendar.Events.Insert.class);
        when(calendar.events()).thenReturn(calEvents);
        when(calEvents.insert(eq("primary"), any(Event.class))).thenReturn(insert);
        when(insert.execute()).thenReturn(new Event());

        CalendarService spyService2 = Mockito.spy(calendarService);
        doReturn(calendar).when(spyService2).getCalendarServiceForUser(userId);

        spyService2.syncTaskWithGoogleCalendar(userId, taskEvent);
        verify(calEvents, times(1)).insert(eq("primary"), any(Event.class));
    }

    @Test
    void testToISOString_returnsCorrectFormat() {
        Date now = new Date();
        String result = calendarService.toISOString(now);
        assertTrue(result.contains("T"));
    }
}