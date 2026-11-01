package smart_fence_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart_fence_backend.entity.DeviceComponent;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceComponentRepository
        extends JpaRepository<DeviceComponent, Long> {

    List<DeviceComponent> findByDevice_Id(Long deviceId);

    Optional<DeviceComponent> findByDevice_IdAndComponentCode(
            Long deviceId, String componentCode
    );

    List<DeviceComponent> findByComponentType(String componentType);
}