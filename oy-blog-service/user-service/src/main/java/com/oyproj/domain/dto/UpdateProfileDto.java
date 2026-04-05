package com.oyproj.domain.dto;

import lombok.Data;

@Data
public class UpdateProfileDto {
    public String username;
    public String email;
    public String avatarUrl;
    public String bio;
}
