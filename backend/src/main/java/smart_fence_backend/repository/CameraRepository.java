package smart_fence_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart_fence_backend.entity.Camera;

import java.util.List;
import java.util.Optional;

@Repository
public interface CameraRepository extends JpaRepository<Camera, Long> {

    Optional<Camera> findByComponent_Id(Long componentId);

    List<Camera> findByStatus(String status);
}