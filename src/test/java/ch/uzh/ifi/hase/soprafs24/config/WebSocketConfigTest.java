package ch.uzh.ifi.hase.soprafs24.config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;





class WebSocketConfigTest {

    private WebSocketConfig config;
    private WebSocketHandler mockHandler;

    @BeforeEach
    void setup() {
        
        config = new WebSocketConfig(null);
        mockHandler = mock(WebSocketHandler.class);
    }

    @Test
    void testInitialHandshakeInterceptor_beforeHandshake_setsAuthenticatedFalseAndReturnsTrue() throws Exception {
        HandshakeInterceptor interceptor = config.initialHandshakeInterceptor();
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, mockHandler, attributes);

        assertTrue(result);
        assertTrue(attributes.containsKey("authenticated"));
        assertEquals(false, attributes.get("authenticated"));
    }

    @Test
    void testInitialHandshakeInterceptor_afterHandshake_logsErrorWhenException() {
        HandshakeInterceptor interceptor = config.initialHandshakeInterceptor();
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        
        InetSocketAddress addr = new InetSocketAddress("localhost", 8080);
        when(request.getRemoteAddress()).thenReturn(addr);

        Exception ex = new Exception("test error");
        
        assertDoesNotThrow(() -> interceptor.afterHandshake(request, response, mockHandler, ex));
    }

    @Test
    void testInitialHandshakeInterceptor_afterHandshake_logsInfoWhenNoException() {
        HandshakeInterceptor interceptor = config.initialHandshakeInterceptor();
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);

        InetSocketAddress addr = new InetSocketAddress("localhost", 8080);
        when(request.getRemoteAddress()).thenReturn(addr);

        
        assertDoesNotThrow(() -> interceptor.afterHandshake(request, response, mockHandler, null));
    }
}
