# Despliegue con ArgoCD (GitOps)

Cluster objetivo: `erp-local` — context `kind-erp-local`  
ArgoCD UI: https://localhost:8080  
Repo GitHub: `https://github.com/ronaldcastillo-tech/DESARROLLO-DE-APLICACIONES-WEB-II-04697-`  
Path de manifests: `resiliencia-demo/k8s`

---

## Prerequisitos

| Herramienta | Verificar |
|-------------|-----------|
| Docker Desktop | `docker info` |
| kind | `kind version` |
| kubectl | `kubectl version --client` |
| argocd CLI | `argocd version` |
| ArgoCD instalado en el cluster | `kubectl get pods -n argocd` |

> Si ArgoCD no está instalado, sigue primero: [instalar ArgoCD en kind](../INSTALL-ARGOCD.md)

---

## Concepto clave

ArgoCD **solo lee manifests desde Git**. No construye imágenes ni hace `kubectl apply` tú mismo.  
El flujo es:

```
código → docker build → kind load → git commit/push → ArgoCD sync → pods running
```

Las imágenes deben estar cargadas en kind **antes** de hacer sync, porque los manifests usan `imagePullPolicy: Never`.

---

## Paso 1 — Seleccionar el cluster local

```bash
kubectl config use-context kind-erp-local
kubectl config current-context   # debe mostrar: kind-erp-local
```

---

## Paso 2 — Construir y cargar las imágenes en kind

```bash
# Construir
docker build -t ms-inventario:1.0 ./ms-inventario
docker build -t ms-pedidos:1.0    ./ms-pedidos

# Cargar en el cluster (obligatorio por imagePullPolicy: Never)
kind load docker-image ms-inventario:1.0 --name erp-local
kind load docker-image ms-pedidos:1.0    --name erp-local
```

Verificar:

```bash
docker exec erp-local-control-plane crictl images | grep -E 'ms-inventario|ms-pedidos'
```

---

## Paso 3 — Commitear y pushear los manifests

ArgoCD sincroniza desde el estado del repo en GitHub. Los cambios **deben estar pusheados** para que ArgoCD los vea.

```bash
# Verificar que los manifests están listos
cat k8s/ms-inventario-deployment.yaml
cat k8s/ms-pedidos-deployment.yaml

# Commitear si hay cambios pendientes
git add k8s/
git commit -m "chore: actualizar manifests k8s resiliencia-demo"
git push origin main
```

---

## Paso 4 — Iniciar port-forward de ArgoCD

En una terminal dedicada (déjala abierta):

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

---

## Paso 5 — Obtener la contraseña de ArgoCD

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

---

## Paso 6 — Login con el CLI

```bash
argocd login localhost:8080 \
  --username admin \
  --password <contraseña-del-paso-5> \
  --insecure
```

Verificar que el login funcionó:

```bash
argocd account get-user-info
```

---

## Paso 7 — Crear la Application en ArgoCD

```bash
argocd app create resiliencia-demo \
  --repo https://github.com/ronaldcastillo-tech/DESARROLLO-DE-APLICACIONES-WEB-II-04697- \
  --path resiliencia-demo/k8s \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace default \
  --sync-policy automated \
  --self-heal \
  --auto-prune
```

Verificar que se creó:

```bash
argocd app list
```

---

## Paso 8 — Sincronizar y esperar

```bash
argocd app sync resiliencia-demo
argocd app wait resiliencia-demo --health --sync --timeout 180
```

Ver el estado en tiempo real:

```bash
argocd app get resiliencia-demo
```

---

## Paso 9 — Verificar los pods

```bash
kubectl get pods -o wide
kubectl get services
```

Todos los pods deben mostrar `1/1 Running`.

---

## Paso 10 — Acceder a los servicios

kind no expone NodePort al host. Usa port-forward en dos terminales:

**Terminal A — ms-inventario:**
```bash
kubectl port-forward svc/ms-inventario-svc 8091:8091
```

**Terminal B — ms-pedidos:**
```bash
kubectl port-forward svc/ms-pedidos-svc 8090:8090
```

Verificar:

```bash
curl http://localhost:8091/actuator/health
curl http://localhost:8090/actuator/health
```

---

## Ver la UI de ArgoCD

Abre en el browser: **https://localhost:8080**  
Usuario: `admin`  
Contraseña: la del Paso 5

Verás la aplicación `resiliencia-demo` con los 4 recursos sincronizados (2 Deployments + 2 Services).

---

## Flujo de actualización (GitOps)

Cada vez que necesites actualizar la versión:

```bash
# 1. Construir nueva versión
docker build -t ms-inventario:1.1 ./ms-inventario
docker build -t ms-pedidos:1.1    ./ms-pedidos

# 2. Cargar en kind
kind load docker-image ms-inventario:1.1 --name erp-local
kind load docker-image ms-pedidos:1.1    --name erp-local

# 3. Editar los manifests
# k8s/ms-inventario-deployment.yaml → image: ms-inventario:1.1
# k8s/ms-pedidos-deployment.yaml    → image: ms-pedidos:1.1

# 4. Commitear y pushear
git add k8s/
git commit -m "chore: bump resiliencia-demo a v1.1"
git push origin main

# 5. ArgoCD detecta el cambio automáticamente (sync-policy: automated)
# O forzar sync manual:
argocd app sync resiliencia-demo
```

---

## Comandos útiles de ArgoCD CLI

```bash
# Estado de la aplicación
argocd app get resiliencia-demo

# Ver todos los recursos
argocd app resources resiliencia-demo

# Historial de sincronizaciones
argocd app history resiliencia-demo

# Forzar re-sync
argocd app sync resiliencia-demo --force

# Ver logs de un pod desde ArgoCD
argocd app logs resiliencia-demo --kind Deployment --resource-name ms-pedidos
```

---

## Limpieza

Eliminar solo la Application de ArgoCD (no borra los recursos del cluster):

```bash
argocd app delete resiliencia-demo
```

Eliminar la Application **y** los recursos del cluster:

```bash
argocd app delete resiliencia-demo --cascade
```
