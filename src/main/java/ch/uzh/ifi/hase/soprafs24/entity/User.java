package ch.uzh.ifi.hase.soprafs24.entity;

import ch.uzh.ifi.hase.soprafs24.constant.ColorID;
import ch.uzh.ifi.hase.soprafs24.constant.UserStatus;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Internal User Representation
 * This class composes the internal representation of the user and defines how
 * the user is stored in the database.
 * Every variable will be mapped into a database field with the @Column
 * annotation
 * - nullable = false -> this cannot be left empty
 * - unique = true -> this value must be unqiue across the database -> composes
 * the primary key
 */
@Entity
@Table(name = "USERS")
public class User implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false, unique = true)
  private String token;

  @Column(nullable = false)
  private UserStatus status;

  @Column(nullable = false)
  private Date creationDate;

  @Column
  private Date birthDate;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private int xp;

  @Column(nullable = false)
  private int level;

  @Enumerated(EnumType.STRING)
  @Column(nullable = true)
  private ColorID color;

  @Column
  private long teamId;


  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public Date getCreationDate(){
    return creationDate;
  }

  public void setCreationDate(Date creationDate) {
    this.creationDate = creationDate;
  }

  public Date getBirthDate(){
    return birthDate;
  }

  public void setBirthDate(Date birthDate) {
    this.birthDate = birthDate;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password; 
  }

  public int getXp() {
    return xp;
  }

  public void setXp(int xp) {
    this.xp = xp;
  }

  public int getLevel() {
    return level;
  }

  public void setLevel(int level) {
    this.level = level;
  }

  public ColorID getColor() {
    return color;
  }

  public void setColor(ColorID color) {
    this.color = color;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

}
