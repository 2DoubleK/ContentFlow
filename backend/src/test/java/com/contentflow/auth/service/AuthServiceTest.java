package com.contentflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.contentflow.auth.dto.AuthDtos;
import com.contentflow.auth.entity.UserEntity;
import com.contentflow.auth.mapper.UserMapper;
import com.contentflow.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
}
