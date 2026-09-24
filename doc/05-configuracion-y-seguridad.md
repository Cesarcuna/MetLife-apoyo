# Configuracion y Seguridad

## Rutas de Storage

| Uso | Contenedor | Ruta |
|---|---|---|
| CSV crudo | `rdz` | `soyio/<resource>/YYYY/MM/DD/` |
| CSV normalizado | `ddz` | `soyio/agreements/` |
| Watermark | `cdz` | `soyio/control/watermark.json` |
| Historial | `cdz` | `soyio/control/history/<runId>.json` |

RDZ, DDZ y CDZ usan la misma variable de conexion de Storage del ambiente.

## Variables por ambiente

Variables no secretas:

```text
SOYIO_USER_REFERENCE_TENANT_ID=ca56a4a5-e300-406a-98ff-7e36a0baac5b
SOYIO_USER_REFERENCE_ENV=dev|qa|int|prod
SOYIO_USER_REFERENCE_CLIENT_ASSERTION_URL=https://wba13545e1dv01.ase01e1np.appserviceenvironment.net/generate
SOYIO_USER_REFERENCE_BASE_URL=<APIM base URL del ambiente>
```

Variables secretas que deben inyectarse por el mecanismo corporativo:

```text
SOYIO_API_KEY
SOYIO_USER_REFERENCE_CLIENT_ID
SOYIO_USER_REFERENCE_SCOPE
SOYIO_USER_REFERENCE_API_KEY
SOYIO_USER_REFERENCE_SUBSCRIPTION_KEY
STORAGE_CONN_STR o STORAGE_CONN_STR_DEV
```

## Valores de APIM

```text
DEV  https://latam.dev.internal.apis.metlife.com/soyio-cm
QA   https://latam.qa.internal.apis.metlife.com/soyio-cm
INT  https://latam.int.internal.apis.metlife.com/soyio-cm
PROD https://latam.internal.apis.metlife.com/soyio-cm
```

Endpoint de resolucion:

```text
/v1/user-references/resolve
```

## Reglas de seguridad

- No hardcodear secretos en codigo, YAML, README ni diagramas.
- No escribir tokens o assertions en logs.
- Usar HTTPS en todas las llamadas.
- Aplicar minimo privilegio a la identidad que escribe Storage.
- Mantener las claves de Soyio y User Reference separadas.
- Rotar secretos fuera del runtime y reiniciar el workload si la plataforma inyecta valores al arranque.
- No imprimir el valor de `user_reference`; solo su longitud y estado.

## Parametros de capacidad

```yaml
user-reference:
  batch-size: 50
  delay-millis: 100
  max-retries: 2
```

Si APIM responde `429`, reducir `batch-size` o aumentar `delay-millis`. Los cambios deben hacerse por ambiente, sin modificar el codigo.
