package com.qtysoft.cms.dto;

import com.qtysoft.cms.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度 3-64")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64")
    private String password;

    @NotNull(message = "角色不能为空")
    private Role role;

    private String displayName;
}
