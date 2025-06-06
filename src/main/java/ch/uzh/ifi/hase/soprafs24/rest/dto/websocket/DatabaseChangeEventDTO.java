package ch.uzh.ifi.hase.soprafs24.rest.dto.websocket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseChangeEventDTO<T> {
    private String entityType; // e.g., "task", "user", "team"
    private T payload;         // payload data e.g a list of all tasks

    @Override
    public String toString() {
        return "DatabaseChangeEventDTO{" +
               "entityType='" + entityType + '\'' +
               ", payload=" + (payload != null ? payload.toString() : "null") +
               '}';
    }
}
