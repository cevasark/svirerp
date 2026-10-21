package com.svivanrilski.svirerp.roomrental;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.svivanrilski.svirerp.governance.Trustee;
import com.svivanrilski.svirerp.governance.TrusteeRepository;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomRentalServiceTest {

    @Mock private RoomRentalRepository rentalRepo;
    @Mock private PersonService personService;
    @Mock private TrusteeRepository trusteeRepo;

    @InjectMocks
    private RoomRentalService service;

    @Test
    void create_validRental_resolvesEntitiesAndSaves() {
        UUID renterId = UUID.randomUUID();
        UUID trusteeId = UUID.randomUUID();
        Person renter = Person.builder().id(renterId).firstName("Ivan").lastName("Dimitrov").build();
        Trustee trustee = Trustee.builder().id(trusteeId).build();

        OffsetDateTime start = OffsetDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);

        RoomRental rental = RoomRental.builder()
                .roomName("Parish Hall")
                .renterPerson(Person.builder().id(renterId).build())
                .contactTrustee(Trustee.builder().id(trusteeId).build())
                .eventTitle("Parish Reception")
                .startTime(start)
                .endTime(end)
                .status("requested")
                .totalFee(new BigDecimal("250.00"))
                .depositAmount(new BigDecimal("100.00"))
                .depositPaymentMethod("cash")
                .contractUrl("https://example.com/contracts/rental-123.pdf")
                .build();

        when(personService.findById(renterId)).thenReturn(renter);
        when(trusteeRepo.findById(trusteeId)).thenReturn(Optional.of(trustee));
        when(rentalRepo.save(any(RoomRental.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomRental saved = service.create(rental);

        assertThat(saved).isNotNull();
        assertThat(saved.getRenterPerson()).isEqualTo(renter);
        assertThat(saved.getContactTrustee()).isEqualTo(trustee);
        assertThat(saved.getContractUrl()).isEqualTo("https://example.com/contracts/rental-123.pdf");
        verify(rentalRepo).save(rental);
    }

    @Test
    void update_validPatch_updatesContractUrlAndFields() {
        UUID rentalId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);

        RoomRental existing = RoomRental.builder()
                .id(rentalId)
                .roomName("Parish Hall")
                .eventTitle("Old Title")
                .startTime(start)
                .endTime(end)
                .status("requested")
                .build();

        RoomRental patch = RoomRental.builder()
                .roomName("Fellowship Room")
                .eventTitle("New Title")
                .startTime(start)
                .endTime(end)
                .status("slot_confirmed")
                .contractUrl("https://example.com/contract-signed.pdf")
                .build();

        when(rentalRepo.findById(rentalId)).thenReturn(Optional.of(existing));
        when(rentalRepo.save(any(RoomRental.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomRental updated = service.update(rentalId, patch);

        assertThat(updated.getEventTitle()).isEqualTo("New Title");
        assertThat(updated.getContractUrl()).isEqualTo("https://example.com/contract-signed.pdf");
        assertThat(updated.getStatus()).isEqualTo("slot_confirmed");
        verify(rentalRepo).save(existing);
    }

    @Test
    void create_startTimeAfterEndTime_throwsException() {
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC);

        RoomRental rental = RoomRental.builder()
                .renterPerson(Person.builder().id(UUID.randomUUID()).build())
                .startTime(start)
                .endTime(end)
                .build();

        assertThatThrownBy(() -> service.create(rental))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start time must be before end time");
    }

    @Test
    void create_invalidStatus_throwsException() {
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);

        RoomRental rental = RoomRental.builder()
                .renterPerson(Person.builder().id(UUID.randomUUID()).build())
                .startTime(start)
                .endTime(end)
                .status("unknown_status")
                .build();

        assertThatThrownBy(() -> service.create(rental))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");
    }

    @Test
    void create_invalidPaymentMethod_throwsException() {
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);

        RoomRental rental = RoomRental.builder()
                .renterPerson(Person.builder().id(UUID.randomUUID()).build())
                .startTime(start)
                .endTime(end)
                .status("deposit_paid")
                .depositPaymentMethod("crypto")
                .build();

        assertThatThrownBy(() -> service.create(rental))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid deposit payment method");
    }

    @Test
    void checkOverlaps_delegatesToRepository() {
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);

        RoomRental existing = RoomRental.builder().id(UUID.randomUUID()).build();
        when(rentalRepo.findOverlappingRentals("Parish Hall", start, end, null))
                .thenReturn(List.of(existing));

        List<RoomRental> overlaps = service.checkOverlaps("Parish Hall", start, end, null);

        assertThat(overlaps).containsExactly(existing);
    }
}
