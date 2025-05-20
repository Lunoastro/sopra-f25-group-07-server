package ch.uzh.ifi.hase.soprafs24.service.Websocket;

import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.rest.dto.websocket.DatabaseChangeEventDTO;
import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.WebSocketNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/*
 * DECLARATION: The comments have been partially added with the help of vscode copilot.
 */

@SpringBootTest
@TestPropertySource(properties = {
    "frontend.url=http://localhost:3000"
})
class WebSocketNotificationServiceIntegrationTest {

    @Autowired
    private WebSocketNotificationService webSocketNotificationService;

    // We autowire the actual SocketHandler to test the integration.
    // Its dependencies (UserService, TeamRepository) will be mocked.
    @Autowired
    private SocketHandler socketHandler;

    @MockBean
    private UserService mockUserService;

    @MockBean
    private TeamRepository mockTeamRepository;

    private ObjectMapper objectMapper;
    private List<WebSocketSession> originalSessions;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Preserve original sessions and clear for test isolation
        // Note: Accessing internal state of SocketHandler like this is typical for
        // deeper integration tests but can be fragile.
        // A more robust approach might involve a dedicated test WebSocket client.
        originalSessions = new ArrayList<>(socketHandler.getSessionsForTesting()); // Assuming a getter for tests
        socketHandler.getSessionsForTesting().clear();
        socketHandler.getPendingSessionsMapForTesting().clear(); // Assuming a getter for tests
    }

    @AfterEach
    void tearDown() {
        // Restore original sessions
        socketHandler.getSessionsForTesting().clear();
        socketHandler.getSessionsForTesting().addAll(originalSessions);
        socketHandler.getPendingSessionsMapForTesting().clear();
    }


    private WebSocketSession createMockSession(String sessionId, Long userId, Long teamId, boolean authenticated) throws IOException {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("authenticated", authenticated);
        if (userId != null) {
            attributes.put("userId", userId);
        }
        if (teamId != null) {
            attributes.put("teamId", teamId);
        }
        when(mockSession.getAttributes()).thenReturn(attributes);
        doNothing().when(mockSession).sendMessage(any(TextMessage.class));
        return mockSession;
    }

    @Test
    void broadcastEntityChange_sendsToAllAuthenticatedSessions() throws IOException {
        // Given
        WebSocketSession session1 = createMockSession("s1", 1L, 10L, true);
        WebSocketSession session2 = createMockSession("s2", 2L, 20L, true);
        WebSocketSession session3 = createMockSession("s3", 3L, null, false); // Unauthenticated
        WebSocketSession session4 = createMockSession("s4", 4L, 10L, true);
        when(session4.isOpen()).thenReturn(false); // Closed session

        socketHandler.getSessionsForTesting().add(session1);
        socketHandler.getSessionsForTesting().add(session2);
        socketHandler.getSessionsForTesting().add(session3);
        socketHandler.getSessionsForTesting().add(session4);


        String entityType = "global-event";
        Map<String, Object> entityData = Map.of("message", "Hello All!");

        // When
        webSocketNotificationService.broadcastEntityChange(entityType, entityData);

        // Then
        DatabaseChangeEventDTO<?> expectedEvent = new DatabaseChangeEventDTO<>(entityType, entityData);
        String expectedJsonPayload = objectMapper.writeValueAsString(expectedEvent);

        // Verify session1 (authenticated, open) received the message
        ArgumentCaptor<TextMessage> messageCaptor1 = ArgumentCaptor.forClass(TextMessage.class);
        verify(session1, times(1)).sendMessage(messageCaptor1.capture());
        assertEquals(expectedJsonPayload, messageCaptor1.getValue().getPayload());

        // Verify session2 (authenticated, open) received the message
        ArgumentCaptor<TextMessage> messageCaptor2 = ArgumentCaptor.forClass(TextMessage.class);
        verify(session2, times(1)).sendMessage(messageCaptor2.capture());
        assertEquals(expectedJsonPayload, messageCaptor2.getValue().getPayload());

        // Verify session3 (unauthenticated) did NOT receive the message
        verify(session3, never()).sendMessage(any(TextMessage.class));

        // Verify session4 (closed) did NOT receive the message
        verify(session4, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void notifyTeamMembers_sendsOnlyToAuthenticatedMembersOfTargetTeam() throws IOException {
        // Given
        Long targetTeamId = 100L;
        WebSocketSession teamMember1 = createMockSession("tm1", 1L, targetTeamId, true);
        WebSocketSession teamMember2 = createMockSession("tm2", 2L, targetTeamId, true);
        WebSocketSession otherTeamMember = createMockSession("otm1", 3L, 200L, true); // Different team
        WebSocketSession unauthenticatedMember = createMockSession("ua1", 4L, targetTeamId, false); // Same team, but not auth
        WebSocketSession pendingMemberNoTeam = createMockSession("pm1", 5L, null, true); // Authenticated, no team yet

        socketHandler.getSessionsForTesting().add(teamMember1);
        socketHandler.getSessionsForTesting().add(teamMember2);
        socketHandler.getSessionsForTesting().add(otherTeamMember);
        socketHandler.getSessionsForTesting().add(unauthenticatedMember);
        socketHandler.getSessionsForTesting().add(pendingMemberNoTeam);

        String entityType = "team-task-update";
        Map<String, Object> entityData = Map.of("taskId", 7L, "status", "in-progress");

        // When
        webSocketNotificationService.notifyTeamMembers(targetTeamId, entityType, entityData);

        // Then
        DatabaseChangeEventDTO<?> expectedEvent = new DatabaseChangeEventDTO<>(entityType, entityData);
        String expectedJsonPayload = objectMapper.writeValueAsString(expectedEvent);

        // Verify teamMember1 received the message
        ArgumentCaptor<TextMessage> messageCaptorTm1 = ArgumentCaptor.forClass(TextMessage.class);
        verify(teamMember1, times(1)).sendMessage(messageCaptorTm1.capture());
        assertEquals(expectedJsonPayload, messageCaptorTm1.getValue().getPayload());

        // Verify teamMember2 received the message
        ArgumentCaptor<TextMessage> messageCaptorTm2 = ArgumentCaptor.forClass(TextMessage.class);
        verify(teamMember2, times(1)).sendMessage(messageCaptorTm2.capture());
        assertEquals(expectedJsonPayload, messageCaptorTm2.getValue().getPayload());

        // Verify otherTeamMember did NOT receive the message
        verify(otherTeamMember, never()).sendMessage(any(TextMessage.class));

        // Verify unauthenticatedMember did NOT receive the message
        verify(unauthenticatedMember, never()).sendMessage(any(TextMessage.class));

        // Verify pendingMemberNoTeam did NOT receive the message
        verify(pendingMemberNoTeam, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void notifyTeamMembers_noAuthenticatedMembersInTeam_noMessageSent() throws IOException {
        // Given
        Long targetTeamId = 300L;
        WebSocketSession otherTeamMember = createMockSession("otm2", 6L, 400L, true);
        WebSocketSession unauthenticatedMember = createMockSession("ua2", 7L, targetTeamId, false);

        socketHandler.getSessionsForTesting().add(otherTeamMember);
        socketHandler.getSessionsForTesting().add(unauthenticatedMember);

        String entityType = "team-announcement";
        Map<String, Object> entityData = Map.of("message", "Meeting moved");

        // When
        webSocketNotificationService.notifyTeamMembers(targetTeamId, entityType, entityData);

        // Then
        verify(otherTeamMember, never()).sendMessage(any(TextMessage.class));
        verify(unauthenticatedMember, never()).sendMessage(any(TextMessage.class));
        // Also verify no attempt to send to any session from the socketHandler perspective for this specific call
        // This might require peeking into SocketHandler's calls if it had its own mock,
        // but here we are verifying the effect: no messages sent to any of the known sessions.
    }

    @Test
    void notifyTeamMembers_nullTeamId_noMessageSent() throws IOException {
        // Given
        WebSocketSession session1 = createMockSession("s1", 1L, 10L, true);
        socketHandler.getSessionsForTesting().add(session1);

        String entityType = "some-event";
        Map<String, Object> entityData = Map.of("data", "important data");

        // When
        webSocketNotificationService.notifyTeamMembers(null, entityType, entityData);

        // Then
        verify(session1, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastEntityChange_nullEntityData_noMessageSent() throws IOException {
        // Given
        WebSocketSession session1 = createMockSession("s1", 1L, 10L, true);
        socketHandler.getSessionsForTesting().add(session1);

        String entityType = "another-event";

        // When
        webSocketNotificationService.broadcastEntityChange(entityType, null);

        // Then
        verify(session1, never()).sendMessage(any(TextMessage.class));
    }
}