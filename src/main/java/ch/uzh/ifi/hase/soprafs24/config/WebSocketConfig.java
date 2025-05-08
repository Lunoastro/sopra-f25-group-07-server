// src/main/java/ch/uzh/ifi/hase/soprafs24/config/WebSocketConfig.java
package ch.uzh.ifi.hase.soprafs24.config;

import ch.uzh.ifi.hase.soprafs24.entity.User;
import ch.uzh.ifi.hase.soprafs24.repository.TeamRepository;
import ch.uzh.ifi.hase.soprafs24.entity.Team; // Import Team
import ch.uzh.ifi.hase.soprafs24.service.UserService;
import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler; // <-- CORRECT IMPORT
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private final SocketHandler myPlainWebSocketHandler;
    private final UserService userService;
    private final TeamRepository teamRepository;

    @Autowired
    public WebSocketConfig(SocketHandler myPlainWebSocketHandler, UserService userService,
                           TeamRepository teamRepository) {
        this.myPlainWebSocketHandler = myPlainWebSocketHandler;
        this.userService = userService;
        this.teamRepository = teamRepository;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(myPlainWebSocketHandler, "/api/ws/updates")
                .addInterceptors(authenticationInterceptor())
                .setAllowedOrigins("*"); // IMPORTANT: Restrict for production
    }

    @Bean
    public HandshakeInterceptor authenticationInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, // <-- Correct type
                                           Map<String, Object> attributes) throws Exception {
                if (request instanceof ServletServerHttpRequest) {
                    ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                    String token = servletRequest.getServletRequest().getParameter("token");

                    if (token != null && !token.isEmpty()) {
                        try {
                            User user = userService.getUserByToken(token);
                            if (user != null) {
                                attributes.put("userId", user.getId());
                                // User can only be in one team
                                Team userTeam = teamRepository.findTeamById(user.getTeamId());
                                if (userTeam != null) {
                                    attributes.put("teamId", userTeam.getId());
                                    log.info("WebSocket handshake authorized for user: {}, userId: {}, teamId: {}",
                                            user.getUsername(), user.getId(), userTeam.getId());
                                } else {
                                    log.info("WebSocket handshake authorized for user: {}, userId: {}. User is not in any team.",
                                            user.getUsername(), user.getId());
                                    // attributes.put("teamId", null); // or don't put it if null
                                }
                                return true;
                            } else {
                                log.warn("WebSocket handshake denied: Invalid token provided.");
                                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                                return false;
                            }
                        } catch (Exception e) {
                            log.error("WebSocket handshake error during token validation: {}", e.getMessage(), e);
                            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                            return false;
                        }
                    } else {
                        log.warn("WebSocket handshake denied: No token provided.");
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                }
                log.warn("WebSocket handshake denied: Request is not a ServletServerHttpRequest or issue in processing.");
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return false;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, // <-- Correct type
                                       Exception exception) {
                if (exception != null) {
                    log.error("Exception after WebSocket handshake: {}", exception.getMessage(), exception);
                }
            }
        };
    }
}