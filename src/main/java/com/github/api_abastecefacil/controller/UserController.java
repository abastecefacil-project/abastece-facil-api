package com.github.api_abastecefacil.controller;

import com.github.api_abastecefacil.dto.user.CreateUserRequest;
import com.github.api_abastecefacil.dto.user.UpdateUserRequest;
import com.github.api_abastecefacil.dto.user.UserResponse;
import com.github.api_abastecefacil.service.UserService;
import com.github.api_abastecefacil.util.IpSolicitante;
import jakarta.servlet.http.HttpServletRequest;
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

    /**
     * Registrado <b>antes</b> de {@code /{userId}} de propósito, ainda que a precedência
     * não dependa disso: o {@code PathPattern} do Spring ordena segmento literal acima de
     * segmento com variável, então {@code /me} venceria mesmo declarado depois — é o que
     * já acontece com {@code /dashboard}. A ordem aqui é para quem lê, não para o
     * roteador.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getUsuarioAutenticado() {
        return ResponseEntity.ok(userService.getUsuarioAutenticado());
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
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request, HttpServletRequest httpRequest) {
        UserResponse createdUser = userService.createUser(request, IpSolicitante.extrair(httpRequest));
        URI location = URI.create("/api/users/" + createdUser.id());
        return ResponseEntity.created(location).body(createdUser);
    }

    /**
     * Reenvia o convite de ativação. Mesmas regras de autorização da criação.
     *
     * <p>200, e não 201: nenhum recurso novo é criado do ponto de vista do cliente. O
     * corpo é o mesmo {@link UserResponse}, e o que interessa nele é o
     * {@code conviteEnviado}.
     */
    @PostMapping("/{userId}/reenviar-ativacao")
    public ResponseEntity<UserResponse> reenviarAtivacao(
            @PathVariable Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.reenviarAtivacao(userId, IpSolicitante.extrair(httpRequest)));
    }

    // O @Valid fica no corpo, e nao no path variable. Ate o S2a ele estava no
    // @PathVariable, onde nao ha nada a validar, e por isso o @Email do
    // UpdateUserRequest nunca rodava. Nenhum teste pegou: o projeto nao tem teste de
    // controller.
    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

}
