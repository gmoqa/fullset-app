# Catálogos que se actualizan solos

Cómo llegan listas nuevas a los teléfonos **sin publicar un APK**. Escrito el **2026-08-20**.

---

## El problema

Los catálogos viajaban horneados en el APK: **18 MB de los ~20 MB** del binario. Mejorar una lista
—corregir 40 fechas, agregar una consola, completar desarrolladoras— no llegaba a nadie hasta la
próxima release.

Para las consolas retro eso se tolera: sus listas están casi cerradas. Para las **modernas** no,
porque su lista nunca está terminada. Una PS5 declarada con catálogo vacío quedaba congelada en cero
hasta que alguien publicara una versión.

## La idea

**El repositorio público es el backend.** No hay servidor que mantener: son archivos estáticos
servidos por el CDN de GitHub, y el camino de escritura es un pull request.

    PR → merge → raw.githubusercontent.com → el teléfono lo ve al día siguiente

Esto permite el modelo que las modernas necesitan: **empezar con una lista incompleta e irla
completando semana a semana**, con cada mejora llegando sola.

## Cómo funciona

El APK sigue trayendo los catálogos horneados, y esos son el **piso**. Sin red, sin permiso o con
GitHub caído, la app funciona igual que siempre. Encima puede haber una copia descargada, que gana
cuando existe:

    leer un catálogo:
      1. filesDir/catalogs/<archivo>      ← lo descargado, si está
      2. assets/catalogs/<archivo>        ← el piso horneado

La sincronización corre después de sembrar, en segundo plano, **una vez por día**:

    GET manifest.json  (24 KB)
      ├── schema > el que entiendo  → parar, quedarse con lo horneado
      ├── ningún sha256 cambió      → nada que hacer
      └── por cada archivo distinto → bajarlo, verificarlo, escribirlo atómico

`manifest.json` es lo que hace esto posible, y **fue escrito para esto**: trae `sha256` y `bytes` por
archivo, así que se puede preguntar *"¿cambió?"* bajando 24 KB en vez de 18 MB.

## Las decisiones, y por qué

### No se calcula SHA-256 en el cliente

El hash sirve para dos cosas y solo una necesita calcularlo:

- **Detectar cambios**: alcanza con recordar el hash que **declaró el manifest** cuando se bajó cada
  archivo. Se comparan dos textos.
- **Verificar integridad**: se usan los `bytes` del manifest y el hecho de que un JSON truncado no
  parsea. El modo de falla real es una descarga cortada, y eso lo cazan las dos cosas. La
  manipulación en tránsito ya la cubre HTTPS.

Implementar SHA-256 en `commonMain` habría sido ~80 líneas de criptografía sin dueño, para verificar
algo que el transporte ya garantiza.

### Un `schema` mayor detiene todo

Lo pedía el propio generador del manifest, antes de que existiera este cliente:

> Un cliente que entienda hasta el esquema N tiene que **negarse** a leer un manifest con esquema
> N+1 en vez de intentarlo: si no, el día que el formato cambie, cada versión vieja instalada se
> rompe sola.

Es la diferencia entre una app que deja de actualizarse —molesto— y una que se rompe sola.

### Al actualizar el APK se descarta lo descargado

Si no, una copia bajada con la versión anterior le ganaría a un catálogo horneado más nuevo, y la app
mostraría **datos peores después de actualizar**. Cuesta volver a bajar lo que cambió, una vez. La
alternativa es un error silencioso y muy difícil de ver.

Se detecta comparando la `version` del manifest horneado con la que se guardó al sincronizar.

### `platforms.json` se escribe último

Lleva los conteos por consola y qué archivo usa cada una. Aplicarlo con catálogos a medio bajar haría
que la app prometiera consolas que no tiene, o contara juegos que no llegaron. Si algún catálogo
falla, el registro no se toca.

### La escritura es atómica

Primero a un temporal, después renombrar. Sin eso, una escritura cortada —sin batería, sin espacio,
la app muerta— deja un catálogo truncado en su lugar definitivo y la app arranca leyendo JSON roto
**en vez del que trae horneado**. El piso dejaría de ser un piso.

### Se ve y se puede pedir a mano

Hay una entrada en Settings, «Check for catalog updates», que saltea el límite diario. Una función
que abre conexiones en una app que promete que las notas no salen del dispositivo **no puede ser
invisible**. El texto dice qué se manda: nada de la colección, solo un GET a un archivo público.

---

## Cómo se agrega o mejora una lista

1. Editar el JSON en `data/catalogs/` (o correr el enriquecedor que corresponda).
2. `python3 tools/catalog_lint.py` — tiene que dar 0.
3. `python3 tools/platform_counts.py && python3 tools/catalog_manifest.py`.
4. PR → merge a `main`.

Al día siguiente los teléfonos lo tienen. No hace falta tocar la app.

**El manifest hay que regenerarlo**: es lo que dispara la actualización. El lint falla si está
desactualizado, justamente para que no se olvide.

## Consolas modernas: la lista incompleta

`ps5-usa.json` arranca **vacío** y es válido — el lint lo acepta y los tests lo afirman
explícitamente. La consola aparece con 0 juegos y va creciendo PR a PR.

Es a propósito: en las modernas la frontera entre físico y digital es borrosa, y las listas
automáticas traen cientos de títulos que nunca existieron en disco. Es el mismo problema que ya nos
costó caro en 3DS y DS. Curar a mano, de a poco, da una lista peor al principio y mejor al final.

## Lo que queda afuera por ahora

- **No borra lo descargado cuando vuelve a coincidir con lo horneado.** Ocupa de más hasta la
  próxima actualización del APK, que limpia todo. Se puede afinar si molesta.
- **No hay sincronización selectiva por consola.** Baja lo que cambió, todo o nada.
- **No hay reintento con espera.** Si falla, se prueba de nuevo al día siguiente o cuando lo pidas a
  mano.
