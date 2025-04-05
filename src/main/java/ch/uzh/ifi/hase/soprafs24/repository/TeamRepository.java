package ch.uzh.ifi.hase.soprafs24.repository;

import ch.uzh.ifi.hase.soprafs24.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository("teamRepository")
public interface TeamRepository extends JpaRepository<Team, Long> {
  Team findByName(String name);

  Team findByCode(String code);

  Team findTeamById(Long id);
}
