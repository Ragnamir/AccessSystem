package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.AccessRuleResponse;
import com.example.accesssystem.api.dto.CreateAccessRuleRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateAccessRuleRequest;
import com.example.accesssystem.service.AccessRuleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin controller for access rule CRUD operations.
 */
@RestController
@RequestMapping("/admin/access-rules")
public class AccessRuleAdminController {
    
    private final AccessRuleRepository accessRuleRepository;
    
    public AccessRuleAdminController(AccessRuleRepository accessRuleRepository) {
        this.accessRuleRepository = accessRuleRepository;
    }
    
    @PostMapping
    public ResponseEntity<AccessRuleResponse> createAccessRule(@Valid @RequestBody CreateAccessRuleRequest request) {
        UUID id = accessRuleRepository.create(request.userId(), request.toZoneId());
        AccessRuleRepository.AccessRuleRecord record = accessRuleRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Access rule not found after creation"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new AccessRuleResponse(
                record.id(), 
                record.userId(), 
                record.toZoneId(), 
                record.createdAt()
            ));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AccessRuleResponse> getAccessRule(@PathVariable UUID id) {
        return accessRuleRepository.findById(id)
            .map(record -> ResponseEntity.ok(
                new AccessRuleResponse(
                    record.id(), 
                    record.userId(), 
                    record.toZoneId(), 
                    record.createdAt()
                )
            ))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<PageResponse<AccessRuleResponse>> listAccessRules(
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var records = userId != null
            ? accessRuleRepository.findByUserId(userId, offset, limit)
            : accessRuleRepository.findAll(offset, limit);
        var responses = records.stream()
            .map(record -> new AccessRuleResponse(
                record.id(), 
                record.userId(), 
                record.toZoneId(), 
                record.createdAt()
            ))
            .toList();
        long total = userId != null
            ? accessRuleRepository.countByUserId(userId)
            : accessRuleRepository.count();
        return ResponseEntity.ok(new PageResponse<>(responses, total, offset, limit));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<AccessRuleResponse> updateAccessRule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccessRuleRequest request) {
        boolean updated = accessRuleRepository.update(id, request.toZoneId());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        AccessRuleRepository.AccessRuleRecord record = accessRuleRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Access rule not found after update"));
        return ResponseEntity.ok(new AccessRuleResponse(
            record.id(), 
            record.userId(), 
            record.toZoneId(), 
            record.createdAt()
        ));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccessRule(@PathVariable UUID id) {
        boolean deleted = accessRuleRepository.deleteById(id);
        return deleted 
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}

