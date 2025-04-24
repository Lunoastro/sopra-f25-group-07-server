package ch.uzh.ifi.hase.soprafs24.config;

import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;
import ch.uzh.ifi.hase.soprafs24.repository.GoogleTokenRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bridges Google’s OAuth DataStore API to our PostgreSQL / H2 database.
 * One row in table GOOGLE_TOKEN holds the JSON credentials for a single user.
 */
@Component
public class JpaDataStoreFactory extends AbstractDataStoreFactory {

    @Autowired
    private GoogleTokenRepository repo;

    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) {

        return new DataStore<>() {

            // ------------- required by interface -------------

            @Override public DataStoreFactory getDataStoreFactory() {
                return JpaDataStoreFactory.this;                  // reference to outer bean
            }

            @Override public String getId() { return id; }

            // ---------------- key/value helpers --------------

            @Override public int size()        { return (int) repo.count(); }
            @Override public boolean isEmpty() { return repo.count() == 0; }

            @Override
            public boolean containsKey(String key) {
                return repo.existsById(Long.valueOf(key));
            }

            @Override
            public boolean containsValue(V value) {
                String jsonValue = value.toString();
                return repo.findAll().stream()
                           .anyMatch(t -> Objects.equals(toJson(t), jsonValue));
            }

            @Override
            public Set<String> keySet() {
                return repo.findAll().stream()
                           .map(t -> String.valueOf(t.getId()))
                           .collect(Collectors.toSet());
            }

            @Override
            public Collection<V> values() {
                return repo.findAll().stream()
                        .map(t -> cast(toJson(t)))
                        .collect(Collectors.toList());
            }

            private String toJson(GoogleToken token) {
                return String.format("{\"accessToken\":\"%s\",\"refreshToken\":\"%s\",\"expirationTime\":%d}",
                        token.getAccessToken(),
                        token.getRefreshToken(),
                        token.getExpirationTime());
            }


            // ----------------- core CRUD ---------------------

            @Override
            public V get(String key) {
                return repo.findById(Long.valueOf(key))
                        .map(this::toJson)
                        .map(this::cast)
                        .orElse(null);
            }


            @Override
            public DataStore<V> set(String key, V value) {
                Map<String, Object> tokenMap = parseJson(value.toString());
                GoogleToken token = new GoogleToken(
                        Long.valueOf(key),
                        (String) tokenMap.get("accessToken"),
                        (String) tokenMap.get("refreshToken"),
                        Long.valueOf(tokenMap.get("expirationTime").toString())
                );
                repo.save(token);
                return this;
            }

            private Map<String, Object> parseJson(String json) {
                try {
                    return new ObjectMapper().readValue(json, new TypeReference<>() {});
                } catch (IOException e) {
                    throw new RuntimeException("Failed to parse token JSON", e);
                }
            }


            @Override
            public DataStore<V> clear() {
                repo.deleteAll();
                return this;
            }

            @Override
            public DataStore<V> delete(String key) {
                repo.deleteById(Long.valueOf(key));
                return this;
            }

            // ------------- helper to silence generic cast -------------
            @SuppressWarnings("unchecked")
            private V cast(Object o) {
                return (V) o;
            }
        };
    }
}
