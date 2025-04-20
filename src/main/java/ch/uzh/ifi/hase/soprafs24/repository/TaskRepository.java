package ch.uzh.ifi.hase.soprafs24.repository;

import ch.uzh.ifi.hase.soprafs24.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;



@Repository("taskRepository")
public interface TaskRepository extends JpaRepository<Task, Long> {
    Task findTaskById(Long id);

    Task findTaskByName(String name);

    List<Task> findTaskByIsAssignedTo(Long id); //user id goes in here
}