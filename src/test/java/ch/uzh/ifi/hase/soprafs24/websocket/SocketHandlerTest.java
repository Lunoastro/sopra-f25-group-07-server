package ch.uzh.ifi.hase.soprafs24.websocket;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.entity.Task;
import ch.uzh.ifi.hase.soprafs24.repository.TaskRepository;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;

import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/*
 * DECLARATION: For ease of understanding of the tests, the comments have been created with the use of vscode copilot.
 */

class SocketHandlerTest {

    @Mock
    private UserService mockUserService;

    @Mock
    private TaskService mockTaskService;

    @Mock
    private TaskRepository mockTaskRepository;

    @Mock
    private TeamRepository mockTeamRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private SocketHandler socketHandler;

    @Mock
    private WebSocketSession mockSession1;
    @Mock
    private WebSocketSession mockSession2;
    @Mock
    private WebSocketSession mockSession3;

    private Map<String, Object> attributes1;
    private Map<String, Object> attributes2;
    private Map<String, Object> attributes3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        attributes1 = new HashMap<>();
        when(mockSession1.getAttributes()).thenReturn(attributes1);
        when(mockSession1.getId()).thenReturn("s1");
        when(mockSession1.isOpen()).thenReturn(true);

        try {
            doNothing().when(mockSession1).sendMessage(any(TextMessage.class));
        } catch (IOException e) {
            fail("Setup failed");
        }

        attributes2 = new HashMap<>();
        when(mockSession2.getAttributes()).thenReturn(attributes2);
        when(mockSession2.getId()).thenReturn("s2");
        when(mockSession2.isOpen()).thenReturn(true);
        try {
            doNothing().when(mockSession2).sendMessage(any(TextMessage.class));
        } catch (IOException e) {
            fail("Setup failed");
        }

