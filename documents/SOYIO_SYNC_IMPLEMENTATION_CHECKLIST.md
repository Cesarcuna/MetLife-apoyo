# Soyio Sync MVP - Checklist Tecnico de Implementacion

## 1) Alcance de esta fase (sin Cosmos)

- Implementar orquestacion de export Soyio desde Storage API.
- Ejecutar sincronizacion incremental cada 6 horas via scheduler externo.
- Persistir watermark en Blob usando archivo JSON de control.
- Dejar observabilidad y reintentos basicos.

## 2) Endpoints del contrato y estado

Referencia de contrato: [documents/eos-chile-storage-service-api.yaml](eos-chile-storage-service-api.yaml)
Ejemplo E2E de consumo: [documents/SOYIO_SYNC_E2E_EXAMPLES.md](SOYIO_SYNC_E2E_EXAMPLES.md)

- Ya existentes (productivo hoy):
  - POST /v1/domain/storage/sendFile
  - POST /v1/domain/storage/sendFileMultipart
  - GET /v1/domain/storage/retrieveFile
  - GET /v1/domain/storage/listFiles
- Nuevos (MVP Soyio a implementar):
  - POST /v1/domain/storage/soyio/sync
  - GET /v1/domain/storage/soyio/sync/status

## 3) Checklist por endpoint nuevo

### 3.1 POST /v1/domain/storage/soyio/sync

- Validar headers obligatorios:
  - Authorization
  - Ocp-Apim-Subscription-Key
  - x-gssp-transactionid
- Validar body opcional:
  - resource permitido
  - outputFormat json/csv
  - fromDateTime y toDateTime coherentes
  - overlapMinutes >= 0
- Definir valores por defecto:
  - resource = consent_actions
  - outputFormat = json
  - overlapMinutes = 10
- Cargar estado de watermark desde Blob control file.
- Calcular ventana incremental efectiva:
  - from = max(fromDateTime opcional, last_successful_sync_at - overlap)
  - to = toDateTime opcional o now
- Ejecutar create export en Soyio.
- Hacer polling a estado export hasta:
  - completed
  - failed
  - timeout de seguridad
- Descargar archivo desde download_url cuando completed.
- Guardar payload en CDZ ruta objetivo.
- Actualizar control file solo si toda la corrida termina bien.
- Responder esquema soyioSyncResponse con:
  - runId
  - status
  - exportId
  - ventana usada
  - recordsCount (si viene en respuesta)

### 3.2 GET /v1/domain/storage/soyio/sync/status

- Leer control file desde Blob.
- Si no existe, responder estado never.
- Retornar:
  - lastSuccessfulSyncAt
  - lastAttemptAt
  - lastStatus
  - lastExportId
  - resource
  - watermarkSource = blob-control-file

## 4) Estructura del control file (Blob)

Ruta sugerida:

- cdz/proveedores_externos/soyio/_control/watermark.json

Ejemplo:

```json
{
  "last_successful_sync_at": "2026-07-24T12:00:00Z",
  "last_attempt_at": "2026-07-24T18:00:00Z",
  "last_status": "completed",
  "last_export_id": "exp_123",
  "resource": "consent_actions",
  "last_run_id": "run_20260724_180000"
}
```

## 5) Flujo tecnico interno (paso a paso)

1. Inicio corrida: generar runId y timestamp.
2. Leer control file.
3. Calcular ventana incremental.
4. Invocar POST /api/v1/exports en Soyio.
5. Polling GET /api/v1/exports/{id} con backoff.
6. En completed, descargar download_url.
7. Subir archivo a CDZ con nombre versionado por fecha/hora.
8. Escribir control file actualizado.
9. Responder estado final.

## 6) Reintentos y resiliencia

- Polling export:
  - intento cada 20-30 segundos
  - timeout total de corrida (ejemplo 15 min)
- Descarga de archivo:
  - 2-3 reintentos con backoff
- Errores 401/403:
  - no actualizar watermark
  - marcar failed y alertar
- Errores transientes (5xx/timeouts):
  - reintentos acotados
  - no avanzar watermark en falla

## 6.1) Limites de tamano

- El limite `spring.servlet.multipart.max-file-size` aplica solo al endpoint `sendFileMultipart`.
- El flujo `POST /soyio/sync` no usa multipart de entrada.
- Se controla tamano de export descargado con `soyio.max-download-bytes` (default 100 MB).
- Si el archivo excede el limite:
  - dividir ventana temporal (`fromDateTime/toDateTime`)
  - reducir volumen por corrida
  - considerar empaquetado/particionado en estrategia de export

## 7) Seguridad minima obligatoria

- No registrar API key, secret ni refresh token en logs.
- Obtener secretos desde Key Vault/variables seguras.
- Mantener key bootstrap separada de key operativa.
- Rotar key operativa con proceso documentado.

Nota AKS/Runtime:

- Evitar inyectar SOYIO_API_KEY dentro de la imagen.
- Consumir secreto desde Key Vault/secret provider en runtime.
- Para aplicar key rotada: actualizar secreto + rollout restart del deployment.
- No requiere rebuild de imagen para cada rotacion.

## 7.1) Integracion Storage API con Key Vault (que agregar y que no)

- En el enfoque recomendado (AKS + secret injection), Storage API NO necesita libreria de Key Vault.
- La app sigue leyendo `SOYIO_API_KEY` desde variable de entorno ya inyectada en runtime.
- Lo que si se requiere:
  - integracion de plataforma AKS -> Key Vault (secret provider/CSI o sync a secret de Kubernetes)
  - restart controlado de pods al rotar secreto si la app lo lee al arranque
- Solo si se decide leer Key Vault directamente desde la app:
  - agregar librerias Azure Identity + Key Vault Secrets
  - manejar cache, retries, timeouts y fallback de secretos
  - este camino agrega complejidad y no es necesario para el MVP

Referencia de ownership/operacion de rotacion:

- [documents/SOYIO_KEY_ROTATION_STRATEGY.md](SOYIO_KEY_ROTATION_STRATEGY.md)

## 8) Scheduler externo cada 6 horas

Opciones:

- Azure Function Timer Trigger
- Logic App con recurrence

Invocacion recomendada:

- POST al endpoint /soyio/sync
- body vacio o body con resource/outputFormat
- cabeceras de trazabilidad

## 9) Observabilidad y alertas

- Log estructurado por runId.
- Metricas minimas:
  - runs_total
  - runs_failed
  - run_duration_ms
  - records_count
- Alertar por:
  - 2 fallas consecutivas
  - 401/403
  - timeout de export

## 10) Definition of Done del MVP

- Endpoint POST /soyio/sync operativo en DEV.
- Endpoint GET /soyio/sync/status operativo en DEV.
- Corrida manual exitosa end-to-end con archivo en CDZ.
- Scheduler 6h configurado en DEV.
- Watermark avanza solo en corridas exitosas.
- Documento tecnico actualizado tras pruebas reales.

## 11) Backlog inmediato (fase 2)

- Migrar estado y auditoria de corridas a Cosmos.
- Agregar historial de ejecuciones consultable por API.
- Idempotencia avanzada y deduplicacion explicita en carga.
- Alertas operativas integradas con canal corporativo.