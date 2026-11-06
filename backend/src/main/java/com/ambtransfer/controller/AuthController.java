package com.ambtransfer.controller;

import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody Requests.Login request) { return auth.login(request); }

    @GetMapping("/me")
    public UserPrincipal me(@AuthenticationPrincipal UserPrincipal user) { return user; }
}
