package com.contentflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contentflow.auth.dto.AuthDtos;
import com.contentflow.auth.entity.UserEntity;
import com.contentflow.auth.mapper.UserMapper;
import com.contentflow.common.exception.AppException;
import com.contentflow.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalize(request.email());
        String phone = normalize(request.phone());
        validate(request, email, phone);
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (existing != null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "账号已存在");
        }
        if (email != null && userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email)) > 0)
            throw new AppException(HttpStatus.BAD_REQUEST, "邮箱已存在");
        if (phone != null && userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPhone, phone)) > 0)
            throw new AppException(HttpStatus.BAD_REQUEST, "手机号已存在");
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        userMapper.insert(user);
        return response(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return response(user);
    }

    public AuthDtos.CurrentUserResponse currentUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "user not found");
        }
        return new AuthDtos.CurrentUserResponse(user.getId(), user.getUsername(), user.getRole());
    }

    private void validate(AuthDtos.RegisterRequest request, String email, String phone) {
        if (request.username() == null || request.username().isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "请输入账号");
        if (request.password() == null || request.password().length() < 6) throw new AppException(HttpStatus.BAD_REQUEST, "密码至少需要 6 位");
        if (!request.password().equals(request.confirmPassword())) throw new AppException(HttpStatus.BAD_REQUEST, "两次输入的密码不一致");
        if (email == null && phone == null)
            throw new AppException(HttpStatus.BAD_REQUEST, "邮箱或手机号至少填写一项");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private AuthDtos.AuthResponse response(UserEntity user) {
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername()), user.getId(), user.getUsername());
    }
}
