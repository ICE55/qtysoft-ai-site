package com.qtysoft.cms.web;

import com.qtysoft.cms.dto.CreateUserRequest;
import com.qtysoft.cms.dto.UpdateUserRequest;
import com.qtysoft.cms.dto.UserView;
import com.qtysoft.cms.model.User;
import com.qtysoft.cms.security.CurrentUser;
import com.qtysoft.cms.service.ContentService;
import com.qtysoft.cms.service.DeployService;
import com.qtysoft.cms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final UserService userService;
    private final DeployService deployService;
    private final ContentService contentService;
    private final CurrentUser currentUser;

    @GetMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<UserView> listUsers() {
        return userService.list();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserView createUser(@RequestBody CreateUserRequest req) {
        return userService.create(req);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public UserView updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        return userService.update(id, req);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, String> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return Map.of("message", "已删除");
    }

    @PostMapping("/deploy")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> triggerDeploy() {
        boolean ok = deployService.trigger();
        return Map.of("triggered", ok);
    }
}
