package com.qtysoft.cms.dto;

import com.qtysoft.cms.model.Role;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private Long id;
    private String username;
    private Role role;
    private String displayName;
    private boolean mustChangePassword;
}
