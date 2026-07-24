package com.contentflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.contentflow.auth.dto.AuthDtos;
import com.contentflow.auth.entity.UserEntity;
import com.contentflow.auth.mapper.UserMapper;
import com.contentflow.security.JwtService;
import com.contentflow.common.exception.AppException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserMapper userMapper;

    @Mock
    JwtService jwtService;

    @Test
    void returnsCurrentUserProfile() {
        UserEntity user = new UserEntity();
        user.setId(12L);
        user.setUsername("writer");
        user.setRole("USER");
        when(userMapper.selectById(12L)).thenReturn(user);
        AuthService service = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService);

        AuthDtos.CurrentUserResponse response = service.currentUser(12L);

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.username()).isEqualTo("writer");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void rejectsMismatchedRegistrationPasswords() {
        AuthService service = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService);
        assertThatThrownBy(() -> service.register(new AuthDtos.RegisterRequest("writer", "secret1", "secret2", "a@example.com", "")))
                .isInstanceOf(AppException.class).hasMessage("两次输入的密码不一致");
    }

    @Test
    void reportsInvalidLoginWithoutRevealingWhetherUsernameExists() {
        AuthService service = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService);
        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("missing", "secret")))
                .isInstanceOf(AppException.class).hasMessage("用户名或密码错误");
    }

    @Test
    void normalizesContactFieldsBeforeRegistration() {
        AuthService service = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService);

        service.register(new AuthDtos.RegisterRequest(
                "writer", "secret1", "secret1", " writer@example.com ", "   "));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("writer@example.com");
        assertThat(captor.getValue().getPhone()).isNull();
    }
}
