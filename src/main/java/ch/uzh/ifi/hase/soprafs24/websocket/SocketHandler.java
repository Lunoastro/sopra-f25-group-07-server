package ch.uzh.ifi.hase.soprafs24.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SocketHandler.class);
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SocketHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Session attributes (userId, teamIds) should be set by the HandshakeInterceptor
        sessions.add(session);
        log.info("Plain WebSocket connection established: {} from {} with attributes: {}",
                session.getId(), session.getRemoteAddress(), session.getAttributes());
    }

    // handleTextMessage, afterConnectionClosed, handleTransportError remain the same as before

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received plain text message: '{}' from session: {}", message.getPayload(), session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("Plain WebSocket connection closed: {} with status: {} - {}", session.getId(), status.getCode(), status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Plain WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
        sessions.remove(session);
    }


    // Renamed old broadcastMessage to broadcastMessageToAll
    public void broadcastMessageToAll(Object dataPayload) {
        if (dataPayload == null) {
            log.warn("Attempted to broadcast a null payload to all. Skipping.");
            return;
        }
        sendMessageToSessions(this.sessions, dataPayload);
    }

    public void broadcastMessageToTeam(Long teamId, Object dataPayload) {
        if (teamId == null || dataPayload == null) {
            log.warn("Attempted to broadcast to team with null teamId or payload. Skipping. TeamId: {}", teamId);
            return;
        }
        List<WebSocketSession> teamSessions = new CopyOnWriteArrayList<>(); // Use a temporary list for iteration
        for (WebSocketSession session : this.sessions) {
            if (session.isOpen()) {
                List<Long> sessionTeamIds = (List<Long>) session.getAttributes().get("teamIds");
                if (sessionTeamIds != null && sessionTeamIds.contains(teamId)) {
                    teamSessions.add(session);
                }
            } else {
                this.sessions.remove(session); // Clean up closed sessions
            }
        }
        if (!teamSessions.isEmpty()) {
            log.info("Broadcasting message to {} members of teamId {}. Payload: {}", teamSessions.size(), teamId, dataPayload.getClass().getSimpleName());
            sendMessageToSessions(teamSessions, dataPayload);
        } else {
            log.info("No active WebSocket sessions found for teamId {} to send message.", teamId);
        }
    }

    private void sendMessageToSessions(List<WebSocketSession> targetSessions, Object dataPayload) {
        try {
            String messageJson = objectMapper.writeValueAsString(dataPayload);
            TextMessage textMessage = new TextMessage(messageJson);
            int sentCount = 0;

            for (WebSocketSession session : targetSessions) { // Iterate over the passed list
                if (session.isOpen()) {
                    try {
                        synchronized(session) {
                             session.sendMessage(textMessage);
                        }
                        sentCount++;
                    } catch (IOException e) {
                        log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                    } catch (IllegalStateException e) {
                        log.error("Illegal state for session {} (likely already closing): {}", session.getId(), e.getMessage());
                        // This session might be in the process of closing, consider removing from main list if applicable
                        // but be careful with concurrent modification if targetSessions is 'this.sessions'
                        if (this.sessions.contains(session)) { // Check if it's from the main list
                           this.sessions.remove(session);
                        }
                    }
                } else {
                     // If iterating over 'this.sessions' directly, remove here.
                     // If iterating a copy, removing from 'this.sessions' is still good practice.
                    if (this.sessions.contains(session)) {
                       this.sessions.remove(session);
                    }
                }
            }
            if (sentCount > 0) {
                 log.info("Sent message to {} session(s): Message starts with: {}", sentCount, messageJson.substring(0, Math.min(messageJson.length(), 100)));
            }

        } catch (IOException e) {
            log.error("Failed to serialize data payload for WebSocket broadcast: {}", dataPayload, e);
        }
    }
}