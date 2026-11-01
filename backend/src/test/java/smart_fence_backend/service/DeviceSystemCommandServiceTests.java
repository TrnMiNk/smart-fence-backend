package smart_fence_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceCommand;
import smart_fence_backend.mqtt.MqttService;
import smart_fence_backend.dto.DeviceCommandResponse;
import smart_fence_backend.dto.DeviceCommandAckPayload;
import smart_fence_backend.mqtt.PendingSystemAckRegistry;
import smart_fence_backend.repository.DeviceCommandRepository;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.DeviceRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.*;

class DeviceSystemCommandServiceTests {

    private DeviceCommandRepository commandRepository;
    private DeviceRepository deviceRepository;
    private DeviceCommandService service;
    private MqttService mqttService;
    private SystemCommandTransactionService systemCommandTransactions;
    private PendingSystemAckRegistry pendingSystemAckRegistry;
    private Device device;
    private DeviceCommand command;

    @BeforeEach
    void setUp() {
        commandRepository = mock(DeviceCommandRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        DeviceComponentRepository componentRepository = mock(DeviceComponentRepository.class);
        mqttService = mock(MqttService.class);
        systemCommandTransactions = mock(SystemCommandTransactionService.class);
        pendingSystemAckRegistry = new PendingSystemAckRegistry();
        service = new DeviceCommandService(
                commandRepository, deviceRepository, componentRepository, mqttService,
                systemCommandTransactions, pendingSystemAckRegistry);

        device = new Device();
        device.setId(11L);
        device.setDeviceCode("SF-11");
        device.setStatus("ONLINE");
        device.setSystemState("ON");

        command = new DeviceCommand();
        command.setDevice(device);
        command.setComponent(null);
        command.setCommandType("SYSTEM_OFF");
        command.setCommandStatus("SENT");
    }

    @Test
    void successfulSystemAckUpdatesConfirmedState() {
        arrangeAck();

        service.processMqttAck(41L, "SF-11", "SYSTEM_OFF", "SUCCESS", "OFF");

        assertEquals("OFF", device.getSystemState());
        assertEquals("SUCCESS", command.getCommandStatus());
        verify(deviceRepository).save(device);
    }

    @Test
    void failedSystemAckDoesNotChangeSystemState() {
        arrangeAck();

        service.processMqttAck(41L, "SF-11", "SYSTEM_OFF", "FAILED", "ON");

        assertEquals("ON", device.getSystemState());
        assertEquals("FAILED", command.getCommandStatus());
        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void successfulAckWithWrongTargetIsRejected() {
        arrangeAck();

        assertThrows(IllegalArgumentException.class,
                () -> service.processMqttAck(
                        41L, "SF-11", "SYSTEM_OFF", "SUCCESS", "ON"));

        assertEquals("ON", device.getSystemState());
        assertEquals("SENT", command.getCommandStatus());
    }

    @Test
    void systemCommandCannotBeCompletedThroughRestStatusApi() {
        when(commandRepository.findById(41L)).thenReturn(Optional.of(command));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateCommandStatus(41L, "SUCCESS"));
        verify(commandRepository, never()).save(any(DeviceCommand.class));
    }

    @Test
    void duplicateSuccessfulAckIsIdempotent() {
        command.setCommandStatus("SUCCESS");
        command.setAckSystemState("OFF");
        device.setSystemState("OFF");
        arrangeAck();

        service.processMqttAck(41L, "SF-11", "SYSTEM_OFF", "SUCCESS", "OFF");

        verify(deviceRepository, never()).save(any(Device.class));
        verify(commandRepository, never()).save(any(DeviceCommand.class));
    }

    @Test
    void duplicateFailedAckMustMatchRecordedSystemState() {
        arrangeAck();
        service.processMqttAck(41L, "SF-11", "SYSTEM_OFF", "FAILED", "ON");
        assertEquals("ON", command.getAckSystemState());

        when(commandRepository.findById(41L)).thenReturn(Optional.of(command));
        when(commandRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(command));
        service.processMqttAck(41L, "SF-11", "SYSTEM_OFF", "FAILED", "ON");

        assertThrows(IllegalArgumentException.class,
                () -> service.processMqttAck(
                        41L, "SF-11", "SYSTEM_OFF", "FAILED", "UNKNOWN"));
    }

    @Test
    void immediateAckCanBeHandledDuringPublishAfterSentCommit() throws Exception {
        var sentResponse = new DeviceCommandResponse(
                41L, 11L, null, "SYSTEM_OFF", "SENT", null, null);
        var completedResponse = new DeviceCommandResponse(
                41L, 11L, null, "SYSTEM_OFF", "SUCCESS", null, null);
        when(systemCommandTransactions.reserve(11L, "SYSTEM_OFF"))
                .thenReturn(new SystemCommandTransactionService.Reservation(41L, "SF-11"));
        when(systemCommandTransactions.markSent(41L)).thenReturn(sentResponse);
        when(systemCommandTransactions.getResponse(41L)).thenReturn(completedResponse);
        arrangeAck();
        pendingSystemAckRegistry.setAckProcessor((deviceCode, ack) ->
                service.processMqttAck(ack.getCommandId(), deviceCode,
                        ack.getCommandType(), ack.getStatus(), ack.getSystemState()));
        doAnswer(invocation -> {
            DeviceCommandAckPayload ack = new DeviceCommandAckPayload();
            ack.setCommandId(41L);
            ack.setCommandType("SYSTEM_OFF");
            ack.setStatus("SUCCESS");
            ack.setSystemState("OFF");
            assertEquals(PendingSystemAckRegistry.DeferResult.DEFERRED,
                    pendingSystemAckRegistry.deferIfDispatching(41L, "SF-11", ack));
            return null;
        }).when(mqttService).publish(any(), any());

        var response = service.createSystemCommand(11L, "system_off");

        assertEquals("SUCCESS", response.getCommandStatus());
        assertEquals("SYSTEM_OFF", response.getCommandType());
        assertEquals(null, response.getComponentId());
        assertEquals("OFF", device.getSystemState());
        verify(mqttService).publish(eq("smart-fence/SF-11/commands"),
                contains("\"command\":\"SYSTEM_OFF\""));
        var order = inOrder(systemCommandTransactions, mqttService);
        order.verify(systemCommandTransactions).reserve(11L, "SYSTEM_OFF");
        order.verify(mqttService).publish(eq("smart-fence/SF-11/commands"), any());
        order.verify(systemCommandTransactions).markSent(41L);
        order.verify(systemCommandTransactions).getResponse(41L);
    }

    @Test
    void markSentFailureRetriesAndDoesNotProcessBufferedSuccessWhilePending() throws Exception {
        command.setCommandStatus("PENDING");
        when(systemCommandTransactions.reserve(11L, "SYSTEM_OFF"))
                .thenReturn(new SystemCommandTransactionService.Reservation(41L, "SF-11"));
        arrangeAck();
        pendingSystemAckRegistry.setAckProcessor((deviceCode, ack) ->
                service.processMqttAck(ack.getCommandId(), deviceCode,
                        ack.getCommandType(), ack.getStatus(), ack.getSystemState()));
        doAnswer(invocation -> {
            DeviceCommandAckPayload ack = new DeviceCommandAckPayload();
            ack.setCommandId(41L);
            ack.setCommandType("SYSTEM_OFF");
            ack.setStatus("SUCCESS");
            ack.setSystemState("OFF");
            assertEquals(PendingSystemAckRegistry.DeferResult.DEFERRED,
                    pendingSystemAckRegistry.deferIfDispatching(41L, "SF-11", ack));
            return null;
        }).when(mqttService).publish(any(), any());
        when(systemCommandTransactions.markSent(41L))
                .thenThrow(new IllegalStateException("commit failed"))
                .thenThrow(new IllegalStateException("commit failed"))
                .thenThrow(new IllegalStateException("commit failed"));

        assertThrows(IllegalStateException.class,
                () -> service.createSystemCommand(11L, "SYSTEM_OFF"));

        assertEquals("PENDING", command.getCommandStatus());
        assertEquals("ON", device.getSystemState());
        verify(systemCommandTransactions, times(3)).markSent(41L);
        verify(systemCommandTransactions, never()).markFailed(41L);
    }

    @Test
    void offlineDeviceCannotReceiveSystemCommand() {
        when(systemCommandTransactions.reserve(11L, "SYSTEM_ON"))
                .thenThrow(new IllegalArgumentException("Device must be ONLINE"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createSystemCommand(11L, "SYSTEM_ON"));
        verifyNoInteractions(mqttService);
    }

    @Test
    void activeSystemCommandPreventsAnotherCommand() {
        when(systemCommandTransactions.reserve(11L, "SYSTEM_ON"))
                .thenThrow(new IllegalArgumentException("A system command is already pending"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createSystemCommand(11L, "SYSTEM_ON"));
        verifyNoInteractions(mqttService);
    }

    private void arrangeAck() {
        when(commandRepository.findById(41L)).thenReturn(Optional.of(command));
        when(deviceRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(device));
        when(commandRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(command));
        when(commandRepository.save(any(DeviceCommand.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
