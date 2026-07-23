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
        validate(request.username(), request.password());
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (existing != null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "username already exists");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        userMapper.insert(user);
        return response(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid username or password");
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

    private void validate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 6) {
            throw new AppException(HttpStatus.BAD_REQUEST, "username and password are required");
        }
    }

    private AuthDtos.AuthResponse response(UserEntity user) {
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername()), user.getId(), user.getUsername());
    }
}
