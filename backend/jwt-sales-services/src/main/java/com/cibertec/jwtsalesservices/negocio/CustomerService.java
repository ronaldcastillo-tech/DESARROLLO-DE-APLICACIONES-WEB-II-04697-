package com.cibertec.jwtsalesservices.negocio;

import com.cibertec.jwtsalesservices.dto.CustomerRequest;
import com.cibertec.jwtsalesservices.dto.CustomerResponse;
import com.cibertec.jwtsalesservices.entidades.Customer;
import com.cibertec.jwtsalesservices.repositorio.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CustomerService {

	public static final String STATUS_POR_CONFIRMAR = "POR CONFIRMAR";
	public static final String STATUS_CONFIRMADO = "CONFIRMADO";

	private final CustomerRepository customerRepository;

	public CustomerService(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	/**
	 * Caso de uso 1: Registro de cliente público.
	 * Se crea con el estado obligatorio 'POR CONFIRMAR'.
	 */
	public CustomerResponse createPublicCustomer(CustomerRequest request) {
		return saveCustomerWithStatus(request, STATUS_POR_CONFIRMAR);
	}

	/**
	 * Caso de uso 2: Registro de cliente protegido (aplicación interna).
	 * Se crea con el estado 'CONFIRMADO'.
	 */
	public CustomerResponse createProtectedCustomer(CustomerRequest request) {
		return saveCustomerWithStatus(request, STATUS_CONFIRMADO);
	}

	public List<CustomerResponse> getAllCustomers() {
		return customerRepository.findAll().stream()
				.map(this::mapToResponse)
				.toList();
	}

	public CustomerResponse getCustomerById(Long id) {
		return customerRepository.findById(id)
				.map(this::mapToResponse)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
	}

	public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
		Customer currentCustomer = customerRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

		if (!currentCustomer.getEmail().equalsIgnoreCase(request.email())
				&& customerRepository.existsByEmail(request.email())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
		}

		currentCustomer.setNombre(request.nombre());
		currentCustomer.setEmail(request.email());

		return mapToResponse(customerRepository.save(currentCustomer));
	}

	public void deleteCustomer(Long id) {
		if (!customerRepository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
		}
		customerRepository.deleteById(id);
	}

	private CustomerResponse saveCustomerWithStatus(CustomerRequest request, String status) {
		if (customerRepository.existsByEmail(request.email())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
		}

		Customer customer = new Customer(
				null,
				request.nombre(),
				request.email(),
				status
		);

		return mapToResponse(customerRepository.save(customer));
	}

	private CustomerResponse mapToResponse(Customer customer) {
		return new CustomerResponse(
				customer.getCustomerId(),
				customer.getNombre(),
				customer.getEmail(),
				customer.getStatus()
		);
	}
}
