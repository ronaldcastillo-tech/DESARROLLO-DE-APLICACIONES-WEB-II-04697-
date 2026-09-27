package com.cibertec.jwtsalesservices.rest;

import com.cibertec.jwtsalesservices.dto.CustomerRequest;
import com.cibertec.jwtsalesservices.dto.CustomerResponse;
import com.cibertec.jwtsalesservices.negocio.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	/**
	 * Caso de uso 1: Registro de cliente público (nace con estado 'POR CONFIRMAR').
	 */
	@PostMapping("/customers/public")
	public ResponseEntity<CustomerResponse> createPublicCustomer(@Valid @RequestBody CustomerRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(customerService.createPublicCustomer(request));
	}

	/**
	 * Caso de uso 2: Registro de cliente protegido / interno (nace con estado 'CONFIRMADO').
	 */
	@PostMapping("/customers")
	public ResponseEntity<CustomerResponse> createProtectedCustomer(@Valid @RequestBody CustomerRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(customerService.createProtectedCustomer(request));
	}

	@GetMapping("/customers")
	public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
		return ResponseEntity.ok(customerService.getAllCustomers());
	}

	@GetMapping("/customers/{id}")
	public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id) {
		return ResponseEntity.ok(customerService.getCustomerById(id));
	}

	@PutMapping("/customers/{id}")
	public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
		return ResponseEntity.ok(customerService.updateCustomer(id, request));
	}

	@DeleteMapping("/customers/{id}")
	public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
		customerService.deleteCustomer(id);
		return ResponseEntity.noContent().build();
	}
}
