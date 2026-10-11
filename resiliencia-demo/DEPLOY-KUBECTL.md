# Despliegue manual en Kubernetes (kind)

Cluster objetivo: `erp-local` — context `kind-erp-local`

## Prerequisitos

| Herramienta | Verificar |
|-------------|-----------|
| Docker Desktop | `docker info` |
| kind | `kind version` |
| kubectl | `kubectl version --client` |

---

## Paso 1 — Seleccionar el cluster local

```bash
kubectl config use-context kind-erp-local
kubectl config current-context   # debe mostrar: kind-erp-local
```

---

## Paso 2 — Construir las imágenes Docker

Desde la raíz de `resiliencia-demo/`:

```bash
docker build -t ms-inventario:1.0 ./ms-inventario
docker build -t ms-pedidos:1.0    ./ms-pedidos
```

> La primera vez tarda varios minutos (descarga Maven + dependencias).

Verificar que existan:

```bash
docker images | grep -E 'ms-inventario|ms-pedidos'
```

---

## Paso 3 — Cargar las imágenes en kind

Los manifests usan `imagePullPolicy: Never` — kind debe tener las imágenes localmente, **no las descarga del registry**.

```bash
kind load docker-image ms-inventario:1.0 --name erp-local
kind load docker-image ms-pedidos:1.0    --name erp-local
```

Verificar que kind las vea:

```bash
docker exec erp-local-control-plane crictl images | grep -E 'ms-inventario|ms-pedidos'
```

---

## Paso 4 — Aplicar los manifests

```bash
kubectl apply -f k8s/
```

Salida esperada:

```
deployment.apps/ms-inventario created
service/ms-inventario-svc created
deployment.apps/ms-pedidos created
service/ms-pedidos-svc created
```

---

## Paso 5 — Esperar el rollout

```bash
kubectl rollout status deployment/ms-inventario --timeout=120s
kubectl rollout status deployment/ms-pedidos    --timeout=120s
```

Verificar pods:

```bash
kubectl get pods -o wide
```

Todos deben mostrar `1/1 Running`.

---

## Paso 6 — Acceder a los servicios

kind no expone NodePort directamente al host. Usa port-forward en dos terminales:

**Terminal 1 — ms-inventario:**
```bash
kubectl port-forward svc/ms-inventario-svc 8091:8091
```

**Terminal 2 — ms-pedidos:**
```bash
kubectl port-forward svc/ms-pedidos-svc 8090:8090
```

---

## Paso 7 — Verificar los endpoints

```bash
# Health checks
curl http://localhost:8091/actuator/health
curl http://localhost:8090/actuator/health

# Stock de inventario (product id = 1)
curl http://localhost:8091/inventario/1

# Pedido CON resiliencia (Circuit Breaker activo)
curl http://localhost:8090/pedidos/con-resiliencia

# Pedido SIN resiliencia (llamada directa, sin fallback)
curl http://localhost:8090/pedidos/sin-resiliencia
```

---

## Demo de resiliencia

### Rate Limiter — ms-inventario

Permite **3 llamadas cada 10 segundos** al endpoint `/inventario/{id}`.

```bash
# Las últimas 2 llamadas deben retornar HTTP 429
for i in {1..5}; do
  echo -n "Llamada $i: "
  curl -s -o /dev/null -w "%{http_code}" http://localhost:8091/inventario/1
  echo
done
```

### Circuit Breaker — ms-pedidos

```bash
# 1. Activar modo fallo en inventario
curl -X POST http://localhost:8091/inventario/demo/fallo/activar

# 2. Hacer 5+ llamadas para abrir el circuit breaker
for i in {1..6}; do
  curl -s http://localhost:8090/pedidos/con-resiliencia
  echo
done

# 3. Verificar estado del circuit breaker (debe aparecer OPEN)
curl http://localhost:8090/actuator/health | python3 -m json.tool

# 4. Desactivar el fallo (el CB vuelve a CLOSED tras ~10s)
curl -X POST http://localhost:8091/inventario/demo/fallo/desactivar
```

---

## Limpieza

```bash
kubectl delete -f k8s/
```

---

## Actualizar a una nueva versión

```bash
# 1. Reconstruir con nuevo tag
docker build -t ms-inventario:1.1 ./ms-inventario
docker build -t ms-pedidos:1.1    ./ms-pedidos

# 2. Cargar en kind
kind load docker-image ms-inventario:1.1 --name erp-local
kind load docker-image ms-pedidos:1.1    --name erp-local

# 3. Editar los manifests
# k8s/ms-inventario-deployment.yaml → image: ms-inventario:1.1
# k8s/ms-pedidos-deployment.yaml    → image: ms-pedidos:1.1

# 4. Aplicar
kubectl apply -f k8s/
```
