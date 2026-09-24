# Vista de Alto Nivel

## Objetivo

Automatizar la obtencion de agreements desde Soyio y producir un archivo tabular claro para consumo analitico y operativo. El proceso conserva el archivo original y genera una segunda salida enriquecida con el RUT resuelto y los permisos separados.

## Actores

| Actor | Responsabilidad |
|---|---|
| Scheduler externo | Invoca el endpoint cada seis horas o inicia una corrida manual. |
| Storage Service | Orquesta exportacion, resolucion, transformacion, carga y control. |
| Soyio API | Genera la exportacion asincrona y entrega el archivo. |
| User Reference API | Resuelve `user_reference` al identificador original. |
| Azure Storage | Mantiene RDZ, DDZ y CDZ. |
| Plataforma de secretos | Inyecta credenciales y parametros por ambiente. |

## Entradas y salidas

### Entrada de Soyio

El archivo contiene agreements y un JSON embebido en `data_permissions`. El archivo de prueba analizado contiene 8,893 filas y 5,061 referencias unicas.

### Salida cruda

Se conserva en RDZ sin alterar el contenido recibido desde Soyio.

### Salida normalizada

Se publica en DDZ con una fila por permiso. Los eventos sin permisos, como revocaciones, tambien se conservan como filas de evento.

## Principio de seguridad operacional

El watermark no avanza por el hecho de que Soyio haya terminado. Solo avanza cuando:

- el archivo crudo fue cargado en RDZ;
- el CSV normalizado fue cargado en DDZ;
- los marcadores `_SUCCESS` fueron escritos;
- el historial de la corrida puede registrarse.

Si falla una etapa de infraestructura, la corrida termina con error y el watermark conserva la ultima corrida exitosa.
