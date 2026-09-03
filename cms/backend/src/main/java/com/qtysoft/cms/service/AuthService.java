package com.qtysoft.cms.service;

import com.qtysoft.cms.dto.ChangePasswordRequest;
import com.qtysoft.cms.dto.LoginRequest;
import com.qtysoft.cms.dto.LoginResponse;
import com.qtysoft.cms.model.User;
import com.qtysoft.cms.repository.UserRepository;
import com.qtysoft.cms.security.JwtUtil;
import com.qtysoft.cms.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${cms.jwt.expiration-minutes:120}")
    private long expirationMinutes;

    @Transactional
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        UserDetailsImpl ud = UserDetailsImpl.fromUser(user);
        String token = jwtUtil.generateToken(ud, user.getId(), user.getRole().name());
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expirationMinutes * 60)
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .displayName(user.getDisplayName())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest req) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    /** 首次启动且库内无账号时，用环境变量创建超管 */
    @Transactional
    public void seedAdminIfEmpty(String username, String password, boolean forceChange) {
        if (userRepository.count() > 0) return;
        User admin = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(com.qtysoft.cms.model.Role.SUPER_ADMIN)
                .mustChangePassword(forceChange)
                .displayName("管理员")
                .build();
        userRepository.save(admin);
    }
}
