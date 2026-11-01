package smart_fence_backend.service;

import org.junit.jupiter.api.Test;
import smart_fence_backend.entity.Device;
import smart_fence_backend.repository.DeviceRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class DeviceServiceStatusTests {

    @Test
    void onlineToOnlinePreservesConfirmedSystemState() {
        Device device = device("ONLINE", "OFF");
        DeviceRepository repository = mock(DeviceRepository.class);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(device));
        when(repository.save(device)).thenReturn(device);

        new DeviceService(repository).updateDeviceStatus(3L, "ONLINE");

        assertEquals("OFF", device.getSystemState());
        assertNotNull(device.getLastSeen());
    }

    @Test
    void offlineToOnlineResetsSystemStateToUnknown() {
        Device device = device("OFFLINE", "UNKNOWN");
        DeviceRepository repository = mock(DeviceRepository.class);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(device));
        when(repository.save(device)).thenReturn(device);

        new DeviceService(repository).updateDeviceStatus(3L, "ONLINE");

        assertEquals("UNKNOWN", device.getSystemState());
        assertNotNull(device.getLastSeen());
    }

    @Test
    void onlineToOfflineResetsSystemStateToUnknown() {
        Device device = device("ONLINE", "ON");
        DeviceRepository repository = mock(DeviceRepository.class);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(device));
        when(repository.save(device)).thenReturn(device);

        new DeviceService(repository).updateDeviceStatus(3L, "OFFLINE");

        assertEquals("UNKNOWN", device.getSystemState());
    }

    @Test
    void offlineToOfflineKeepsUnknown() {
        Device device = device("OFFLINE", "UNKNOWN");
        DeviceRepository repository = mock(DeviceRepository.class);
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(device));
        when(repository.save(device)).thenReturn(device);

        new DeviceService(repository).updateDeviceStatus(3L, "OFFLINE");

        assertEquals("UNKNOWN", device.getSystemState());
    }

    private Device device(String status, String systemState) {
        Device device = new Device();
        device.setId(3L);
        device.setStatus(status);
        device.setSystemState(systemState);
        return device;
    }
}
