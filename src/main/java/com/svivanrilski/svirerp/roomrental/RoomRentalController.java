package com.svivanrilski.svirerp.roomrental;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/room-rentals")
public class RoomRentalController {

    private final RoomRentalService service;

    public RoomRentalController(RoomRentalService service) {
        this.service = service;
    }

    @GetMapping
    public Page<RoomRental> listRentals(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return service.findByStatus(status, pageable);
        }
        return service.findAll(pageable);
    }

    @GetMapping("/schedule")
    public List<RoomRental> getSchedule(
            @RequestParam(required = false) String roomName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return service.findByRoomAndDateRange(roomName, start, end);
    }

    @GetMapping("/check-overlap")
    public List<RoomRental> checkOverlap(
            @RequestParam(defaultValue = "Parish Hall") String roomName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @RequestParam(required = false) UUID excludeId) {
        return service.checkOverlaps(roomName, start, end, excludeId);
    }

    @GetMapping("/{id}")
    public RoomRental getRental(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<RoomRental> createRental(@Valid @RequestBody RoomRental rental) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(rental));
    }

    @PutMapping("/{id}")
    public RoomRental updateRental(@PathVariable UUID id, @Valid @RequestBody RoomRental rental) {
        return service.update(id, rental);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRental(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
