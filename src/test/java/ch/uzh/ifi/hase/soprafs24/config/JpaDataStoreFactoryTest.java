package ch.uzh.ifi.hase.soprafs24.config;
import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;
import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;
import com.google.api.client.util.store.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;




class JpaDataStoreFactoryTest {

    private GoogleTokenRepository mockRepo;
    private JpaDataStoreFactory factory;
    private DataStore<String> dataStore;

    @BeforeEach
    void setup() {
        mockRepo = mock(GoogleTokenRepository.class);
        factory = new JpaDataStoreFactory();
        // Inject mock repo
        factory.repo = mockRepo;
        dataStore = factory.createDataStore("test");
    }

    @Test
    void testGetIdAndFactory() throws java.io.IOException {
        assertEquals("test", dataStore.getId());
        assertEquals(factory, dataStore.getDataStoreFactory());
    }

    @Test
    void testSizeAndIsEmpty() throws java.io.IOException {
        when(mockRepo.count()).thenReturn(0L);
        assertTrue(dataStore.isEmpty());
        assertEquals(0, dataStore.size());

        when(mockRepo.count()).thenReturn(2L);
        assertFalse(dataStore.isEmpty());
        assertEquals(2, dataStore.size());
    }

    @Test
    void testContainsKey() throws java.io.IOException {
        when(mockRepo.existsById(1L)).thenReturn(true);
        when(mockRepo.existsById(2L)).thenReturn(false);
        assertTrue(dataStore.containsKey("1"));
        assertFalse(dataStore.containsKey("2"));
    }

    @Test
    void testContainsValue() throws java.io.IOException {
        GoogleToken token = new GoogleToken(1L, "a", "b", 123L);
        when(mockRepo.findAll()).thenReturn(Collections.singletonList(token));
        String json = "{\"accessToken\":\"a\",\"refreshToken\":\"b\",\"expirationTime\":123}";
        assertTrue(dataStore.containsValue(json));
        assertFalse(dataStore.containsValue("{\"accessToken\":\"x\",\"refreshToken\":\"y\",\"expirationTime\":999}"));
    }

    @Test
    void testKeySetAndValues() throws java.io.IOException {
        GoogleToken t1 = new GoogleToken(1L, "a", "b", 123L);
        GoogleToken t2 = new GoogleToken(2L, "c", "d", 456L);
        when(mockRepo.findAll()).thenReturn(Arrays.asList(t1, t2));
        Set<String> keys = dataStore.keySet();
        assertTrue(keys.contains("1"));
        assertTrue(keys.contains("2"));
        Collection<String> values = dataStore.values();
        assertEquals(2, values.size());
        assertTrue(values.contains("{\"accessToken\":\"a\",\"refreshToken\":\"b\",\"expirationTime\":123}"));
        assertTrue(values.contains("{\"accessToken\":\"c\",\"refreshToken\":\"d\",\"expirationTime\":456}"));
    }

    @Test
    void testGet() throws java.io.IOException {
        GoogleToken token = new GoogleToken(1L, "a", "b", 123L);
        when(mockRepo.findById(1L)).thenReturn(Optional.of(token));
        String json = "{\"accessToken\":\"a\",\"refreshToken\":\"b\",\"expirationTime\":123}";
        assertEquals(json, dataStore.get("1"));
        when(mockRepo.findById(2L)).thenReturn(Optional.empty());
        assertNull(dataStore.get("2"));
    }

    @Test
    void testSet() throws java.io.IOException {
        String json = "{\"accessToken\":\"a\",\"refreshToken\":\"b\",\"expirationTime\":123}";
        dataStore.set("1", json);
        ArgumentCaptor<GoogleToken> captor = ArgumentCaptor.forClass(GoogleToken.class);
        verify(mockRepo).save(captor.capture());
        GoogleToken saved = captor.getValue();
        assertEquals(1L, saved.getId());
        assertEquals("a", saved.getAccessToken());
        assertEquals("b", saved.getRefreshToken());
        assertEquals(123L, saved.getExpirationTime());
    }

    @Test
    void testClear() throws java.io.IOException {
        dataStore.clear();
        verify(mockRepo).deleteAll();
    }

    @Test
    void testDelete() throws java.io.IOException {
        dataStore.delete("1");
        verify(mockRepo).deleteById(1L);
    }

    @Test
    void testSet_invalidJson_throwsRuntimeException() {
        String invalidJson = "{this is not valid json}";
        assertThrows(RuntimeException.class, () -> dataStore.set("1", invalidJson));
    }
}
