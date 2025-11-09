package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.CreateZoneRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateZoneRequest;
import com.example.accesssystem.api.dto.ZoneResponse;
import com.example.accesssystem.service.ZoneRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin controller for zone CRUD operations.
 */
@RestController
@RequestMapping("/admin/zones")
public class ZoneAdminController {
    
    private final ZoneRepository zoneRepository;
    
    public ZoneAdminController(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }
    
    @PostMapping
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody CreateZoneRequest request) {
        UUID id = zoneRepository.create(request.code());
        ZoneRepository.ZoneRecord record = zoneRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Zone not found after creation"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ZoneResponse(record.id(), record.code(), record.createdAt()));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponse> getZone(@PathVariable UUID id) {
        return zoneRepository.findById(id)
            .map(record -> ResponseEntity.ok(
                new ZoneResponse(record.id(), record.code(), record.createdAt())
            ))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<PageResponse<ZoneResponse>> listZones(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var records = zoneRepository.findAll(offset, limit);
        var responses = records.stream()
            .map(record -> new ZoneResponse(record.id(), record.code(), record.createdAt()))
            .toList();
        long total = zoneRepository.count();
        return ResponseEntity.ok(new PageResponse<>(responses, total, offset, limit));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponse> updateZone(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateZoneRequest request) {
        boolean updated = zoneRepository.update(id, request.code());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        ZoneRepository.ZoneRecord record = zoneRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Zone not found after update"));
        return ResponseEntity.ok(new ZoneResponse(record.id(), record.code(), record.createdAt()));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        boolean deleted = zoneRepository.deleteById(id);
        return deleted 
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}

