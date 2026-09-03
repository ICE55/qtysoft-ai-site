package com.qtysoft.cms.service;

import com.qtysoft.cms.dto.CreateUserRequest;
import com.qtysoft.cms.dto.UpdateUserRequest;
import com.qtysoft.cms.dto.UserView;
import com.qtysoft.cms.model.Role;
import com.qtysoft.cms.model.User;
import com.qtysoft.cms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return userRepository.findAll().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public UserView get(Long id) {
        return toView(userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在")));
    }

    @Transactional
    public UserView create(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = User.builder()
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .displayName(req.getDisplayName())
                .mustChangePassword(true)
                .build();
        return toView(userRepository.save(user));
    }

    @Transactional
    public UserView update(Long id, UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        if (req.getRole() != null) user.setRole(req.getRole());
        if (req.getDisplayName() != null) user.setDisplayName(req.getDisplayName());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            user.setMustChangePassword(false);
        }
        return toView(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UsernameNotFoundException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    private UserView toView(User u) {
        return UserView.builder()
                .id(u.getId())
                .username(u.getUsername())
                .displayName(u.getDisplayName())
                .role(u.getRole())
                .mustChangePassword(u.isMustChangePassword())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
