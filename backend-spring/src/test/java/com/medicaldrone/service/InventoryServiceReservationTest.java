package com.medicaldrone.service;

import com.medicaldrone.model.HospitalInventory;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.model.MedicalItem;
import com.medicaldrone.repository.HospitalInventoryRepository;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.repository.MedicalItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceReservationTest {
    @Mock private HospitalInventoryRepository inventoryRepository;
    @Mock private MedicalCenterRepository medicalCenterRepository;
    @Mock private MedicalItemRepository medicalItemRepository;
    @Mock private RedisLockService redisLockService;

    private InventoryService service;
    private HospitalInventory inventory;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, medicalCenterRepository, medicalItemRepository, redisLockService);
        MedicalCenter hospital = new MedicalCenter();
        hospital.setId(4L);
        MedicalItem item = new MedicalItem();
        item.setId(9L);
        inventory = new HospitalInventory(hospital, item, 8, 2, null);
    }

    @Test
    void reservationAtomicallyMovesQuantityFromAvailableToReservedAndDispatchGateCanSeeIt() {
        when(redisLockService.acquireLock(anyString(), anyString(), eq(5L))).thenReturn(true);
        when(inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(4L, 9L)).thenReturn(Optional.of(inventory));
        assertTrue(service.reserveStock(4L, 9L, 5));

        assertEquals(3, inventory.getAvailableQuantity());
        assertEquals(7, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
        verify(redisLockService).releaseLock(startsWith("lock:inventory:4:9"), anyString());
        assertTrue(service.hasReservedStockForUpdate(4L, 9L, 7));
    }

    @Test
    void insufficientStockDoesNotChangeOrSaveInventory() {
        when(redisLockService.acquireLock(anyString(), anyString(), eq(5L))).thenReturn(true);
        when(inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(4L, 9L)).thenReturn(Optional.of(inventory));
        assertFalse(service.reserveStock(4L, 9L, 9));

        assertEquals(8, inventory.getAvailableQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void nonPositiveReservationIsRejectedBeforeTakingLock() {
        assertThrows(IllegalArgumentException.class, () -> service.reserveStock(4L, 9L, 0));
        verifyNoInteractions(redisLockService);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void lockContentionFailsClosedWithoutReadingOrChangingStock() {
        when(redisLockService.acquireLock(anyString(), anyString(), eq(5L))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.reserveStock(4L, 9L, 1));

        verify(inventoryRepository, never()).findByHospitalIdAndMedicalItemIdForUpdate(anyLong(), anyLong());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void releaseClampsToReservedQuantityAndCannotMintStock() {
        when(inventoryRepository.findByHospitalIdAndMedicalItemIdForUpdate(4L, 9L)).thenReturn(Optional.of(inventory));
        service.releaseReservedStock(4L, 9L, 9);

        assertEquals(10, inventory.getAvailableQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }
}
