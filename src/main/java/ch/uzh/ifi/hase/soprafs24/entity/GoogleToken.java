package ch.uzh.ifi.hase.soprafs24.entity;

import javax.persistence.*;
import java.io.Serializable;

/**
 * Internal representation of a Google OAuth token
 */
@Entity
@Table(name = "GOOGLE_TOKEN")
public class GoogleToken implements Serializable {

    private static final long serialVersionUID = 1L;

    public GoogleToken() {
        // Default constructor for JPA
    }

    public GoogleToken(Long id, String accessToken, String refreshToken, Long expirationTime) {
        this.id = id;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expirationTime = expirationTime;
    }

    @Id
    @Column(updatable = false, nullable = false)
    private Long id;  // The userId

    @Column(nullable = false)
    private String accessToken;

    @Column(nullable = false)
    private String refreshToken;

    @Column(nullable = false)
    private Long expirationTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
    }
}
