// src/main/java/ch/uzh/ifi/hase/soprafs24/config/WebSocketConfig.java
package ch.uzh.ifi.hase.soprafs24.config;

import ch.uzh.ifi.hase.soprafs24.websocket.SocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
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

    @Autowired
    public WebSocketConfig(SocketHandler myPlainWebSocketHandler) {
        this.myPlainWebSocketHandler = myPlainWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(myPlainWebSocketHandler, "/api/ws/updates")
                .addInterceptors(initialHandshakeInterceptor())
                .setAllowedOrigins("*"); // IMPORTANT: Restrict for production
    }

    @Bean
    public HandshakeInterceptor initialHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler,
                                           Map<String, Object> attributes) throws Exception {
                // Allow the handshake to proceed. Authentication will occur via the first message.
                log.info("WebSocket initial handshake allowed for session from {}. Awaiting auth message.", request.getRemoteAddress());
                attributes.put("authenticated", false); // Mark session as not yet authenticated
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Exception exception) {
                if (exception != null) {
                    log.error("Exception after WebSocket initial handshake: {}", exception.getMessage(), exception);
                }
            }
        };
    }
}