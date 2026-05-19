package com.miroapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.miroapp.auth.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

// =====================================================================
// UserResponse — datos del usuario que retorna la API
//
// NO incluye:
// → password_hash → nunca se expone
// → failed_attempts → dato interno
// → locked_until → dato interno
// =====================================================================
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

  private UUID id;
  private String email;
  private String firstName;
  private String lastName;
  private String fullName;
  private String phone;
  private Role role;
  private UUID branchId;
  private String avatarUrl;
  private boolean active;
  private boolean mustChangePassword;
  private OffsetDateTime lastLoginAt;
  private OffsetDateTime passwordChangedAt;
  private OffsetDateTime createdAt;

}
