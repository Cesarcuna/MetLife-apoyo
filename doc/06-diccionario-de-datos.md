# Diccionario de Datos del CSV Normalizado

## Convenciones

- `Origen Soyio`: campo recibido en el CSV original.
- `Origen permiso`: campo extraido desde `data_permissions`.
- `Origen User Reference API`: valor obtenido al resolver `user_reference`.
- `Calculado`: valor generado por Storage Service.
- Los nombres de campo se mantienen en ingles para conservar compatibilidad tecnica.

## Identidad y acuerdo

| Campo | Origen | Definicion | Ejemplo |
|---|---|---|---|
| `rut` | User Reference API | Identificador original devuelto en `data`. Se espera que corresponda al RUT. Se conserva exactamente como responde la API. | `12345678K` |
| `rut_normalized` | Calculado | RUT con formato estandar y guion cuando el valor cumple el patron esperado. Si no se puede validar, queda vacio. | `12345678-K` |
| `user_reference` | Soyio | Referencia opaca asociada al usuario. Se conserva para trazabilidad y reproceso. | `AbC123...` |
| `agreement_id` | Soyio | Identificador unico del agreement. | `agr_xxx` |
| `created_at` | Soyio | Fecha y hora en que se creo el agreement. | `2026-09-11 16:21:36 UTC` |
| `version` | Soyio | Version del agreement para el sujeto. | `1` |
| `subject_id` | Soyio | Identificador interno del sujeto o entidad asociada al consentimiento. | `ent_xxx` |
| `subject_type` | Soyio | Tipo de sujeto registrado. En el archivo analizado aparece `Entity`. | `Entity` |

## Origen del cambio de consentimiento

| Campo | Origen | Definicion | Ejemplo |
|---|---|---|---|
| `version_source_type` | Soyio | Tipo de evento que genero la version. Permite distinguir un consentimiento, una accion o una revocacion. | `ConsentCommit` |
| `version_source_id` | Soyio | Identificador del evento origen. | `ccom_xxx` |
| `consent_status` | Calculado | Estado funcional del registro. Es `active` para acuerdos normales y `revoked` para `CompanyConsentRevocation`. | `active` |
| `previous_evidence_ids` | Soyio | Lista serializada de evidencias relacionadas con el agreement anterior o con la operacion. | `["evd_xxx"]` |

## Permisos de datos

Estos campos salen de cada objeto dentro de `data_permissions`. Por eso un agreement puede generar varias filas.

| Campo | Origen | Definicion | Ejemplo |
|---|---|---|---|
| `data_category` | Permiso | Categoria de dato involucrada en el consentimiento. | `user.contact.email` |
| `data_label` | Permiso | Etiqueta legible o de negocio de la categoria. Puede ser igual a `data_category` o venir vacia. | `user.contact.email` |
| `data_use` | Permiso | Finalidad autorizada para usar el dato. | `marketing` |
| `data_subject` | Permiso | Tipo de persona o sujeto al que pertenece el dato. | `prospect` |
| `scope_type` | Permiso | Tipo de alcance del permiso. Puede ser `branch`, `product` u otro valor definido por Soyio. | `branch` |
| `scope_id` | Permiso | Identificador del alcance al que aplica el permiso. | `branch_xxx` |
| `scope_version` | Permiso | Version de la configuracion del alcance. | `1` |
| `expires_at` | Permiso | Fecha de expiracion del permiso. Vacio o `null` significa que el permiso no trae expiracion. | `2026-09-25T15:38:53Z` |

## Resolucion y trazabilidad

| Campo | Origen | Definicion | Ejemplo |
|---|---|---|---|
| `resolution_status` | Calculado | Resultado de resolver `user_reference`. | `resolved` |
| `resolution_error` | Calculado | Detalle acotado del error cuando la referencia no se pudo resolver. Nunca contiene tokens. | `HTTP 400: invalid user_reference` |
| `data_permissions_raw` | Soyio | JSON original de permisos. Se conserva especialmente para filas sin permisos o con JSON no interpretable. | `[]` |
| `source_file` | Calculado | Nombre del archivo original procesado. | `agreements-2026-09-11.csv` |
| `processed_at` | Calculado | Fecha y hora UTC en que Storage Service genero la fila normalizada. | `2026-09-24T12:00:00Z` |

## Valores de `resolution_status`

| Valor | Significado |
|---|---|
| `resolved` | La API respondio correctamente y devolvio `data`. |
| `invalid_format` | La referencia no cumple el formato minimo o los caracteres permitidos por la API. |
| `api_400` | La API rechazo el request por validacion. |
| `api_401` | Token, API key o credenciales invalidas o expiradas. |
| `api_403` | La suscripcion APIM no tiene permisos. |
| `api_429` | Se alcanzo el limite de consumo. |
| `api_5xx` | Error temporal o interno de la API. |
| `auth_failed` | No fue posible obtener el token antes de resolver. |
| `failed` | Error no clasificado durante la resolucion o transformacion. |

## Interpretacion de una fila

Una fila con `consent_status=active` y `data_category` informado representa un permiso de uso de datos.

Una fila con `consent_status=revoked` y campos de permiso vacios representa un evento de revocacion que debe conservarse, aunque el agreement no traiga permisos en ese registro.

Una fila con `resolution_status` distinto de `resolved` sigue siendo util: conserva el agreement y el permiso, pero indica que el RUT requiere reproceso o investigacion.
