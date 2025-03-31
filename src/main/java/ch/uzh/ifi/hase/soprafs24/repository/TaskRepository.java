package ch.uzh.ifi.hase.soprafs24.repository;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository("taskRepository")
public interface TaskRepository extends JpaRepository<Task, Long> {
    Task findByTaskId(Long taskId);

}