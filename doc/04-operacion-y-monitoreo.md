# Operacion y Monitoreo

## Disparador

El Storage Service no contiene un scheduler interno. Un scheduler externo debe invocar el endpoint cada seis horas. En el diseno ADF actual, el trigger correcto es `TR_SOYIO_CDZ_6H`; el trigger legacy de Synapse debe permanecer desactivado.

El endpoint tambien puede ejecutarse manualmente para QA o reproceso controlado.

## Logs

Todos los mensajes relevantes deben buscarse por `runId` y `x-gssp-transactionid`.

El flujo registra:

- inicio y fin de corrida;
- configuracion resuelta sin imprimir secretos;
- ventana incremental;
- exportId y estado de polling;
- descarga y tamano de archivo;
- inicio/fin de cada segmento de resolucion;
- longitud de referencia, nunca la referencia completa;
- status HTTP de la API;
- reintentos y backoff;
- renovacion de token;
- carga de RDZ y DDZ;
- metricas finales;
- etapa exacta donde ocurrio un error.

Nunca se deben registrar API keys, subscription keys, client assertions o access tokens.

## Metricas de respuesta

La respuesta exitosa incluye `normalizedMetrics` con:

```text
sourceRows
outputRows
permissionRows
eventRows
resolved
invalidFormat
failed
revoked
```

## Diagnostico por etapa

| Sintoma | Revisar |
|---|---|
| `failed` antes de exportar | Variables obligatorias y configuracion del ambiente. |
| Error al crear exportacion | `SOYIO_URL_BASE`, `SOYIO_API_KEY`, ventana y permisos de Soyio. |
| Timeout de polling | Estado de exportacion en Soyio y `poll-timeout-seconds`. |
| Error de descarga | URL firmada, conectividad y limite de bytes. |
| Muchos `invalid_format` | Formato de referencias del CSV y compatibilidad con User Reference API. |
| Muchos `api_400` | Payload o referencias no aceptadas por la API. |
| `api_401`/`api_403` | Token, API key, subscription key, client id y scope. |
| `api_429` | Reducir `batch-size`, aumentar `delay-millis` y revisar limites APIM. |
| Error DDZ | Conexion Storage, contenedor `ddz` y ruta `soyio/agreements`. |
| Watermark no cambia | Fallo de una etapa critica posterior a la exportacion. |

## Reproceso

Un reproceso debe usar una ventana explicita (`fromDateTime` y `toDateTime`) y un `runId` nuevo. No se debe modificar manualmente el watermark para ocultar una falla.

## Validacion QA

Para el CSV de QA se esperan referencias invalidas y posiblemente errores de API. El resultado correcto es un archivo completo con estados por fila y `completed_with_errors`, no la perdida silenciosa de los registros.
