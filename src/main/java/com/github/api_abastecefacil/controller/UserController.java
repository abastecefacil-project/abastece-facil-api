package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.auth.RegisterRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(value = "active") boolean active,
            @RequestParam(value = "name", required = false, defaultValue = "") String name,
            Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(active, name, pageable));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Long> countAllActiveUsers() {
        return ResponseEntity.ok(userService.countAllActiveUsers());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody RegisterRequest request) {
        UserResponse createdUser = userService.createUser(request);
        URI location = URI.create("/api/users/" + createdUser.id());
        return ResponseEntity.created(location).body(createdUser);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(@Valid @PathVariable Long userId, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

}
