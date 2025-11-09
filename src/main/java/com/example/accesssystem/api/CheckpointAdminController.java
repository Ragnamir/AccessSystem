package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.CheckpointResponse;
import com.example.accesssystem.api.dto.CreateCheckpointRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateCheckpointRequest;
import com.example.accesssystem.service.CheckpointRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin controller for checkpoint CRUD operations.
 */
@RestController
@RequestMapping("/admin/checkpoints")
public class CheckpointAdminController {
    
    private final CheckpointRepository checkpointRepository;
    
    public CheckpointAdminController(CheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }
    
    @PostMapping
    public ResponseEntity<CheckpointResponse> createCheckpoint(@Valid @RequestBody CreateCheckpointRequest request) {
        UUID id = checkpointRepository.create(request.code(), request.fromZoneId(), request.toZoneId());
        CheckpointRepository.CheckpointRecord record = checkpointRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Checkpoint not found after creation"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CheckpointResponse(
                record.id(), 
                record.code(), 
                record.fromZoneId(), 
                record.toZoneId(), 
                record.createdAt()
            ));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CheckpointResponse> getCheckpoint(@PathVariable UUID id) {
        return checkpointRepository.findById(id)
            .map(record -> ResponseEntity.ok(
                new CheckpointResponse(
                    record.id(), 
                    record.code(), 
                    record.fromZoneId(), 
                    record.toZoneId(), 
                    record.createdAt()
                )
            ))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<PageResponse<CheckpointResponse>> listCheckpoints(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var records = checkpointRepository.findAll(offset, limit);
        var responses = records.stream()
            .map(record -> new CheckpointResponse(
                record.id(), 
                record.code(), 
                record.fromZoneId(), 
                record.toZoneId(), 
                record.createdAt()
            ))
            .toList();
        long total = checkpointRepository.count();
        return ResponseEntity.ok(new PageResponse<>(responses, total, offset, limit));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<CheckpointResponse> updateCheckpoint(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCheckpointRequest request) {
        boolean updated = checkpointRepository.update(id, request.code(), request.fromZoneId(), request.toZoneId());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        CheckpointRepository.CheckpointRecord record = checkpointRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Checkpoint not found after update"));
        return ResponseEntity.ok(new CheckpointResponse(
            record.id(), 
            record.code(), 
            record.fromZoneId(), 
            record.toZoneId(), 
            record.createdAt()
        ));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCheckpoint(@PathVariable UUID id) {
        boolean deleted = checkpointRepository.deleteById(id);
        return deleted 
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}

