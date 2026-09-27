package com.cibertec.jwtsalesservices.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
		@NotBlank @Size(max = 100) String nombre,
		@NotBlank @Email @Size(max = 120) String email
) {
}
