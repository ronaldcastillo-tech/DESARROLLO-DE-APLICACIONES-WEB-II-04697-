package com.cibertec.resiliencia.pedidos.service;

import com.cibertec.resiliencia.pedidos.client.InventarioClient;
import com.cibertec.resiliencia.pedidos.dto.PedidoRequest;
import com.cibertec.resiliencia.pedidos.dto.PedidoResponse;
import com.cibertec.resiliencia.pedidos.dto.StockResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class PedidoService {

	private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

	private final InventarioClient inventarioClient;
	private final AtomicLong secuenciaPedidos = new AtomicLong(1000);

	public PedidoService(InventarioClient inventarioClient) {
		this.inventarioClient = inventarioClient;
	}

	public PedidoResponse crearPedidoSinResiliencia(PedidoRequest request) {
		log.debug("[SIN-RESILIENCIA] Consultando stock para producto={} cantidad={}", request.productoId(), request.cantidad());
		StockResponse stock = inventarioClient.consultarStock(request.productoId());
		log.debug("[SIN-RESILIENCIA] Stock recibido: {}", stock);
		validarStockDisponible(request, stock);
		return pedidoConfirmado(request, stock);
	}

	@CircuitBreaker(name = "inventario", fallbackMethod = "fallbackCrearPedido")
	public PedidoResponse crearPedidoConResiliencia(PedidoRequest request) {
		log.debug("[CB] Intentando crear pedido producto={} cantidad={}", request.productoId(), request.cantidad());
		StockResponse stock = inventarioClient.consultarStock(request.productoId());
		log.debug("[CB] Stock recibido: {}", stock);
		validarStockDisponible(request, stock);
		return pedidoConfirmado(request, stock);
	}

	public PedidoResponse fallbackCrearPedido(PedidoRequest request, Throwable error) {
		log.warn("[CB-FALLBACK] Circuit breaker activo para producto={}. Causa: {}", request.productoId(), error.getClass().getSimpleName());
		return new PedidoResponse(
				secuenciaPedidos.incrementAndGet(),
				request.productoId(),
				request.cantidad(),
				request.clienteId(),
				"RECIBIDO_SIN_VALIDAR_STOCK",
				"Pedido recibido en modo degradado. Inventario no respondio: " + error.getClass().getSimpleName(),
				null
		);
	}

	private void validarStockDisponible(PedidoRequest request, StockResponse stock) {
		if (!stock.disponible() || stock.stock() < request.cantidad()) {
			log.warn("[STOCK] Insuficiente para producto={}: disponible={} stock={} solicitado={}", request.productoId(), stock.disponible(), stock.stock(), request.cantidad());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para confirmar el pedido");
		}
	}

	private PedidoResponse pedidoConfirmado(PedidoRequest request, StockResponse stock) {
		log.info("[PEDIDO] Confirmado #{} producto={} cantidad={}", secuenciaPedidos.get() + 1, request.productoId(), request.cantidad());
		return new PedidoResponse(
				secuenciaPedidos.incrementAndGet(),
				request.productoId(),
				request.cantidad(),
				request.clienteId(),
				"CONFIRMADO",
				"Pedido confirmado con stock validado por ms-inventario",
				stock
		);
	}
}
