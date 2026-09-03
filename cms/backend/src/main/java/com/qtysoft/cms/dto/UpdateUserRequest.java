package com.qtysoft.cms.dto;

import com.qtysoft.cms.model.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    private Role role;
    private String displayName;
    private String password; // 可选，留空则不改密码
}
