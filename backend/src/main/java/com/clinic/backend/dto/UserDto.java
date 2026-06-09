package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Carries user data between the admin UI and {@code UserService}.
 * On create, {@code password} holds the initial plaintext password.
 * On edit, {@code password} is optional — left blank means "keep current password".
 */
@Getter @Setter
public class UserDto {
    private Long id;
    private String username;
    private String fullName;
    private String role;
    private String password;
    private boolean active = true;
    private LocalDateTime createdAt;
}
