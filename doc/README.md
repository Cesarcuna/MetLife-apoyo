# Soyio Sync y Normalizacion de Agreements

Documentacion funcional, tecnica y operativa del proceso Soyio implementado en Storage Service.

## Indice

1. [Vista de alto nivel](01-alto-nivel.md)
2. [Reglas de negocio](02-negocio.md)
3. [Diseno tecnico](03-tecnico.md)
4. [Operacion y monitoreo](04-operacion-y-monitoreo.md)
5. [Configuracion y seguridad](05-configuracion-y-seguridad.md)
6. [Diccionario de datos](06-diccionario-de-datos.md)
7. [Arquitectura](diagrams/01-arquitectura.mmd)
8. [Flujo principal](diagrams/02-flujo-principal.mmd)
9. [Normalizacion de agreements](diagrams/03-normalizacion-agreements.mmd)
10. [Errores y estados](diagrams/04-errores-y-estados.mmd)

## Resumen

El endpoint `POST /v1/domain/storage/soyio/sync` ejecuta una corrida incremental de Soyio. La corrida:

1. Lee el watermark desde CDZ.
2. Crea y monitorea una exportacion de Soyio.
3. Guarda el CSV crudo en RDZ.
4. Resuelve `user_reference` a su identificador original, esperado como RUT.
5. Expande `data_permissions` a una fila por permiso.
6. Conserva revocaciones y referencias que no puedan resolverse.
7. Guarda el CSV normalizado en DDZ.
8. Actualiza el watermark solamente despues de completar las salidas y sus marcadores `_SUCCESS`.

## Rutas principales

```text
RDZ: rdz/soyio/<resource>/YYYY/MM/DD/<archivo-crudo>.csv
DDZ: ddz/soyio/agreements/<archivo-normalizado>.csv
CDZ: cdz/soyio/control/watermark.json
CDZ: cdz/soyio/control/history/<runId>.json
```