        attributes3 = new HashMap<>();
        when(mockSession3.getAttributes()).thenReturn(attributes3);
        when(mockSession3.getId()).thenReturn("s3");
        when(mockSession3.isOpen()).thenReturn(true);
        try {
            doNothing().when(mockSession3).sendMessage(any(TextMessage.class));
        } catch (IOException e) {
            fail("Setup failed");
        }
        socketHandler = new SocketHandler(mockUserService, mockTeamRepository, mockTaskService);
        socketHandler.getSessionsForTesting().clear();
        socketHandler.getPendingSessionsMapForTesting().clear();
    }

    private TextMessage createAuthMessage(String token, boolean useBearer) {
        String tokenValue = (token == null) ? null : (useBearer ? "Bearer " + token : token);
        if (tokenValue == null) {
            return new TextMessage("{\"type\":\"auth\",\"token\":null}");
        }
        return new TextMessage(String.format("{\"type\":\"auth\",\"token\":\"%s\"}", tokenValue));
    }

    private TextMessage createAuthMessageMissingTokenField() {
        return new TextMessage("{\"type\":\"auth\"}");
    }

    @Test
    void afterConnectionEstablished_addsSession() throws Exception {
        socketHandler.afterConnectionEstablished(mockSession1);
        assertTrue(socketHandler.getSessionsForTesting().contains(mockSession1));
    }

    @Test
    void tryAuthenticate_validTokenWithBearer_userHasTeam_success() throws Exception {
        String token = "valid-token-team";
        User user = new User();
        user.setId(1L);
        user.setUsername("user1");
        user.setTeamId(10L);
        Team team = new Team();
        team.setId(10L);

        when(mockUserService.validateToken(token)).thenReturn(true);
        when(mockUserService.getUserByToken(token)).thenReturn(user);
        when(mockTeamRepository.findTeamById(10L)).thenReturn(team);
        when(mockTaskRepository.findAll()).thenReturn(Collections.emptyList());
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessage(token, true));

        assertEquals(true, attributes1.get("authenticated"));
        assertEquals(1L, attributes1.get("userId"));
        assertEquals(10L, attributes1.get("teamId"));
        assertFalse(socketHandler.getPendingSessionsMapForTesting().containsKey(1L));
        verify(mockSession1).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("auth_success")));
    }

    @Test
    void tryAuthenticate_validTokenNoBearer_userNoTeam_successAndPending() throws Exception {
        String token = "valid-token-no-team-no-bearer";
        User user = new User();
        user.setId(2L);
        user.setUsername("user2");
        user.setTeamId(null);

        when(mockUserService.validateToken(token)).thenReturn(true);
        when(mockUserService.getUserByToken(token)).thenReturn(user);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1,
                new TextMessage("{\"type\":\"auth\",\"token\":\"" + token + "\"}"));

        assertEquals(true, attributes1.get("authenticated"));
        assertEquals(2L, attributes1.get("userId"));
        assertNull(attributes1.get("teamId"));
        assertTrue(socketHandler.getPendingSessionsMapForTesting().containsKey(2L));
        assertEquals(mockSession1, socketHandler.getPendingSessionsMapForTesting().get(2L));
        verify(mockSession1).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("auth_success")));
    }

    @Test
    void tryAuthenticate_invalidToken_sendsFailureAndCloses() throws Exception {
        String token = "invalid-token";

        when(mockUserService.validateToken(token)).thenReturn(false);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessage(token, true));

        verify(mockSession1).sendMessage(
                argThat(msg -> ((TextMessage) msg).getPayload().contains("Invalid token or user offline")));
        verify(mockSession1).close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token or user offline"));
    }

    @Test
    void tryAuthenticate_emptyTokenValueInMessage_sendsFailureAndCloses() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, new TextMessage("{\"type\":\"auth\",\"token\":\"\"}"));

        verify(mockSession1)
                .sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Token missing or empty")));
        verify(mockSession1).close(CloseStatus.POLICY_VIOLATION.withReason("Token missing or empty"));
    }

    @Test
    void tryAuthenticate_nullTokenValueInMessage_sendsCorrectFailureAndCloses() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);

        String rawTokenValueThatBecomesValidateArg = "null";
        when(mockUserService.validateToken(rawTokenValueThatBecomesValidateArg)).thenReturn(false);

        socketHandler.handleTextMessage(mockSession1, new TextMessage("{\"type\":\"auth\",\"token\":null}"));

        verify(mockSession1).sendMessage(
                argThat(msg -> ((TextMessage) msg).getPayload().contains("Invalid token or user offline")));
        verify(mockSession1).close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token or user offline"));
    }

    @Test
    void tryAuthenticate_missingTokenFieldInMessage_sendsFailureAndCloses() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessageMissingTokenField());

        verify(mockSession1)
                .sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Token field missing")));
        verify(mockSession1).close(CloseStatus.POLICY_VIOLATION.withReason("Token field missing"));
    }

    @Test
    void tryAuthenticate_userServiceReturnsNullUserAfterValidateTrue_sendsInconsistencyAndCloses() throws Exception {
        String token = "inconsistent-token";
        when(mockUserService.validateToken(token)).thenReturn(true);
        when(mockUserService.getUserByToken(token)).thenReturn(null);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessage(token, true));

        verify(mockSession1)
                .sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Authentication inconsistency")));
        verify(mockSession1).close(CloseStatus.SERVER_ERROR.withReason("Authentication inconsistency"));
    }

    @Test
    void tryAuthenticate_userHasTeamId_teamNotFoundInRepo_authenticatesLogsWarning() throws Exception {
        String token = "user-with-ghost-team";
        User user = new User();
        user.setId(3L);
        user.setUsername("user3");
        user.setTeamId(20L);

        when(mockUserService.validateToken(token)).thenReturn(true);
        when(mockUserService.getUserByToken(token)).thenReturn(user);
        when(mockTeamRepository.findTeamById(20L)).thenReturn(null);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessage(token, true));

        assertEquals(true, attributes1.get("authenticated"));
        assertEquals(3L, attributes1.get("userId"));
        assertNull(attributes1.get("teamId"));
        verify(mockSession1).sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("auth_success")));
    }

    @Test
    void tryAuthenticate_nonAuthTypeMessage_closesSession() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.handleTextMessage(mockSession1, new TextMessage("{\"type\":\"not_auth\",\"content\":\"stuff\"}"));

        verify(mockSession1, never()).sendMessage(any(TextMessage.class));

        verify(mockSession1).close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required as first message"));
    }

    @Test
    void tryAuthenticate_malformedJson_sendsFailureAndCloses() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.handleTextMessage(mockSession1, new TextMessage("not-json"));

        verify(mockSession1)
                .sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Invalid auth message format")));
        verify(mockSession1).close(CloseStatus.BAD_DATA.withReason("Invalid auth message format"));
    }

    @Test
    void tryAuthenticate_genericExceptionDuringAuth_sendsFailureAndCloses() throws Exception {
        String token = "exception-token";
        when(mockUserService.validateToken(token)).thenThrow(new RuntimeException("Unexpected DB error"));
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessage(token, true));

        verify(mockSession1)
                .sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("Authentication error")));
        verify(mockSession1).close(CloseStatus.SERVER_ERROR.withReason("Authentication error"));
    }

    @Test
    void tryAuthenticate_genericExceptionDuringAuth_sessionNotOpen_logsError() throws Exception {
        String token = "exception-token-session-closed";
        when(mockUserService.validateToken(token)).thenThrow(new RuntimeException("Unexpected DB error"));
        when(mockSession1.isOpen()).thenReturn(false);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, createAuthMessage(token, true));

        verify(mockSession1, never()).sendMessage(any(TextMessage.class));
        verify(mockSession1, never()).close(any(CloseStatus.class));

    }

    @Test
    void handleTextMessage_alreadyAuthenticated_logsMessage() throws Exception {
        attributes1.put("authenticated", true);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTextMessage(mockSession1, new TextMessage("{\"data\":\"some payload\"}"));

        verify(mockSession1, never()).sendMessage(any());
        verify(mockSession1, never()).close(any());
    }

    @Test
    void afterConnectionClosed_removesSessionAndFromPendingMap() throws Exception {
        Long userId = 1L;
        attributes1.put("userId", userId);
        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.getPendingSessionsMapForTesting().put(userId, mockSession1);

        socketHandler.afterConnectionClosed(mockSession1, CloseStatus.NORMAL);

        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession1));
        assertFalse(socketHandler.getPendingSessionsMapForTesting().containsKey(userId));
    }

    @Test
    void afterConnectionClosed_userIdNotPresentInAttributes_removesSessionOnly() throws Exception {

        socketHandler.getSessionsForTesting().add(mockSession1);
        assertTrue(socketHandler.getSessionsForTesting().contains(mockSession1));

        socketHandler.afterConnectionClosed(mockSession1, CloseStatus.NORMAL);

        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession1));

        assertTrue(socketHandler.getPendingSessionsMapForTesting().isEmpty());
    }

    @Test
    void handleTransportError_closesAndRemovesSessionAndFromPendingMap() throws Exception {
        Long userId = 1L;
        attributes1.put("userId", userId);
        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.getPendingSessionsMapForTesting().put(userId, mockSession1);

        socketHandler.handleTransportError(mockSession1, new IOException("Network issue"));

        verify(mockSession1).close(CloseStatus.SERVER_ERROR.withReason("Transport error"));
        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession1));
        assertFalse(socketHandler.getPendingSessionsMapForTesting().containsKey(userId));
    }

    @Test
    void handleTransportError_sessionNotOpen_removesSession() throws Exception {
        when(mockSession1.isOpen()).thenReturn(false);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.handleTransportError(mockSession1, new IOException("Network issue on already closed session"));

        verify(mockSession1, never()).close(any(CloseStatus.class));
        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession1));
    }

    @Test
    void associateSessionWithTeam_validUserAndTeam_updatesSessionAndNotifies() throws Exception {
        Long userId = 1L;
        Long teamId = 10L;
        socketHandler.getPendingSessionsMapForTesting().put(userId, mockSession1);
        socketHandler.getSessionsForTesting().add(mockSession1);

        socketHandler.associateSessionWithTeam(userId, teamId);

        assertEquals(teamId, attributes1.get("teamId"));
        assertFalse(socketHandler.getPendingSessionsMapForTesting().containsKey(userId));
        verify(mockSession1)
                .sendMessage(argThat(msg -> ((TextMessage) msg).getPayload().contains("team_association_complete")
                        && ((TextMessage) msg).getPayload().contains("\"teamId\":" + teamId)));
    }

    @Test
    void associateSessionWithTeam_sessionNotPendingOrNotOpen_doesNothing() throws Exception {
        socketHandler.associateSessionWithTeam(99L, 10L);
        verify(mockSession1, never()).sendMessage(any());

        Long userId = 1L;
        socketHandler.getPendingSessionsMapForTesting().put(userId, mockSession1);
        when(mockSession1.isOpen()).thenReturn(false);
        socketHandler.associateSessionWithTeam(userId, 10L);
        verify(mockSession1, never()).sendMessage(any());
        assertTrue(socketHandler.getPendingSessionsMapForTesting().containsKey(userId));
    }

    @Test
    void associateSessionWithTeam_ioExceptionOnSend_logsError() throws Exception {
        Long userId = 1L;
        Long teamId = 10L;
        socketHandler.getPendingSessionsMapForTesting().put(userId, mockSession1);
        socketHandler.getSessionsForTesting().add(mockSession1);

        doThrow(new IOException("Failed to send message")).when(mockSession1).sendMessage(any(TextMessage.class));

        socketHandler.associateSessionWithTeam(userId, teamId);

        assertEquals(teamId, attributes1.get("teamId"));
        assertFalse(socketHandler.getPendingSessionsMapForTesting().containsKey(userId));

    }

    @Test
    void broadcastMessageToAll_sendsToAllOpenAuthenticatedSessions() throws Exception {
        Map<String, Object> payload = Map.of("data", "global message");
        String expectedJson = objectMapper.writeValueAsString(payload);

        attributes1.put("authenticated", true);
        attributes2.put("authenticated", true);
        attributes3.put("authenticated", false);

        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.getSessionsForTesting().add(mockSession2);
        socketHandler.getSessionsForTesting().add(mockSession3);

        socketHandler.broadcastMessageToAll(payload);

        verify(mockSession1).sendMessage(eq(new TextMessage(expectedJson)));
        verify(mockSession2).sendMessage(eq(new TextMessage(expectedJson)));
        verify(mockSession3, never()).sendMessage(any());
    }

    @Test
    void broadcastMessageToAll_noAuthenticatedSessions_logsInfo() throws Exception {
        attributes1.put("authenticated", false);
        socketHandler.getSessionsForTesting().add(mockSession1);
        Map<String, Object> payload = Map.of("data", "message");

        socketHandler.broadcastMessageToAll(payload);

        verify(mockSession1, never()).sendMessage(any());
    }

    @Test
    void broadcastMessageToAll_nullPayload_logsWarningAndSkips() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);
        attributes1.put("authenticated", true);

        socketHandler.broadcastMessageToAll(null);

        verify(mockSession1, never()).sendMessage(any());
    }

    @Test
    void broadcastMessageToTeam_sendsToCorrectTeamMembersOnly() throws Exception {
        Long teamId1 = 10L;
        Long teamId2 = 20L;
        Map<String, Object> payload = Map.of("data", "team 10 message");
        String expectedJson = objectMapper.writeValueAsString(payload);

        attributes1.put("authenticated", true);
        attributes1.put("teamId", teamId1);
        attributes2.put("authenticated", true);
        attributes2.put("teamId", teamId2);
        attributes3.put("authenticated", true);
        attributes3.put("teamId", teamId1);

        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.getSessionsForTesting().add(mockSession2);
        socketHandler.getSessionsForTesting().add(mockSession3);

        socketHandler.broadcastMessageToTeam(teamId1, payload);

        verify(mockSession1).sendMessage(eq(new TextMessage(expectedJson)));
        verify(mockSession2, never()).sendMessage(any());
        verify(mockSession3).sendMessage(eq(new TextMessage(expectedJson)));
    }

    @Test
    void broadcastMessageToTeam_removesClosedSessionDuringIterationAndSendsToOpen() throws Exception {
        Long targetTeamId = 77L;
        Map<String, Object> payload = Map.of("data", "team 77 message");
        String expectedJson = objectMapper.writeValueAsString(payload);

        attributes1.put("authenticated", true);
        attributes1.put("teamId", targetTeamId);
        attributes2.put("authenticated", true);
        attributes2.put("teamId", targetTeamId);
        attributes3.put("authenticated", true);
        attributes3.put("teamId", targetTeamId);

        socketHandler.getSessionsForTesting().add(mockSession1);
        socketHandler.getSessionsForTesting().add(mockSession2);
        socketHandler.getSessionsForTesting().add(mockSession3);

        when(mockSession2.isOpen()).thenReturn(false);

        socketHandler.broadcastMessageToTeam(targetTeamId, payload);

        verify(mockSession1).sendMessage(eq(new TextMessage(expectedJson)));
        verify(mockSession2, never()).sendMessage(any());
        verify(mockSession3).sendMessage(eq(new TextMessage(expectedJson)));

        assertTrue(socketHandler.getSessionsForTesting().contains(mockSession1));
        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession2),
                "Closed session should have been removed by sendMessagesToOpenSessions's else branch");
        assertTrue(socketHandler.getSessionsForTesting().contains(mockSession3));
    }

    @Test
    void broadcastMessageToTeam_nullTeamIdOrPayload_logsWarningAndSkips() throws Exception {
        socketHandler.getSessionsForTesting().add(mockSession1);
        attributes1.put("authenticated", true);
        attributes1.put("teamId", 10L);

        socketHandler.broadcastMessageToTeam(null, Map.of("data", "test"));
        verify(mockSession1, never()).sendMessage(any());

        socketHandler.broadcastMessageToTeam(10L, null);
        verify(mockSession1, never()).sendMessage(any());
    }

    @Test
    void broadcastMessageToTeam_noMatchingSessions_logsInfo() throws Exception {
        Long targetTeamId = 100L;
        attributes1.put("authenticated", true);
        attributes1.put("teamId", 10L);
        socketHandler.getSessionsForTesting().add(mockSession1);
        Map<String, Object> payload = Map.of("data", "message");

        socketHandler.broadcastMessageToTeam(targetTeamId, payload);

        verify(mockSession1, never()).sendMessage(any());
    }

    @Test
    void sendMessageToSession_ioException_logsErrorAndReturnsFalse() throws Exception {
        when(mockSession1.isOpen()).thenReturn(true);
        doThrow(new IOException("Simulated send error")).when(mockSession1).sendMessage(any(TextMessage.class));
        attributes1.put("authenticated", true);

        attributes1.put("userId", 1L);
        socketHandler.getSessionsForTesting().add(mockSession1);

        Map<String, Object> payload = Map.of("data", "test");

        socketHandler.broadcastMessageToAll(payload);

        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession1),
                "Session should be removed by removeClosedSession after IOException");
    }

    @Test
    void sendMessageToSession_illegalStateException_logsErrorRemovesSessionReturnsFalse() throws Exception {
        when(mockSession1.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException("Simulated session closing")).when(mockSession1)
                .sendMessage(any(TextMessage.class));
        attributes1.put("authenticated", true);
        socketHandler.getSessionsForTesting().add(mockSession1);

        Map<String, Object> payload = Map.of("data", "test");
        socketHandler.broadcastMessageToAll(payload);

        assertFalse(socketHandler.getSessionsForTesting().contains(mockSession1));
    }

    @Test
    void sendCurrentTasksForTeam_sendsTasksForGivenTeam() throws Exception {
        // Arrange
        Long teamId = 42L;
        Task task1 = new Task();
        task1.setId(1L);
        task1.setTeamId(teamId);
        Task task2 = new Task();
        task2.setId(2L);
        task2.setTeamId(teamId);
        Task taskOther = new Task();
        taskOther.setId(3L);
        taskOther.setTeamId(99L);

        when(mockTaskService.getAllTasks()).thenReturn(List.of(task1, task2, taskOther));

        // Act
        socketHandler.sendCurrentTasksForTeam(mockSession1, teamId);

        // Assert
        verify(mockSession1).sendMessage(argThat(msg -> {
            String payload = ((TextMessage) msg).getPayload();
            // Check for new DatabaseChangeEventDTO structure
            return payload.contains("\"entityType\":\"TASKS\"")
                    && payload.contains("\"id\":1")
                    && payload.contains("\"id\":2")
                    && !payload.contains("\"id\":3")
                    && payload.contains("\"payload\":[");
        }));
    }

    @Test
    void sendCurrentTasksForTeam_noTasksForTeam_sendsEmptyList() throws Exception {
        Long teamId = 123L;
        when(mockTaskService.getAllTasks()).thenReturn(List.of());

        socketHandler.sendCurrentTasksForTeam(mockSession1, teamId);

        verify(mockSession1).sendMessage(argThat(msg -> {
            String payload = ((TextMessage) msg).getPayload();
            // Check for new DatabaseChangeEventDTO structure with empty payload
            return payload.contains("\"entityType\":\"TASKS\"")
                    && payload.contains("\"payload\":[]");
        }));
    }

    @Test
    void sendCurrentTasksForTeam_sendMessageThrowsIOException_logsError() throws Exception {
        Long teamId = 55L;
        Task task = new Task();
        task.setId(1L);
        task.setTeamId(teamId);

        when(mockTaskService.getAllTasks()).thenReturn(List.of(task));
        doThrow(new IOException("fail")).when(mockSession1).sendMessage(any(TextMessage.class));

        assertThrows(IOException.class, () -> socketHandler.sendCurrentTasksForTeam(mockSession1, teamId));
    }

}