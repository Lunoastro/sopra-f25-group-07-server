package ch.uzh.ifi.hase.soprafs24.repository;

import ch.uzh.ifi.hase.soprafs24.entity.GoogleToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository("googleTokenRepository")
public interface GoogleTokenRepository extends JpaRepository<GoogleToken, Long> {

}