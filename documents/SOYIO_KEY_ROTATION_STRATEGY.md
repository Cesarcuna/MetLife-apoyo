# Soyio API Keys - Estrategia de rotacion y ownership

## 1) Decision arquitectonica

La rotacion de API keys de Soyio se gestiona fuera del runtime de Storage API.

Motivo:

- Separacion de responsabilidades.
- Menor riesgo de seguridad en tiempo de ejecucion.
- Mejor control operacional por equipos de plataforma/seguridad.

## 2) Donde se trata la rotacion

- Scripts operativos en el workspace de integracion:
  - scripts/rotate-soyio-apikey.ps1
  - scripts/register-monthly-rotation-task.ps1
- Secretos administrados en vault/variables seguras (Key Vault).

## 3) Que hace Storage API respecto a keys

Storage API solo consume la key activa de ejecucion:

- SOYIO_API_KEY

No crea, refresca ni revoca keys directamente en este MVP.

## 4) Flujo recomendado de rotacion

1. Ejecutar refresh de key operativa con key bootstrap.
2. Actualizar secreto en Key Vault.
3. Aplicar recarga de runtime en AKS (rollout restart del deployment).
4. Validar corrida de sync.
5. Revocar key anterior cuando la nueva este estable.

## 4.1) Aclaracion operativa para AKS

- Con Key Vault, la rotacion NO requiere nuevo build de imagen.
- Tampoco requiere cambio de codigo para cada rotacion.
- Si la aplicacion lee la key al arranque (patron actual), se necesita reiniciar pods para cargar el nuevo secreto.

En terminos practicos:

1. Cambiar secreto en Key Vault.
2. Esperar sincronizacion del secreto a runtime (segun mecanismo del cluster).
3. Ejecutar rollout restart del deployment.
4. Verificar healthcheck y corrida de sync.

Resultado:

- Hay una accion operativa de despliegue (restart), pero no un release de software ni publicacion de imagen.

## 5) Controles minimos

- Nunca registrar secretos en logs.
- Nunca commitear archivos .key o tokens.
- Mantener bootstrap key con uso restringido.
- Alertar fallas 401/403 durante sync (posible key invalida o vencida).

## 6) Evolucion posible

En una fase posterior se puede agregar un endpoint administrativo para comprobar estado de key (health de autenticacion), pero la rotacion debe seguir siendo un proceso controlado por plataforma.