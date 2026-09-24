# Reglas de Negocio

## 1. Ventana incremental

La corrida lee `cdz/soyio/control/watermark.json`.

- Si existe `last_successful_sync_at`, el inicio se calcula restando `default-overlap-minutes`.
- El fin es la hora UTC actual o `toDateTime` si viene en la solicitud.
- Si no existe watermark, se usa `initial-lookback-hours`.
- Si el inicio no es menor que el fin, la corrida responde como no-op.

## 2. Una corrida, una exportacion

El servicio crea una sola exportacion para la ventana completa. No divide la ventana temporal. La segmentacion aplica unicamente a la resolucion de referencias para respetar la capacidad de la API de User Reference.

## 3. Una fila por permiso

Un agreement con varios elementos en `data_permissions` produce varias filas con los datos del agreement repetidos y un permiso diferente por fila. De esta manera no se pierde el detalle de categorias, usos, scopes ni expiraciones.

## 4. RUT

La API de User Reference responde el identificador original en `data`. Ese valor se escribe en `rut` sin modificar. Si cumple un patron de RUT chileno, se agrega `rut_normalized` con formato estandar y guion.

Nunca se fabrica un RUT. Si la respuesta no es un RUT reconocible, se conserva el valor original y `rut_normalized` queda vacio.

## 5. Referencias invalidas

Las referencias que no cumplen el formato documentado de la API no se envian. Se conservan en la salida con:

```text
resolution_status=invalid_format
resolution_error=<detalle>
```

Esto es necesario para los datos de prueba de QA y para evitar llamadas inutiles que terminarian en HTTP 400.

## 6. Revocaciones y eventos sin permisos

Los registros `CompanyConsentRevocation` pueden tener `data_permissions` vacio. No se eliminan. Se generan como una fila de evento con:

```text
consent_status=revoked
version_source_type=CompanyConsentRevocation
```

Los campos de permiso quedan vacios, pero los datos del agreement, evidencias y referencia permanecen.

## 7. Estados de resultado

| Estado | Significado |
|---|---|
| `completed` | Todos los registros y referencias fueron procesados sin incidencias. |
| `completed_with_errors` | Se genero la salida, pero hubo referencias invalidas, errores de API o JSON de permisos con problemas. |
| `failed` | Fallo una etapa critica: configuracion, autenticacion inicial, exportacion, descarga o carga de Storage. |

`completed_with_errors` no oculta problemas: el detalle queda en cada fila y en las metricas de la corrida.
