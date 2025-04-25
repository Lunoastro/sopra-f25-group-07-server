package ch.uzh.ifi.hase.soprafs24.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
@Profile("calendar") // This configuration is only active when the "calendar" profile is active
// USE: ./gradlew bootRunDev --args='--spring.profiles.active=calendar'
public class GoogleCalendarConfig {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = System.getProperty("user.home") + "/.taskaway_tokens";

    @Bean
    public NetHttpTransport netHttpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    @Bean
    public GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow(NetHttpTransport httpTransport) throws IOException {
        GoogleClientSecrets clientSecrets = loadClientSecrets();

        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                JSON_FACTORY,
                clientSecrets,
                Collections.singleton(CalendarScopes.CALENDAR))
                .setAccessType("offline")
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .build();
    }

    private GoogleClientSecrets loadClientSecrets() throws IOException {
        String credentialsJson = System.getenv("GOOGLE_CALENDAR_CREDENTIALS");
        if (credentialsJson == null || credentialsJson.isEmpty()) {
            throw new FileNotFoundException("Environment variable 'GOOGLE_CALENDAR_CREDENTIALS' is not set or is empty.");
        }

        try (InputStream in = new ByteArrayInputStream(credentialsJson.getBytes());
             InputStreamReader reader = new InputStreamReader(in)) {
            return GoogleClientSecrets.load(JSON_FACTORY, reader);
        }
    }
}
