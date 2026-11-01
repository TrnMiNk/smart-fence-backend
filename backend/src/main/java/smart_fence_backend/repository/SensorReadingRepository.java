package smart_fence_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart_fence_backend.entity.SensorReading;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SensorReadingRepository
        extends JpaRepository<SensorReading, Long> {

    List<SensorReading> findTop50ByComponent_IdOrderByRecordedAtDesc(
            Long componentId
    );

    Optional<SensorReading> findTop1ByComponent_IdOrderByRecordedAtDesc(
            Long componentId
    );

    List<SensorReading> findByComponent_IdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long componentId,
            LocalDateTime start,
            LocalDateTime end
    );
}