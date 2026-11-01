package smart_fence_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import smart_fence_backend.entity.DeviceCommand;
import jakarta.persistence.LockModeType;

import java.util.List;

@Repository
public interface DeviceCommandRepository
        extends JpaRepository<DeviceCommand, Long> {

    List<DeviceCommand> findByDevice_IdOrderByCreatedAtDesc(
            Long deviceId
    );

    List<DeviceCommand> findByCommandStatus(String commandStatus);

    boolean existsByDevice_IdAndCommandTypeInAndCommandStatusIn(
            Long deviceId,
            List<String> commandTypes,
            List<String> commandStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DeviceCommand c join fetch c.device where c.id = :id")
    java.util.Optional<DeviceCommand> findByIdForUpdate(@Param("id") Long id);
}
