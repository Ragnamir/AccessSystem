package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.CreateUserRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateUserRequest;
import com.example.accesssystem.api.dto.UserResponse;
import com.example.accesssystem.service.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin controller for user CRUD operations.
 */
@RestController
@RequestMapping("/admin/users")
public class UserAdminController {
    
    private final UserRepository userRepository;
    
    public UserAdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UUID id = userRepository.create(request.code());
        UserRepository.UserRecord record = userRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("User not found after creation"));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new UserResponse(record.id(), record.code(), record.createdAt()));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
            .map(record -> ResponseEntity.ok(
                new UserResponse(record.id(), record.code(), record.createdAt())
            ))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        var records = userRepository.findAll(offset, limit);
        var responses = records.stream()
            .map(record -> new UserResponse(record.id(), record.code(), record.createdAt()))
            .toList();
        long total = userRepository.count();
        return ResponseEntity.ok(new PageResponse<>(responses, total, offset, limit));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        boolean updated = userRepository.update(id, request.code());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        UserRepository.UserRecord record = userRepository.findById(id)
            .orElseThrow(() -> new IllegalStateException("User not found after update"));
        return ResponseEntity.ok(new UserResponse(record.id(), record.code(), record.createdAt()));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        boolean deleted = userRepository.deleteById(id);
        return deleted 
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}

