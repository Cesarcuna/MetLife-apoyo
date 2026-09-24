# Soyio Sync MVP - Ejemplo E2E (punta a punta)

Este documento muestra un ejemplo practico de extremo a extremo para la sincronizacion Soyio hacia CDZ usando el endpoint interno de Storage API.

## 1) Request de sync (lo que mandas)

### Opcion A: request minimo (usa defaults)

```http
POST /v1/domain/storage/soyio/sync HTTP/1.1
Host: latam.dev.internal.apis.metlife.com
Authorization: Bearer <INTERNAL_API_TOKEN>
Ocp-Apim-Subscription-Key: <APIM_KEY>
x-gssp-transactionid: trx-20260724-180000
Content-Type: application/json
```

### Opcion B: request con ventana explicita

```http
POST /v1/domain/storage/soyio/sync HTTP/1.1
Host: latam.dev.internal.apis.metlife.com
Authorization: Bearer <INTERNAL_API_TOKEN>
Ocp-Apim-Subscription-Key: <APIM_KEY>
x-gssp-transactionid: trx-20260724-180000
Content-Type: application/json

{
  "resource": "consent_actions",
  "outputFormat": "json",
  "fromDateTime": "2026-07-24T12:00:00Z",
  "toDateTime": "2026-07-24T18:00:00Z",
  "overlapMinutes": 10
}
```

## 2) Response de sync (lo que recibes)

```json
{
  "runId": "run_20260724T180001",
  "status": "completed",
  "resource": "consent_actions",
  "exportId": "exp_1B2M2Y8AsgTpgAmY7PhCfg",
  "recordsCount": 12873,
  "fromDateTime": "2026-07-24T12:00:00Z",
  "toDateTime": "2026-07-24T18:00:00Z",
  "startedAt": "2026-07-24T18:00:01Z",
  "finishedAt": "2026-07-24T18:03:46Z",
  "message": "Soyio export synced successfully to cdz/proveedores_externos/soyio/exports/consent_actions/2026/07/24/consent-actions-20260724-1800.json",
  "metadata": {
    "gsspTrxId": "trx-20260724-180000",
    "apiName": "Chile Insurance Storage API",
    "timestamp": "20260724T180346.113"
  }
}
```

## 3) Archivo esperado en CDZ (dataset exportado)

Ruta ejemplo de salida:

- cdz/proveedores_externos/soyio/exports/consent_actions/2026/07/24/consent-actions-20260724-1800.json

### Ejemplo JSON

```json
[
  {
    "id": "ca_001",
    "agreement_id": "agr_1001",
    "subject_id": "sub_4509",
    "action": "accepted",
    "template_id": "tpl_marketing_v3",
    "created_at": "2026-07-24T12:15:11Z",
    "metadata": {
      "channel": "web",
      "country": "CL"
    }
  },
  {
    "id": "ca_002",
    "agreement_id": "agr_1002",
    "subject_id": "sub_4510",
    "action": "revoked",
    "template_id": "tpl_marketing_v3",
    "created_at": "2026-07-24T13:01:09Z",
    "metadata": {
      "channel": "mobile",
      "country": "CL"
    }
  }
]
```

### Ejemplo CSV

```csv
id,agreement_id,subject_id,action,template_id,created_at,metadata.channel,metadata.country
ca_001,agr_1001,sub_4509,accepted,tpl_marketing_v3,2026-07-24T12:15:11Z,web,CL
ca_002,agr_1002,sub_4510,revoked,tpl_marketing_v3,2026-07-24T13:01:09Z,mobile,CL
```

## 4) watermark.json actualizado (estado de control)

Ruta esperada:

- cdz/proveedores_externos/soyio/_control/watermark.json

```json
{
  "last_successful_sync_at": "2026-07-24T18:00:00Z",
  "last_attempt_at": "2026-07-24T18:03:46Z",
  "last_status": "completed",
  "last_export_id": "exp_1B2M2Y8AsgTpgAmY7PhCfg",
  "resource": "consent_actions",
  "last_run_id": "run_20260724T180001"
}
```

## 5) Notas operativas

- El endpoint sync no recibe archivos por multipart.
- El limite de seguridad para descarga se controla con soyio.max-download-bytes.
- Si el archivo excede el limite, dividir ventana temporal y reintentar.