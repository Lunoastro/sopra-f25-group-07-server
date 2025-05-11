package ch.uzh.ifi.hase.soprafs24.websocket;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.service.UserService;
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

    @Autowired
    public SocketHandler(UserService userService, TeamRepository teamRepository) {
        this.userService = userService;
        this.teamRepository = teamRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    
    public List<WebSocketSession> getSessionsForTesting() {
        return sessions;
    }

    public Map<Long, WebSocketSession> getPendingSessionsMapForTesting() {
        return pendingSessionsMap;
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

        if (Boolean.TRUE.equals(authenticated)) { // More robust check for true
            log.info("Received regular message from an authenticated session.");
            // Potentially handle other message types here if your protocol defines them
        } else {
            tryAuthenticate(session, message);
        }
    }

    private void tryAuthenticate(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        log.debug("Attempting to authenticate a session.");
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("type") && "auth".equalsIgnoreCase(jsonNode.get("type").asText())) { // Check type first
                if (jsonNode.has("token")) { // Then check for token
                    String rawToken = jsonNode.get("token").asText();
                    String tokenToValidate = null;

                    if (rawToken != null && !rawToken.isEmpty()) {
                        if (rawToken.toLowerCase().startsWith("bearer ")) {
                            tokenToValidate = rawToken.substring(7);
                        } else {
                            log.warn("Auth message received without 'Bearer ' prefix for session {}. Assuming raw token.", session.getId());
                            tokenToValidate = rawToken; 
                        }
                    }


                    if (tokenToValidate != null && !tokenToValidate.isEmpty()) {
                        if (userService.validateToken(tokenToValidate)) {
                            User user = userService.getUserByToken(tokenToValidate);
                            if (user != null) {
                                session.getAttributes().put("userId", user.getId());
                                if (user.getTeamId() != null) {
                                    Team userTeam = teamRepository.findTeamById(user.getTeamId());
                                    if (userTeam != null) {
                                        session.getAttributes().put("teamId", userTeam.getId());
                                        log.info("WebSocket session {} authenticated for user: {}, userId: {}, teamId: {}",
                                                session.getId(), user.getUsername(), user.getId(), userTeam.getId());
                                    } else {
                                        log.warn(
                                                "WebSocket session {} authenticated for user: {}, userId: {}. User has teamId {} but team entity not found.",
                                                session.getId(), user.getUsername(), user.getId(), user.getTeamId());
                                    }
                                } else {
                                    pendingSessionsMap.put(user.getId(), session);
                                    log.info(
                                            "WebSocket session {} authenticated for user: {}, userId: {}. User is not in any team. Session stored pending team assignment.",
                                            session.getId(), user.getUsername(), user.getId());
                                }
                                session.getAttributes().put("authenticated", true);
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
                    } else { // Token value was null or empty after processing rawToken
                        log.warn(
                                "WebSocket authentication failed for session {}: Token was effectively missing or empty after processing.",
                                session.getId());
                        session.sendMessage(
                                new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Token missing or empty\"}"));
                        session.close(CloseStatus.POLICY_VIOLATION.withReason("Token missing or empty"));
                    }
                } else { // "token" field is missing in auth message
                    log.warn("WebSocket authentication failed for session {}: 'token' field missing in auth message.", session.getId());
                    session.sendMessage(new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Token field missing\"}"));
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("Token field missing"));
                }
            } else { // Not an "auth" type message or "type" field missing
                log.warn("Received non-authentication message or malformed auth type from unauthenticated session {}.", session.getId());
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
                if (session.isOpen()) { // Check if session is still open before sending
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
        sessions.remove(session);
        // Also remove from pendingSessionsMap if the user ID was stored
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            pendingSessionsMap.remove(userId, session); // Remove only if this specific session was mapped
        }
        log.info("Plain WebSocket connection closed: {} with status: {} - {} - Authenticated: {}",
                session.getId(), status.getCode(), status.getReason(), session.getAttributes().get("authenticated"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Plain WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(),
                exception);
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException e) {
                log.error("Error closing session {} after transport error: {}", session.getId(), e.getMessage());
            }
        }
        sessions.remove(session);
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            pendingSessionsMap.remove(userId, session);
        }
    }

    public void associateSessionWithTeam(Long userId, Long teamId) {
        WebSocketSession session = pendingSessionsMap.get(userId);
        if (session != null && session.isOpen()) {
            session.getAttributes().put("teamId", teamId);
            pendingSessionsMap.remove(userId);
            log.info("WebSocket session {} re-associated with teamId: {} for userId: {}", session.getId(), teamId,
                    userId);
            try {
                session.sendMessage(
                        new TextMessage("{\"type\":\"team_association_complete\",\"teamId\":" + teamId + "}"));
            } catch (IOException e) {
                log.error("Failed to send team association_complete message to session {}: {}", session.getId(),
                        e.getMessage());
            }
        } else {
            log.info("No pending WebSocket session found for userId {} or session is not open.", userId);
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

    /**
     * Broadcasts a message to all authenticated WebSocket sessions associated with a specific team.
     *
     * @param teamId      The ID of the team to which the message should be sent.
     * @param dataPayload The data payload to be sent as a message.
     */
public void broadcastMessageToTeam(Long teamId, Object dataPayload) {
    if (teamId == null || dataPayload == null) {
        log.warn("Attempted to broadcast to team with null teamId or payload. Skipping. TeamId: {}", teamId);
        return;
    }
    List<WebSocketSession> teamSessionsToSend = new ArrayList<>();
    List<WebSocketSession> sessionsToRemove = new ArrayList<>();

    for (WebSocketSession session : this.sessions) {
        if (!session.isOpen()) {
            sessionsToRemove.add(session); // Mark for removal
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
        for(WebSocketSession removedSession : sessionsToRemove) {
            log.debug("Removed closed session {} during team broadcast filtering.", removedSession.getId());
        }
    }

    if (!teamSessionsToSend.isEmpty()) {
        log.info("Broadcasting message to {} authenticated members of teamId {}. Payload: {}", teamSessionsToSend.size(),
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
            logSentMessageInfo(sentCount, messageJson);
        } catch (IOException e) {
            log.error("Failed to serialize data payload for WebSocket broadcast: {}", dataPayload, e);
        }
    }

    private int sendMessagesToOpenSessions(List<WebSocketSession> targetSessions, TextMessage textMessage) {
        int sentCount = 0;
        
        for (WebSocketSession session : new ArrayList<>(targetSessions)) { // Iterate over a copy for safety if original list can change
            if (session.isOpen()) {
                if (sendMessageToSession(session, textMessage)) {
                    sentCount++;
                }
            } else {
                // Session was found to be closed before attempting send.
                // It might have been closed by another thread or event.
                removeClosedSession(session); // Ensure it's removed from the main `sessions` list
            }
        }
        return sentCount;
    }

    private boolean sendMessageToSession(WebSocketSession session, TextMessage textMessage) {
        try {
            synchronized (this) {
                session.sendMessage(textMessage);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Illegal state for session {} (likely already closing): {}", session.getId(), e.getMessage());
            removeClosedSession(session); // Remove from the main 'sessions' list
        }
        return false;
    }

    private void removeClosedSession(WebSocketSession session) {
        // This method assumes `this.sessions` is the main list of active sessions.
        boolean removed = this.sessions.remove(session);
        if (removed) {
            log.debug("Removed closed session {} from active list.", session.getId());
        }
    }

    private void logSentMessageInfo(int sentCount, String messageJson) {
        if (sentCount > 0) {
            log.info("Sent message to {} session(s): Message starts with: {}", sentCount,
                    messageJson.substring(0, Math.min(messageJson.length(), 100)));
        }
    }
}