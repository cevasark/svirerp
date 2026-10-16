package com.svivanrilski.svirerp.roomrental;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import com.svivanrilski.svirerp.governance.Trustee;
import com.svivanrilski.svirerp.governance.TrusteeRepository;
import com.svivanrilski.svirerp.person.Person;
import com.svivanrilski.svirerp.person.PersonService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RoomRentalService {

    public static final Set<String> ALLOWED_STATUSES = Set.of(
            "requested", "slot_confirmed", "deposit_paid", "confirmed", "cancelled", "completed"
    );

    public static final Set<String> ALLOWED_PAYMENT_METHODS = Set.of(
            "cash", "check", "zelle", "card", "other"
    );

    private final RoomRentalRepository rentalRepo;
    private final PersonService personService;
    private final TrusteeRepository trusteeRepo;

    public RoomRentalService(RoomRentalRepository rentalRepo, PersonService personService, TrusteeRepository trusteeRepo) {
        this.rentalRepo = rentalRepo;
        this.personService = personService;
        this.trusteeRepo = trusteeRepo;
    }

    public Page<RoomRental> findAll(Pageable pageable) {
        return rentalRepo.findAll(pageable);
    }

    public Page<RoomRental> findByStatus(String status, Pageable pageable) {
        validateStatus(status);
        return rentalRepo.findByStatus(status, pageable);
    }

    public List<RoomRental> findByRoomAndDateRange(String roomName, OffsetDateTime start, OffsetDateTime end) {
        return rentalRepo.findByRoomAndDateRange(roomName, start, end);
    }

    public List<RoomRental> checkOverlaps(String roomName, OffsetDateTime startTime, OffsetDateTime endTime, UUID excludeId) {
        return rentalRepo.findOverlappingRentals(roomName, startTime, endTime, excludeId);
    }

    public RoomRental findById(UUID id) {
        return rentalRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoomRental", id));
    }

    @Transactional
    public RoomRental create(RoomRental rental) {
        validateTimes(rental.getStartTime(), rental.getEndTime());
        validateStatus(rental.getStatus());
        validatePaymentMethods(rental.getDepositPaymentMethod(), rental.getFinalPaymentMethod());

        // Resolve Renter Person
        if (rental.getRenterPerson() == null || rental.getRenterPerson().getId() == null) {
            throw new IllegalArgumentException("Renter person is required for a room rental.");
        }
        Person renter = personService.findById(rental.getRenterPerson().getId());
        rental.setRenterPerson(renter);

        // Resolve Contact Trustee (optional)
        if (rental.getContactTrustee() != null && rental.getContactTrustee().getId() != null) {
            Trustee trustee = trusteeRepo.findById(rental.getContactTrustee().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trustee", rental.getContactTrustee().getId()));
            rental.setContactTrustee(trustee);
        } else {
            rental.setContactTrustee(null);
        }

        return rentalRepo.save(rental);
    }

    @Transactional
    public RoomRental update(UUID id, RoomRental patch) {
        RoomRental existing = findById(id);

        validateTimes(patch.getStartTime(), patch.getEndTime());
        validateStatus(patch.getStatus());
        validatePaymentMethods(patch.getDepositPaymentMethod(), patch.getFinalPaymentMethod());

        existing.setRoomName(patch.getRoomName() != null ? patch.getRoomName() : "Parish Hall");
        existing.setEventTitle(patch.getEventTitle());
        existing.setStartTime(patch.getStartTime());
        existing.setEndTime(patch.getEndTime());
        existing.setEstimatedAttendees(patch.getEstimatedAttendees());
        existing.setStatus(patch.getStatus());
        existing.setTotalFee(patch.getTotalFee());
        existing.setDepositAmount(patch.getDepositAmount());
        existing.setDepositPaidDate(patch.getDepositPaidDate());
        existing.setDepositPaymentMethod(patch.getDepositPaymentMethod());
        existing.setFinalAmountPaid(patch.getFinalAmountPaid());
        existing.setFinalPaymentDate(patch.getFinalPaymentDate());
        existing.setFinalPaymentMethod(patch.getFinalPaymentMethod());
        existing.setNotes(patch.getNotes());

        if (patch.getRenterPerson() != null && patch.getRenterPerson().getId() != null) {
            Person renter = personService.findById(patch.getRenterPerson().getId());
            existing.setRenterPerson(renter);
        }

        if (patch.getContactTrustee() != null && patch.getContactTrustee().getId() != null) {
            Trustee trustee = trusteeRepo.findById(patch.getContactTrustee().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trustee", patch.getContactTrustee().getId()));
            existing.setContactTrustee(trustee);
        } else {
            existing.setContactTrustee(null);
        }

        return rentalRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        if (!rentalRepo.existsById(id)) {
            throw new ResourceNotFoundException("RoomRental", id);
        }
        rentalRepo.deleteById(id);
    }

    private void validateTimes(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start time and end time are required.");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Allowed: " + ALLOWED_STATUSES);
        }
    }

    private void validatePaymentMethods(String depositMethod, String finalMethod) {
        if (depositMethod != null && !depositMethod.isBlank() && !ALLOWED_PAYMENT_METHODS.contains(depositMethod)) {
            throw new IllegalArgumentException("Invalid deposit payment method: " + depositMethod + ". Allowed: " + ALLOWED_PAYMENT_METHODS);
        }
        if (finalMethod != null && !finalMethod.isBlank() && !ALLOWED_PAYMENT_METHODS.contains(finalMethod)) {
            throw new IllegalArgumentException("Invalid final payment method: " + finalMethod + ". Allowed: " + ALLOWED_PAYMENT_METHODS);
        }
    }
}
