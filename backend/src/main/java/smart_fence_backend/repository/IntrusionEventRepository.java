package smart_fence_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart_fence_backend.entity.IntrusionEvent;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntrusionEventRepository
        extends JpaRepository<IntrusionEvent, Long> {

    Page<IntrusionEvent> findByDevice_IdOrderByDetectedAtDesc(
            Long deviceId,
            Pageable pageable
    );

    List<IntrusionEvent> findByEventStatusOrderByDetectedAtDesc(
            String eventStatus
    );

    Optional<IntrusionEvent> findByDevice_DeviceCodeAndEventId(
            String deviceCode,
            String eventId
    );
}
