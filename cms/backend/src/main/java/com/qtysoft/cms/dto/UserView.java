package com.qtysoft.cms.dto;

import com.qtysoft.cms.model.Role;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserView {
    private Long id;
    private String username;
    private String displayName;
    private Role role;
    private boolean mustChangePassword;
    private Instant createdAt;
}
