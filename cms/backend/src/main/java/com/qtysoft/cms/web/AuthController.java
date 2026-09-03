package com.qtysoft.cms.web;

import com.qtysoft.cms.dto.ChangePasswordRequest;
import com.qtysoft.cms.dto.LoginRequest;
import com.qtysoft.cms.dto.LoginResponse;
import com.qtysoft.cms.security.CurrentUser;
import com.qtysoft.cms.model.User;
import com.qtysoft.cms.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        User user = currentUser.get();
        authService.changePassword(user, req);
        return Map.of("message", "密码已修改");
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public LoginResponse me() {
        User user = currentUser.get();
        return LoginResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .displayName(user.getDisplayName())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }
}
