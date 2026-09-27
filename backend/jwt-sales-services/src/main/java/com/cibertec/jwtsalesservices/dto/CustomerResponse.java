package com.cibertec.jwtsalesservices.dto;

public record CustomerResponse(
		Long customerId,
		String nombre,
		String email,
		String status
) {
}
