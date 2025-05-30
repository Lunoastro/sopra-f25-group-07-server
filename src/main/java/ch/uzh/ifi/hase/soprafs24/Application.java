package ch.uzh.ifi.hase.soprafs24;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RestController
@SpringBootApplication
@EnableTransactionManagement
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    @Value("${cors.allowed.origins:*}") // Default to "*" if property is not set
    private String[] allowedOrigins;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public String helloWorld() {
        return "The application is running!";
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry
                .addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("*");
            }
        };
    }

    @Bean
    public CommandLineRunner checkDatabaseConfig(Environment environment) {
        return args -> {
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles.length == 0) {
                logger.warn("No active profiles found. Defaulting to 'dev'.");
                activeProfiles = new String[]{"dev"};
            }
            logger.info("Active profiles: {}", String.join(", ", activeProfiles));

            try {
                // Retrieve the redirect.uri from the properties file based on the active profile
                String redirectUri = environment.getProperty("REDIRECT_URI");
                if (redirectUri == null) {
                    redirectUri = environment.getProperty("redirect.uri");
                }

                if (redirectUri != null) {
                    logger.info("Using redirect URI: {}", redirectUri);
                } else {
                    logger.error("No redirect URI configured!");
                }

                String datasourceUrl = environment.getProperty("spring.datasource.url");
            
                if (datasourceUrl != null && datasourceUrl.contains("h2")) {
                    logger.warn("H2 DATABASE IN USE - NOT PRODUCTION CONFIG");
                } else if (datasourceUrl != null) {
                    logger.info("Production database configuration detected");
                } else {
                    logger.error("No datasource URL configured!");
                }
            } catch (Exception e) {
                logger.error("Error checking database configuration", e);
            }
        };
    }
}