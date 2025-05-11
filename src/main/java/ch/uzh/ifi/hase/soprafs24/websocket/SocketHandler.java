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
import java.util.Iterator;

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

        if (authenticated == null || !authenticated) {
            tryAuthenticate(session, message);
        } else {
            log.info("Received regular message from authenticated session {}: '{}'", session.getId(),
                    message.getPayload());
        }
    }

    private void tryAuthenticate(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        log.debug("Attempting to authenticate session {} with payload: {}", session.getId(), payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            if (jsonNode.has("type") && "auth".equalsIgnoreCase(jsonNode.get("type").asText())
                    && jsonNode.has("token")) {
                String rawToken = jsonNode.get("token").asText();
                String token = null;

                if (rawToken != null && rawToken.toLowerCase().startsWith("bearer ")) {
                    token = rawToken.substring(7);
                } else {
                    log.warn("Auth message for session {} received without 'Bearer ' prefix. Assuming raw token.",
                            session.getId());
                    token = rawToken;
                }

                if (token != null && !token.isEmpty()) {
                    // Use UserService.validateToken for authentication
                    if (userService.validateToken(token)) {
                        User user = userService.getUserByToken(token); // We know user exists and is online from
                                                                       // validateToken
                        if (user != null) { // Should always be true if validateToken passed, but good practice to check
                            session.getAttributes().put("userId", user.getId());
                            if (user.getTeamId() != null) {
                                Team userTeam = teamRepository.findTeamById(user.getTeamId());
                                if (userTeam != null) {
                                    session.getAttributes().put("teamId", userTeam.getId());
                                    log.info("WebSocket session {} authenticated for user: {}, userId: {}, teamId: {}",
                                            session.getId(), user.getUsername(), user.getId(), userTeam.getId());
                                } else {
                                    // User has a teamId, but the team entity couldn't be found.
                                    // This could be a data integrity issue or a race condition if teams can be
                                    // deleted.
                                    // For now, we'll log it and not add to pending, as the user *thinks* they are
                                    // in a team.
                                    log.warn(
                                            "WebSocket session {} authenticated for user: {}, userId: {}. User has teamId {} but team entity not found.",
                                            session.getId(), user.getUsername(), user.getId(), user.getTeamId());
                                }
                            } else {
                                // User is authenticated but not yet in a team.
                                // Store the session as pending team assignment.
                                pendingSessionsMap.put(user.getId(), session);
                                log.info(
                                        "WebSocket session {} authenticated for user: {}, userId: {}. User is not in any team. Session stored pending team assignment.",
                                        session.getId(), user.getUsername(), user.getId());
                            }
                            session.getAttributes().put("authenticated", true);
                            session.sendMessage(new TextMessage(
                                    "{\"type\":\"auth_success\",\"message\":\"Authentication successful\"}"));
                            return;
                        } else {
                            // This case should ideally not be reached if validateToken works correctly
                            log.error(
                                    "WebSocket authentication inconsistency for session {}: validateToken passed but getUserByToken failed for token: {}",
                                    session.getId(), token);
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
                            "WebSocket authentication failed for session {}: Token was missing or empty in auth message.",
                            session.getId());
                    session.sendMessage(
                            new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Token missing or empty\"}"));
                    session.close(CloseStatus.POLICY_VIOLATION.withReason("Token missing or empty"));
                }
            } else {
                log.warn("Received non-authentication message from unauthenticated session {}. Payload: {}",
                        session.getId(), payload);
                session.sendMessage(
                        new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Authentication required\"}"));
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required as first message"));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse auth message from session {}. Payload: {}. Error: {}", session.getId(), payload,
                    e.getMessage());
            session.sendMessage(
                    new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Invalid auth message format\"}"));
            session.close(CloseStatus.BAD_DATA.withReason("Invalid auth message format"));
        } catch (Exception e) { // Catching generic Exception to handle any other unexpected errors during auth
            log.error("Error during WebSocket authentication for session {}: {}", session.getId(), e.getMessage(), e);
            try {
                session.sendMessage(
                        new TextMessage("{\"type\":\"auth_failure\",\"message\":\"Authentication error\"}"));
                session.close(CloseStatus.SERVER_ERROR.withReason("Authentication error"));
            } catch (IOException ex) {
                log.error("Failed to send error message or close session {} during auth error handling: {}",
                        session.getId(), ex.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("Plain WebSocket connection closed: {} with status: {} - {} - Authenticated: {}",
                session.getId(), status.getCode(), status.getReason(), session.getAttributes().get("authenticated"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Plain WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(),
                exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
        sessions.remove(session);
    }

    public void associateSessionWithTeam(Long userId, Long teamId) {
        WebSocketSession session = pendingSessionsMap.get(userId);
        if (session != null && session.isOpen()) {
            session.getAttributes().put("teamId", teamId);
            pendingSessionsMap.remove(userId); // Session is now fully associated
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

    public void broadcastMessageToTeam(Long teamId, Object dataPayload) {
        if (teamId == null || dataPayload == null) {
            log.warn("Attempted to broadcast to team with null teamId or payload. Skipping. TeamId: {}", teamId);
            return;
        }
        List<WebSocketSession> teamSessions = new CopyOnWriteArrayList<>();
        Iterator<WebSocketSession> iterator = this.sessions.iterator();
        while (iterator.hasNext()) {
            WebSocketSession session = iterator.next();
            Boolean authenticated = (Boolean) session.getAttributes().get("authenticated");
            if (session.isOpen() && Boolean.TRUE.equals(authenticated)) {
                Long sessionTeamId = (Long) session.getAttributes().get("teamId");
                if (teamId.equals(sessionTeamId)) {
                    teamSessions.add(session);
                }
            } else if (!session.isOpen()) {
                iterator.remove();
            }
        }
        if (!teamSessions.isEmpty()) {
            log.info("Broadcasting message to {} authenticated members of teamId {}. Payload: {}", teamSessions.size(),
                    teamId, dataPayload.getClass().getSimpleName());
            sendMessageToSessions(teamSessions, dataPayload);
        } else {
            log.info("No active and authenticated WebSocket sessions found for teamId {} to send message.", teamId);
        }
    }

    private void sendMessageToSessions(List<WebSocketSession> targetSessions, Object dataPayload) {
        try {
            String messageJson = objectMapper.writeValueAsString(dataPayload);
            TextMessage textMessage = new TextMessage(messageJson);
            int sentCount = 0;

            for (WebSocketSession session : targetSessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                        sentCount++;
                    } catch (IOException e) {
                        log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                    } catch (IllegalStateException e) {
                        log.error("Illegal state for session {} (likely already closing): {}", session.getId(),
                                e.getMessage());
                        if (this.sessions.contains(session)) {
                            this.sessions.remove(session);
                        }
                    }
                } else {
                    if (this.sessions.contains(session)) {
                        this.sessions.remove(session);
                    }
                }
            }
            if (sentCount > 0) {
                log.info("Sent message to {} session(s): Message starts with: {}", sentCount,
                        messageJson.substring(0, Math.min(messageJson.length(), 100)));
            }
        } catch (IOException e) {
            log.error("Failed to serialize data payload for WebSocket broadcast: {}", dataPayload, e);
        }
    }
}