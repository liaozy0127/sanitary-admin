package com.sanitary.admin.controller;

import com.sanitary.admin.common.Result;
import com.sanitary.admin.dto.LoginRequest;
import com.sanitary.admin.dto.LoginResponse;
import com.sanitary.admin.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            String role = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("ROLE_USER")
                    .replace("ROLE_", "");

            String token = jwtUtil.generateToken(request.getUsername(), role);

            return Result.success(LoginResponse.builder()
                    .token(token)
                    .username(request.getUsername())
                    .role(role)
                    .build());
        } catch (BadCredentialsException e) {
            return Result.error(401, "用户名或密码错误");
        }
    }
}
