package ch.uzh.ifi.hase.soprafs24.websocket;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.service.TaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.ArrayList;

@Component
public class SocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SocketHandler.class);
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<Long, WebSocketSession> pendingSessionsMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final TeamRepository teamRepository;
    private final TaskService taskService;

    @Autowired
    public SocketHandler(UserService userService, TeamRepository teamRepository, TaskService taskService) {
        this.userService = userService;
        this.teamRepository = teamRepository;
        this.taskService = taskService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private void closeExistingSessionForUser(Long userId) {
    if (userId == null) return;
    for (WebSocketSession session : new ArrayList<>(sessions)) {
        Long sessionUserId = (Long) session.getAttributes().get("userId");
        if (userId.equals(sessionUserId) && session.isOpen()) {
            try {
                log.info("Closing previous WebSocket session {} for user {} due to new login.", session.getId(), userId);
                session.close(CloseStatus.NORMAL.withReason("New login from another device or tab"));
            } catch (IOException e) {
                log.warn("Failed to close previous session for user {}: {}", userId, e.getMessage());
            }
        }
    }
}


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("Plain WebSocket connection established: {} from {}. Awaiting authentication message.",
                session.getId(), session.getRemoteAddress());
    }

    @Override
protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    Boolean authenticated = (Boolean) session.getAttributes().get("authenticated");

    if (Boolean.TRUE.equals(authenticated)) {
        
        String payload = message.getPayload();
        JsonNode jsonNode = objectMapper.readTree(payload);
        String messageType = jsonNode.has("type") ? jsonNode.get("type").asText() : null;
        Long userId = (Long) session.getAttributes().get("userId"); 

        if ("LOCK".equalsIgnoreCase(messageType)) {
            if (jsonNode.has("payload") && jsonNode.get("payload").has("taskId")) {
                String taskIdStr = jsonNode.get("payload").get("taskId").asText();
                try {
                    Long taskId = Long.parseLong(taskIdStr);
                    
                    taskService.lockTask(taskId, userId);
                    
                    log.info("User {} requested to lock task {}", userId, taskId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid taskId format received for LOCK: {}", taskIdStr);
                    
                } catch (Exception e) {
                    log.error("Error processing LOCK for user {}: {}", userId, e.getMessage());
                    
                }
            }
        } else if ("UNLOCK".equalsIgnoreCase(messageType)) {
            if (jsonNode.has("payload") && jsonNode.get("payload").has("taskId")) {
                String taskIdStr = jsonNode.get("payload").get("taskId").asText();
                 try {
                    Long taskId = Long.parseLong(taskIdStr);
                    
                    taskService.unlockTask(taskId, userId);
                    
                    log.info("User {} requested to unlock task {}", userId, taskId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid taskId format received for UNLOCK: {}", taskIdStr);
                    
                } catch (Exception e) {
                    log.error("Error processing UNLOCK for user {}: {}", userId, e.getMessage());
                    
                }
            }
        } else {
            
            log.info("Received message of type '{}' from authenticated session {}.", messageType, session.getId());
        }

    } else {
        tryAuthenticate(session, message);
    }
}

    private void tryAuthenticate(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        log.debug("Attempting to authenticate session {}.", session.getId());
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("type") && "auth".equalsIgnoreCase(jsonNode.get("type").asText())) {
                if (jsonNode.has("token")) {
                    String rawToken = jsonNode.get("token").asText();
                    String tokenToValidate = null;

                    if (rawToken != null && !rawToken.isEmpty()) {
                        if (rawToken.toLowerCase().startsWith("bearer ")) {
                            tokenToValidate = rawToken.substring(7);
                        } else {
                            log.warn(
                                    "Auth message received without 'Bearer ' prefix for session {}. Assuming raw token.",
                                    session.getId());
                            tokenToValidate = rawToken;
                        }
                    }

                    if (tokenToValidate != null && !tokenToValidate.isEmpty()) {
                        if (userService.validateToken(tokenToValidate)) {
                            User user = userService.getUserByToken(tokenToValidate);
                            if (user != null) {
                                closeExistingSessionForUser(user.getId());
                                session.getAttributes().put("userId", user.getId());
                                session.getAttributes().put("authenticated", true);

                                if (user.getTeamId() != null) {
                                    Team userTeam = teamRepository.findTeamById(user.getTeamId());
                                    if (userTeam != null) {
                                        session.getAttributes().put("teamId", userTeam.getId());
                                        log.info(
                                                "WebSocket session {} authenticated for user: {}, userId: {}, teamId: {}",
                                                session.getId(), user.getUsername(), user.getId(), userTeam.getId());
                                    } else {
                                        log.warn(
                                                "WebSocket session {} authenticated for user: {}, userId: {}. User has teamId {} but team entity not found. Moving to pending.",
                                                session.getId(), user.getUsername(), user.getId(), user.getTeamId());
                                        pendingSessionsMap.put(user.getId(), session);
                                    }
                                } else {
                                    pendingSessionsMap.put(user.getId(), session);
                                    log.info(
                                            "WebSocket session {} authenticated for user: {}, userId: {}. User is not in any team. Session stored pending team assignment.",
                                            session.getId(), user.getUsername(), user.getId());
                                }
                                session.sendMessage(new TextMessage(
                                        "{\"type\":\"auth_success\",\"message\":\"Authentication successful\"}"));
                            } else {
                                log.error(
                                        "WebSocket authentication inconsistency for session {}: validateToken passed but getUserByToken failed for token: {}",
                                        session.getId(), tokenToValidate);
                                session.sendMessage(new TextMessage(
                                        "{\"type\":\"auth_failure\",\"message\":\"Authentication inconsistency\"}"));
                                session.close(CloseStatus.SERVER_ERROR.withReason("Authentication inconsistency"));
                            }
                        } else {
                            log.warn("WebSocket authentication failed for session {}: Invalid or offline user token.",
                                    session.getId());
                            session.sendMessage(new TextMessage(
                                    "{\"type\":\"auth_failure\",\"message\":\"Invalid token or user offline\"}"));
                            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token or user offline"));
                        }
                    } else {
                        log.warn(
                                "WebSocket authentication failed for session {}: Token was effectively missing or empty after processing.",
                                session.getId());
                        session.sendMessage(
                                new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Token missing or empty\"}"));
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Token missing or empty"));
                    }
                } else {
                    log.warn("WebSocket authentication failed for session {}: 'token' field missing in auth message.",
                            session.getId());
                    session.sendMessage(
                            new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Token field missing\"}"));
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("Token field missing"));
                }
            } else {
                log.warn("Received non-authentication message or malformed auth type from unauthenticated session {}.",
                        session.getId());
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required as first message"));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse auth message from session {}. Payload: {}. Error: {}", session.getId(), payload,
                    e.getMessage());
            session.sendMessage(
                    new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Invalid auth message format\"}"));
            session.close(CloseStatus.BAD_DATA.withReason("Invalid auth message format"));
        } catch (Exception e) {
            log.error("Error during WebSocket authentication for session {}: {}", session.getId(), e.getMessage(), e);
            try {
                if (session.isOpen()) {
                    session.sendMessage(
                            new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Authentication error\"}"));
                    session.close(CloseStatus.SERVER_ERROR.withReason("Authentication error"));
                }
            } catch (IOException ex) {
                log.error("Failed to send error message or close session {} during auth error handling: {}",
                        session.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        boolean removedFromSessions = sessions.remove(session);
        Long userId = (Long) session.getAttributes().get("userId");
        boolean removedFromPending = false;
        if (userId != null) {

            removedFromPending = pendingSessionsMap.remove(userId, session);
        }
        log.info(
                "Plain WebSocket connection closed: {} with status: {} - {} - Authenticated: {}. Removed from sessions: {}, Removed from pending: {}",
                session.getId(), status.getCode(), status.getReason(), session.getAttributes().get("authenticated"),
                removedFromSessions, removedFromPending);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Plain WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(),
                exception);
        boolean removedFromSessions = sessions.remove(session);
        Long userId = (Long) session.getAttributes().get("userId");
        boolean removedFromPending = false;
        if (userId != null) {
            removedFromPending = pendingSessionsMap.remove(userId, session);
        }
        log.debug("Session {} removed due to transport error. From sessions: {}, From pending: {}", session.getId(),
                removedFromSessions, removedFromPending);

        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"));
            } catch (IOException e) {
                log.error("Error closing session {} after transport error: {}", session.getId(), e.getMessage());
            }
        }
    }

    public void associateSessionWithTeam(Long userId, Long teamId) {
        WebSocketSession session = pendingSessionsMap.get(userId);
        if (session != null && session.isOpen()) {
            session.getAttributes().put("teamId", teamId);
            pendingSessionsMap.remove(userId, session);
            log.info("WebSocket session {} re-associated with teamId: {} for userId: {}", session.getId(), teamId,
                    userId);
            try {
                session.sendMessage(
                        new TextMessage(objectMapper
                                .writeValueAsString(Map.of("type", "team_association_complete", "teamId", teamId))));
            } catch (IOException e) {
                log.error("Failed to send team association_complete message to session {}: {}", session.getId(),
                        e.getMessage());
            }
        } else {
            log.info(
                    "No pending WebSocket session found for userId {} or session is not open to associate with team {}.",
                    userId, teamId);
        }
    }

    /**
     * Moves an active WebSocket session for a given userId to the pending state.
     * This is typically called when a user leaves a team.
     * 
     * @param userId The ID of the user whose session should be moved.
     */
    public void moveSessionToPending(Long userId) {
        if (userId == null) {
            log.warn("moveSessionToPending called with null userId. Skipping.");
            return;
        }

        WebSocketSession sessionToPend = null;

        for (WebSocketSession session : this.sessions) {
            Long sessionUserId = (Long) session.getAttributes().get("userId");
            if (userId.equals(sessionUserId)) {
                sessionToPend = session;
                break;
            }
        }

        if (sessionToPend != null && sessionToPend.isOpen()) {
            Object oldTeamId = sessionToPend.getAttributes().remove("teamId");

            if (sessionToPend.getAttributes().get("authenticated") == null) {
                sessionToPend.getAttributes().put("authenticated", true);
            }

            pendingSessionsMap.put(userId, sessionToPend);

            if (oldTeamId != null) {
                log.info("User {} left team {}. Their WebSocket session {} ({}) moved to pending.", userId, oldTeamId,
                        sessionToPend.getId(), sessionToPend.getRemoteAddress());
            } else {
                log.info("User {} now has no team. Their WebSocket session {} ({}) moved/confirmed to pending.", userId,
                        sessionToPend.getId(), sessionToPend.getRemoteAddress());
            }
            try {
                sessionToPend.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "session_pending", "message", "Your session is now pending team assignment."))));
            } catch (IOException e) {
                log.error("Failed to send session_pending message to session {} for user {}: {}", sessionToPend.getId(),
                        userId, e.getMessage());
            }
        } else {
            if (sessionToPend == null) {
                log.info("No active WebSocket session found for userId {} to move to pending.", userId);
            } else {
                log.info("WebSocket session {} for userId {} found but is not open. Cannot move to pending.",
                        sessionToPend.getId(), userId);

                this.sessions.remove(sessionToPend);
                this.pendingSessionsMap.remove(userId, sessionToPend);
            }
        }
    }

    /**
     * Closes the WebSocket session for a specific user, if one exists.
     * 
     * @param userId The ID of the user whose session should be closed.
     * @param reason The reason for closing the session.
     */
    public void closeSessionForUser(Long userId, String reason) {
        if (userId == null) {
            log.warn("closeSessionForUser called with null userId. Skipping.");
            return;
        }

        WebSocketSession sessionToClose = null;
        for (WebSocketSession session : this.sessions) {
            Long sessionUserId = (Long) session.getAttributes().get("userId");
            if (userId.equals(sessionUserId)) {
                sessionToClose = session;
                break;
            }
        }

        if (sessionToClose != null) {
            if (sessionToClose.isOpen()) {
                log.info("Closing WebSocket session {} for user {} due to: {}", sessionToClose.getId(), userId, reason);
                try {
                    sessionToClose.close(CloseStatus.NORMAL.withReason(reason));

                } catch (IOException e) {
                    log.error("Error closing WebSocket session {} for user {}: {}. Forcing removal.",
                            sessionToClose.getId(), userId, e.getMessage());

                    this.sessions.remove(sessionToClose);
                    this.pendingSessionsMap.remove(userId, sessionToClose);
                }
            } else {
                log.info("Session {} for user {} was already closed. Ensuring it's removed from tracking.",
                        sessionToClose.getId(), userId);
                this.sessions.remove(sessionToClose);
                this.pendingSessionsMap.remove(userId, sessionToClose);
            }
        } else {
            log.info("No active WebSocket session found for user {} to close.", userId);
        }
    }

    public void broadcastMessageToAll(Object dataPayload) {
        if (dataPayload == null) {
            log.warn("Attempted to broadcast a null payload to all. Skipping.");
            return;
        }
        List<WebSocketSession> authenticatedSessions = new CopyOnWriteArrayList<>();
        for (WebSocketSession session : this.sessions) {
            Boolean authenticated = (Boolean) session.getAttributes().get("authenticated");
            if (session.isOpen() && Boolean.TRUE.equals(authenticated)) {
                authenticatedSessions.add(session);
            }
        }
        if (!authenticatedSessions.isEmpty()) {
            sendMessageToSessions(authenticatedSessions, dataPayload);
        } else {
            log.info("No authenticated WebSocket sessions found to broadcast message to all.");
        }
    }

    public void broadcastMessageToTeam(Long teamId, Object dataPayload) {
        if (teamId == null || dataPayload == null) {
            log.warn("Attempted to broadcast to team with null teamId or payload. Skipping. TeamId: {}", teamId);
            return;
        }
        List<WebSocketSession> teamSessionsToSend = new ArrayList<>();
        List<WebSocketSession> sessionsToRemove = new ArrayList<>();

        for (WebSocketSession session : this.sessions) {
            if (!session.isOpen()) {
                sessionsToRemove.add(session);
                Long sessionUserId = (Long) session.getAttributes().get("userId");
                if (sessionUserId != null) {
                    pendingSessionsMap.remove(sessionUserId, session);
                }
                continue;
            }
            Boolean authenticated = (Boolean) session.getAttributes().get("authenticated");
            if (Boolean.TRUE.equals(authenticated)) {
                Long sessionTeamId = (Long) session.getAttributes().get("teamId");
                if (teamId.equals(sessionTeamId)) {
                    teamSessionsToSend.add(session);
                }
            }
        }
        if (!sessionsToRemove.isEmpty()) {
            this.sessions.removeAll(sessionsToRemove);
            log.debug("Removed {} closed sessions during team broadcast filtering.", sessionsToRemove.size());
        }

        if (!teamSessionsToSend.isEmpty()) {
            log.info("Broadcasting message to {} authenticated members of teamId {}. Payload type: {}",
                    teamSessionsToSend.size(),
                    teamId, dataPayload.getClass().getSimpleName());
            sendMessageToSessions(teamSessionsToSend, dataPayload);
        } else {
            log.info("No active and authenticated WebSocket sessions found for teamId {} to send message.", teamId);
        }
    }

    private void sendMessageToSessions(List<WebSocketSession> targetSessions, Object dataPayload) {
        try {
            String messageJson = objectMapper.writeValueAsString(dataPayload);
            TextMessage textMessage = new TextMessage(messageJson);
            int sentCount = sendMessagesToOpenSessions(targetSessions, textMessage);

            if (sentCount > 0) {
                log.info("Sent message to {} session(s). Message starts with: {}", sentCount,
                        messageJson.substring(0, Math.min(messageJson.length(), 100)));
            } else {
                log.info("No open sessions in the target list to send message: {}",
                        messageJson.substring(0, Math.min(messageJson.length(), 100)));
            }
        } catch (IOException e) {
            log.error("Failed to serialize data payload for WebSocket broadcast: {}",
                    dataPayload.getClass().getSimpleName(), e);
        }
    }

    private int sendMessagesToOpenSessions(List<WebSocketSession> targetSessions, TextMessage textMessage) {
        int sentCount = 0;
        List<WebSocketSession> sessionsToRemove = new ArrayList<>();

        for (WebSocketSession session : targetSessions) {
            if (session.isOpen()) {
                if (sendMessageToSession(session, textMessage)) {
                    sentCount++;
                }
            } else {
                sessionsToRemove.add(session);
            }
        }
        if (!sessionsToRemove.isEmpty()) {
            this.sessions.removeAll(sessionsToRemove);
            for (WebSocketSession removedSession : sessionsToRemove) {
                Long userId = (Long) removedSession.getAttributes().get("userId");
                if (userId != null) {
                    this.pendingSessionsMap.remove(userId, removedSession);
                }
                log.debug("Removed closed session {} during batch send.", removedSession.getId());
            }
        }
        return sentCount;
    }

    private boolean sendMessageToSession(WebSocketSession session, TextMessage textMessage) {
        try {

            session.sendMessage(textMessage);

            return true;
        } catch (IOException e) {
            log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            removeClosedSession(session);
        } catch (IllegalStateException e) {
            log.error("Illegal state for session {} (likely closing/closed): {}", session.getId(), e.getMessage());
            removeClosedSession(session);
        }
        return false;
    }

    private void removeClosedSession(WebSocketSession session) {
        boolean removedMain = this.sessions.remove(session);
        Long userId = (Long) session.getAttributes().get("userId");
        boolean removedPending = false;
        if (userId != null) {
            removedPending = this.pendingSessionsMap.remove(userId, session);
        }
        if (removedMain || removedPending) {
            log.debug("Removed closed session {} from active tracking (main: {}, pending: {}).", session.getId(),
                    removedMain, removedPending);
        }
    }

    public List<WebSocketSession> getSessionsForTesting() {
        return sessions;
    }

    public Map<Long, WebSocketSession> getPendingSessionsMapForTesting() {
        return pendingSessionsMap;
    }
}