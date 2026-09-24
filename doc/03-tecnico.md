# Diseno Tecnico

## Endpoint de ejecucion

```http
POST /v1/domain/storage/soyio/sync
x-gssp-transactionid: <id>
Content-Type: application/json
```

Body opcional:

```json
{
  "resource": "consent_actions",
  "outputFormat": "csv",
  "overlapMinutes": 10,
  "fromDateTime": "2026-09-11T00:00:00Z",
  "toDateTime": "2026-09-12T00:00:00Z"
}
```

El endpoint conserva la API publica existente. El formato efectivo para este flujo debe ser `csv`.

## Etapas internas

1. Resolver configuracion y generar `runId`.
2. Leer watermark.
3. Calcular ventana UTC.
4. Crear exportacion en Soyio.
5. Hacer polling hasta `completed`, `failed` o timeout.
6. Descargar el archivo firmado.
7. Validar tamano y cargar CSV crudo en RDZ.
8. Obtener assertion y access token para User Reference.
9. Resolver referencias validas en segmentos secuenciales.
10. Parsear CSV y JSON embebido.
11. Expandir permisos.
12. Cargar CSV normalizado en DDZ.
13. Escribir `_SUCCESS`, watermark e historial.
14. Responder con estado y metricas.

## API de User Reference

### Token

La autenticacion usa client credentials con client assertion:

```text
GET <client-assertion-url>?env=<environment>
POST https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token
```

### Resolucion

```http
POST <base-url>/v1/user-references/resolve
Authorization: Bearer <access_token>
X-Api-Key: <api-key>
Ocp-Apim-Subscription-Key: <subscription-key>
Content-Type: application/json
```

```json
{
  "user_reference": "<reference>"
}
```

Respuesta esperada:

```json
{
  "data": "12345678K"
}
```

## Segmentacion y reintentos

- `batch-size`: 50 referencias por segmento por defecto.
- `delay-millis`: 100 ms entre llamadas por defecto.
- `max-retries`: 2 reintentos por defecto.
- `429` y `5xx`: reintento con backoff exponencial.
- `400`, `401` y `403`: se marca la referencia; no se reintenta automaticamente.
- Un `401` puede disparar una renovacion unica del token durante una corrida.

El endpoint de resolucion procesa una referencia por llamada. No se usa el endpoint bulk de generacion porque no corresponde a esta operacion.

## Esquema de salida normalizada

```text
rut
rut_normalized
user_reference
agreement_id
created_at
version
subject_id
subject_type
version_source_type
version_source_id
consent_status
data_category
data_label
data_use
data_subject
scope_type
scope_id
scope_version
expires_at
previous_evidence_ids
resolution_status
resolution_error
data_permissions_raw
source_file
processed_at
```

`data_permissions_raw` se conserva para filas de evento sin permisos o cuando el JSON no puede interpretarse.

## Control de estado

```json
{
  "last_successful_sync_at": "2026-09-24T12:00:00Z",
  "last_attempt_at": "2026-09-24T12:15:00Z",
  "last_status": "completed_with_errors",
  "last_export_id": "exp_123",
  "resource": "consent_actions",
  "last_run_id": "run_20260924T120000",
  "updated_at": "2026-09-24T12:15:00Z"
}
```

El watermark se escribe despues de DDZ. Los errores por fila no detienen la actualizacion si el procesamiento y las cargas de archivos terminaron correctamente.
