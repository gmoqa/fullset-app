# Catálogos — procedencia, normalización y mejora continua

Los catálogos (`data/catalogs/*.json`) **no son una lista estática** de juegos
retro. Son un **dataset vivo** que se corrige y mejora con el tiempo: títulos, fechas, editoras,
carátulas y regiones se refinan a medida que aparecen mejores fuentes (una fecha que pasa de solo
año a año-mes confirmada por una revista/base de datos, una carátula bajo el nombre correcto, un
título mal traducido que se arregla). Este documento fija **de dónde sale cada dato**, **cómo se
mejora**, y **hacia dónde va** la calidad. No aspira a ser perfecto ya; aspira a tener los cimientos
para mejorar sin perder el rastro de por qué.

## Esquema canónico

Cada catálogo es un array JSON, **un objeto por línea**, **ordenado por `slug`**, con estas 11 claves
SIEMPRE presentes (aunque vacías) — lo valida `tools/catalog_lint.py`:

```
{title, platform, region, year, releaseDate, publisher, genre, slug, serial, coverUrl, rating}
```

- `slug`: identidad estable del juego dentro de su plataforma (clave para overrides y merges).
- `region`: `"NTSC-U"` | `"NTSC-J"` | `"PAL"` — la de ese archivo (ver [Región](#región)).
- `year`: entero o `null` — lo que usa la app para mostrar/ordenar.
- `releaseDate`: fecha ISO de **precisión variable** `""` | `"1991"` | `"1991-06"` | `"1991-06-11"`. Es
  la versión precisa y con fuente del año; **mejora con el tiempo** cuando aparece un dato más fino.
- `rating`: clasificación normalizada `""` | `"VRC: GA"` | `"ESRB: Teen"` … (VRC = sistema Sega pre-1994).
- El resto: texto (vacío `""` si falta).

## Procedencia — de dónde obtenemos cada dato

| Campo | Fuente | Cómo |
|---|---|---|
| `title` / `year` / `publisher` | **Wikipedia** "List of \<consola\> games", columna **North America** | scrape del wikitext (`catalog_common.py`), filtrando lanzamientos NA = NTSC-U |
| `serial` | **libretro-database** DAT (`metadat/serial/…`, CC BY-SA 4.0) | match por título/región; no todas las plataformas tienen DAT |
| `serial` (SNES, huecos) | **[SNES Central](https://snescentral.com)** | tabla "Cartridge label information", fila Americas/USA; consulta puntual respetando su `Crawl-delay: 10` |
| `serial` (consolas de disco) | **Redump**, vía `libretro-database/metadat/redump/` | `tools/enrich_serials_redump.py`, tomando la entrada de la región del catálogo |
| `coverUrl` (PS3) | **SteamGridDB** | `tools/enrich_covers_steamgriddb.py`, **solo con título idéntico**; libretro publica apenas 67 tapas de PS3 |
| `releaseDate` (no-Sega) | **Wikipedia**, listas "List of … games" | `tools/enrich_dates_wikipedia.py`; la columna de región se lee de la cabecera de la tabla, no se asume |
| `releaseDate` / `publisher` (huecos) | **Wikipedia**, ficha del artículo de cada juego | `tools/enrich_infobox_wikipedia.py`, leyendo `{{Video game release\|NA\|…\|EU\|…}}` acotado al bloque de la consola; la editora también sale por región |
| `coverUrl` | **libretro-thumbnails** (`Named_Boxarts/`) | match por título, prefiriendo la región del catálogo y evitando Beta/Proto |
| `publisher` / `genre` | **libretro-database** (`metadat/publisher/`, `metadat/genre/`, CC BY-SA 4.0) | `tools/enrich_meta_libretro.py`, prefiriendo la etiqueta de región del DAT |
| `publisher` (consolas de disco) | **Sega Retro**, tabla `companies` | rol `Publisher(US/JP/EU)` según la región, con respaldo al `Publisher` genérico |
| `condition` | manual | lo carga el usuario desde la app |

**Elegir la edición de tienda en Redump.** El DAT ordena alfabéticamente, y `Título (Europe) (…)`
viene antes que `Título (Europe, Australia) (…)`, así que quedarse con la primera coincidencia daba
el disco promocional: *Gran Turismo 4* europeo salía con el serial del **disco de demostración de
BMW** y *Killzone* con el de su Bonus Disc. `enrich_serials_redump.py` ahora puntúa cada candidata y
prefiere la de nombre más simple —la edición normal es `Título (Región) (Idiomas)`, las promocionales
agregan un grupo— y desempata descartando los prefijos `SCED`/`SLED`, que son promocionales de PAL y
comparten número con el retail (*My Street* es SCED-51677 **y** SCES-51677). Corrigió **77 seriales**
repartidos en PlayStation, PS2, PS3 y GameCube.

**La región de la carátula se lee del nombre, no se busca como subcadena.** No-Intro lista varias
separadas por coma cuando un mismo cartucho salió en más de un mercado, y el 32X compartía el
americano con Japón: la tapa de EE.UU. de *Doom* se llama `Doom (Japan, USA) (En)`. Buscar `"(USA"`
—con el paréntesis pegado— no la encontraba, así que ganaba `Doom (Europe)`, que sí calzaba, y el
catálogo americano terminaba mostrando cajas europeas.

Corregidas **130 carátulas** en 20 catálogos (32X americano 7, Genesis europeo 89, NES 11…). Quedan
**1.739 que apuntan a otra región y no son un error**: libretro no publica tapa de ese mercado y
caer a otra es lo único posible. Otras 5 son deliberadas — la única americana disponible es una
`(Beta)`, y una caja de prototipo no es la que está en el estante.

## Invariantes semánticos — validar el significado, no solo la forma

Las reglas de forma de `catalog_lint.py` (11 claves, orden, tipos, unicidad, ordenamiento) daban
**OK** mientras el dataset tenía fechas japonesas en catálogos americanos, carátulas europeas en el
americano y seriales de discos promocionales. Una fecha de otra región es estructuralmente
impecable: por eso ninguno de esos defectos se detectó solo, y todos aparecieron de casualidad.

Ahora también se valida el significado:

| Invariante | Por qué | Estado |
|---|---|---|
| `year` es el año de `releaseDate` | Son el mismo hecho contado dos veces; separarse significa que un enriquecedor escribió uno y olvidó el otro | duro, 0 |
| Ningún juego es anterior al lanzamiento de su consola | *The Three Stooges* de PSX estaba fechado en 1987, que es el original de Amiga | duro, 0 (13 corregidos) |
| El prefijo del catalog number no es de otra región | `SHVC-AHZJ-JPN` —con "JPN" en el texto— estaba en el catálogo americano de SNES | duro, 0 (8 vaciados) |
| Las carátulas de otra región no superan la línea base | 1.827 son inevitables (libretro no publica tapa de ese mercado); lo que no se tolera es que **crezcan** | línea base |

Lo que **no** se valida, a propósito: que un juego salga *después* de discontinuar la consola. Es
normal — Tec Toy publicó Master System en Brasil hasta 2011 y Japón tuvo Dreamcast hasta 2004.
Validarlo daba 151 falsos positivos.

El prefijo de serial solo diagnostica región en Sony y Nintendo. En Sega la misma `T` aparece en
las tres, y en PS3 los prefijos asiáticos (`BLAS`, `BCAS`, `BLKS`) también: esos quedan exentos.

`tools/test_catalog_lint.py` prueba cada invariante **contra el defecto real que le dio origen**,
más los casos legítimos que no debe marcar. Un invariante sin test es una promesa, no una garantía.

**Rating con sistema.** El 40% de los ratings (1.549) no llevaba prefijo: eran `All ages`, `18+`,
`Violence`, `X (adults only)`. Todos NTSC-J y de plataformas Sega — la autorregulación previa a
CERO. Pasaron a `Sega: All ages` y equivalentes, así que el esquema `SISTEMA: valor` ahora se
cumple en el 100%.

## Normalización — identidad, vocabularios y desambiguación

**El slug se deriva del título y no se congela.** `slug()` descartaba los diacríticos en vez de
transliterarlos, así que `Astérix` daba `ast-rix` y `Crüe Ball` daba `cr-e-ball`: la `é` no es
`[a-z0-9]` y el `re.sub` final la convertía en separador, partiendo la palabra. Corregido, más la
convención de `&` (408 títulos ya usaban `and`, 39 lo descartaban), quedaron **310 slugs
normalizados**. El lint exige ahora que el slug derive del título, admitiendo un sufijo extra para
las desambiguaciones a mano —`Street Fighter Zero 2` y `Street Fighter Zero 2'` darían el mismo, y
el segundo lleva su catalog number pegado—.

Renombrar un slug es delicado porque la tabla `game` de la app lo usa como vínculo con el catálogo,
y el refresco hacía `?: continue`: el juego dejaba de recibir actualizaciones **en silencio**. Ahora
`DiarySeeder` tiene un índice de respaldo por título normalizado que reconoce el juego y le repara
el slug, así que este renombre y cualquier futuro se resuelven solos.

**Editoras.** Eran 1.663 grafías para 1.574 empresas. `tools/alias-editoras.json` colapsa **89
variantes** que son puro ruido —mayúsculas, comas, `Inc.`, `Co.`, `Corporation`— y deja intactas las
palabras que distinguen una entidad de otra: `Sega of America` y `Sega Europe` son editoras
distintas y siguen separadas. Donde la frecuencia elegía una grafía que no es la de la marca hay una
excepción explícita: `SEGA` (1.094) perdía contra `Sega` (736) por conteo, pero *Sega* es como lo
escriben Wikipedia, Sega Retro y esta documentación.

**Géneros.** `Various` no es un género sino la ausencia de uno disfrazada de dato: se vació.
`Casual Game`→`Casual`, `Sports with Animals`→`Sports`, `Music / Dancing`→`Music`. **No** se
fusionaron `Shooter`, `Shoot'em Up` y `Lightgun Shooter`: parecen solapados y son subgéneros
distintos.

**Lo que NO se normalizó, a propósito.** 228 títulos de catálogos Sega llevan su propia plataforma
entre paréntesis (`Doom (32X)`, `Putt & Putter (Game Gear)`) porque Sega Retro desambigua contra su
wiki entera. Sacarlo es puramente cosmético —`core()` ya los ignora al cruzar— y el costo es
renombrar 228 slugs *y* cambiar el título mostrado, que rompe las dos vías de reparación a la vez.
No compensa.

**Desambiguación.** 31 juegos son indistinguibles al normalizar, porque `core()` borra los
paréntesis para poder cruzar con los nombres de No-Intro y eso también borra los desambiguadores
propios: `Sonic the Hedgehog` y `Sonic the Hedgehog (Genesis)`, `Jeopardy!` y `Jeopardy! (2012)`.
Cualquier matcher que se quede con el primero elige al azar. El validador de la colección ahora
prefiere la coincidencia **literal** y, si sigue habiendo más de una, pregunta en vez de elegir.

### Estado por catálogo (auditado 2026-08-03)

**Sega: cobertura completa.** Las 8 consolas × las 3 regiones (**6226 juegos**), con Sega Retro como
fuente única vía API (`tools/local/segaretro_api.py`). Fechas ISO de precisión variable, catalog
number y rating salen todos de ahí; la carátula de libretro-thumbnails prefiriendo la región.

| Catálogo | Juegos | año/serial/cover | rating |
|---|---|---|---|
| `sg-1000-jp.json` | 79 | 100/98/91 | — |
| `master-system-usa.json` | 114 | 100/94/92 | — |
| `master-system-jp.json` | 84 | 98/96/83 | — |
| `master-system-eu.json` | 363 | 93/83/73 | 26% |
| `genesis-usa.json` | 711 | 99/98/92 | 68% |
| `genesis-jp.json` | 435 | 99/99/87 | 3% |
| `genesis-eu.json` | 747 | 88/84/79 | 41% |
| `game-gear-usa.json` | 234 | 99/99/91 | 61% |
| `game-gear-jp.json` | 199 | 98/97/81 | 23% |
| `game-gear-eu.json` | 208 | 95/97/87 | 32% |
| `sega-cd-usa.json` | 146 | 100/97/93 | 83% |
| `sega-cd-jp.json` | 116 | 99/99/87 | 5% |
| `sega-cd-eu.json` | 109 | 90/97/93 | 68% |
| `32x-usa.json` | 31 | 96/96/87 | 96% |
| `32x-jp.json` | 18 | 94/94/72 | 88% |
| `32x-eu.json` | 26 | 96/96/92 | 96% |
| `saturn-usa.json` | 251 | 99/100/92 | 99% |
| `saturn-jp.json` | 1091 | 99/99/79 | 88% |
| `saturn-eu.json` | 247 | 98/98/94 | 98% |
| `dreamcast-usa.json` | 248 | 100/93/99 | 93% |
| `dreamcast-jp.json` | 545 | 99/94/77 | 91% |
| `dreamcast-eu.json` | 224 | 97/97/94 | 96% |

**Editora y género** salen de `metadat/publisher/` y `metadat/genre/` de libretro-database para las
consolas de cartucho, y de la tabla `companies` de Sega Retro para las de disco (Saturn, Sega CD,
Dreamcast), que libretro no cubre. Ambas fuentes distinguen región: la editora cambia según el
mercado, y en Sega Retro el rol viene etiquetado (`Publisher(US)`, `Publisher(JP)`, `Publisher(EU)`).
Era el hueco más viejo del dataset — los catálogos Sega tenían editora en **0%**.

Dos cosas que sorprenden y son correctas: **Master System PAL (363) triplica al americano (114)** —
en Europa y Brasil fue *la* consola de Sega, no el Genesis; y **Saturn JP (1091) cuadruplica al
americano** — la Saturn se vendió sobre todo en Japón.

**La clasificación depende de quién clasifica.** El mismo código significa cosas distintas por
territorio y época, así que se normaliza con ambos datos: `12` es **USK** en Alemania, **ClassInd**
en Brasil y **ELSPA/PEGI** en el resto de Europa. Ojo con la época: **PEGI recién existe desde 2003**,
o sea después de toda la era que catalogamos (la Dreamcast se discontinuó en 2001), así que lo
europeo es ELSPA salvo lanzamientos tardíos. En Japón, antes de CERO (2002) regía la autorregulación
de Sega: 全年齢 (todas las edades), 18禁 y la marca X. En EE.UU., VRC hasta 1994 y ESRB después.

**Game Boy Advance: el DAT sin Wikipedia.** 2.624 cartuchos, y el único de los tres portátiles de
Nintendo que **no necesita** enriquecerse desde Wikipedia.

| Catálogo | Cartuchos | año/editora/género/serial/cover |
|---|---|---|
| `gba-usa.json` | 982 | 80/98/**98**/100/96 |
| `gba-jp.json` | 797 | 92/95/**88**/100/96 |
| `gba-eu.json` | 845 | 74/98/**97**/100/94 |

Dos diferencias con DS y 3DS, y las dos cambian el camino:

- **La GBA no tuvo tienda**, así que el DAT no se usa para separar lo físico de lo digital —todo
  salió en cartucho— sino porque es la lista más completa y la única con el `serial` de la etiqueta.
- **libretro sí publica sus metadatos**: género 3.110, editora 3.188 y año 2.088 entradas, contra
  ~100 y **0** para DS y 3DS. Con eso alcanza, así que el builder es de **una sola fuente** —menos
  superficie donde equivocarse— y el género queda arriba del 88%, que solo el Atari 2600 supera.

**La caja japonesa es apaisada y la occidental no.** Medido sobre 90 al azar: 35 de 35 cuadradas en
NTSC-U, 31 de 31 en PAL, y 20 de 24 **anchas (1.59)** en NTSC-J. Va a `COVER_ASPECT_BY_REGION`, como
la Saturn y la Sega CD. Un solo número dejaba media biblioteca con banda a los costados.

**Nintendo DS: la biblioteca más grande del dataset.** 6.092 cartuchos entre las tres regiones —más
que la PS2 y la SNES juntas—, por el mismo camino que la 3DS: DAT primero, Wikipedia como
enriquecedor. La maquinaria común de los DAT de No-Intro vive en `tools/nointro.py`.

| Catálogo | Cartuchos | año/editora/serial/cover |
|---|---|---|
| `ds-usa.json` | 1782 | 54/82/**100**/93 |
| `ds-jp.json` | 1875 | 45/48/**100**/95 |
| `ds-eu.json` | 2435 | 24/57/**100**/91 |

**Por qué el año de PAL queda en 24%.** Las cuatro páginas de DS traen `Título | Desarrolladora |
Editora | First released | JP | NA | EU | AU`, donde las cuatro últimas son **tildes** y la fecha es
**una sola**: la del primer mercado que lo recibió, con su región adentro de la plantilla
(`{{#invoke:vgrtbl|main|EU|2008-10-31}}`).

Esa fecha se asigna **solo a la región que la plantilla nombra**. Repartirla a las tres daría casi
100% de cobertura, pero estaría afirmando que un juego japonés de 2005 salió en Europa en 2005, y en
todo el resto del dataset `releaseDate` significa "salió en ESTA región en esta fecha". Como casi
ningún juego debutó en Europa, PAL se queda con pocas. Es un hueco honesto, no un dato inventado.

La editora sí es una sola columna para todo el juego, así que se aplica a las tres regiones.

**Nintendo 3DS: el primero que se arma desde el DAT y no desde Wikipedia.** Es también el primero
con **serial al 100%** y el único **apaisado** (aspecto 1.13; el resto del dataset es vertical).

| Catálogo | Cartuchos | año/editora/serial/cover |
|---|---|---|
| `3ds-usa.json` | 419 | 51/38/**100**/93 |
| `3ds-jp.json` | 661 | 27/25/**100**/96 |
| `3ds-eu.json` | 547 | 43/33/**100**/93 |

**Por qué se invirtieron las fuentes.** La lista de Wikipedia tiene 1.078 juegos y **más de la mitad
son de eShop**, que nunca existieron en cartucho. Para una app de colección física eso no es un juego
que puedas tener, y además hundía la cobertura de carátulas al **42%**: libretro-thumbnails sigue a
No-Intro, que cataloga volcados de cartucho, así que un título solo-descarga no tiene tapa porque no
tuvo caja. Se verificó con casos conocidos —los 7 retail probados aparecen y 5 de 7 de eShop no; los
2 que sí (BOXBOY!, Shovel Knight) tuvieron edición física después—.

La fuente principal pasa a ser el DAT de **No-Intro** (`metadat/no-intro/`), que es literalmente la
lista de cartuchos y trae el `serial` de la etiqueta (`CTR-P-BP4J`). Wikipedia queda como
enriquecedor de año y editora, por título.

**El precio, y por qué se pagó.** Los títulos vienen en formato de volcado y hay que convertirlos
(`Sims 3, The (USA) (En,Fr,Es)` → `The Sims 3`; el guión con espacios es como No-Intro escribe los
dos puntos, que no pueden ir en un nombre de archivo). Y el año y la editora solo aparecen donde el
título cruza con Wikipedia: **51% / 27% / 43%**. La alternativa —Wikipedia primero, filtrando por el
DAT— daba cada entrada completa pero **la mitad de los cartuchos**: 216 contra 419 en NTSC-U, porque
los dos catálogos nombran distinto (`Disney Frozen - Olaf's Quest` contra `Frozen: Olaf's Quest`).
En una app que mide completitud —"148 of 1893"— el denominador tiene que ser la biblioteca real: un
juego sin año se puede tener igual, uno que no está en la lista no.

**Regiones.** El DAT reparte por país, así que PAL se arma como **unión de territorios** igual que en
Sega: Europa + Alemania, Francia, España, Italia, Países Bajos, Reino Unido, Rusia y Australia.
Canadá va a NTSC-U. **Corea (102) y Taiwán (31) quedan afuera** porque no tenemos región para ellos,
y meterlos en otra sería mentir sobre dónde salió ese cartucho.

Del DAT se filtra lo que no es un cartucho vendido —eShop, beta, proto, demo, kiosk: 21 de 2.076— y
se **deduplica por título**, porque las 259 `(Rev N)` son el mismo cartucho reimpreso.

**Portátiles aparte.** La 3DS y la Game Gear llevan `info.handheld` y viven en su propia solapa del
paso 1 de Add game. No es una taxonomía: ordenadas junto a las de sobremesa por año, la Game Gear
caía entre la Genesis y la SNES y la 3DS quedaba sola inaugurando una 8ª generación de una consola.

**Atari 2600: una sola región, y eso es un hueco, no un dato.** La consola que convirtió el
cartucho en el estándar (1977) abre el dataset por abajo: es la única de **2ª generación**.

| Catálogo | Juegos | año/editora/género/cover |
|---|---|---|
| `atari-2600-usa.json` | 471 | 93/99/**92**/96 |

**El género al 92% es la excepción del dataset.** En siete consolas ese campo está en 0% porque
libretro no publica `metadat/genre` para ellas. Para la 2600 lo publica —703 entradas— *y además*
Wikipedia lo trae en columna, así que sale de las dos fuentes en merge.

**Tiene builder propio** (`build_atari2600_catalog.py`) porque su página **no tiene eje de región**:
donde las demás listas traen un bloque `colspan` con Japón / Norteamérica / Europa y una fecha por
mercado, esta trae una sola columna `Year`. El `build()` genérico se ancla justo a ese bloque. Son
además dos tablas con columnas distintas: de la de Atari y Sears sale el **serial** (el número CX
impreso en el cartucho, 10%) y de la de terceros el **género**.

**Solo NTSC-U, y hay que decirlo como lo que es.** La 2600 sí tuvo mercado PAL —libretro tiene 139
carátulas europeas, y 25 japonesas de la **Atari 2800**, que es como se vendió allá— pero esa página
no distingue mercados y no hay de dónde sacar esas listas. Es **distinto** de la SG-1000 (que solo
existió en Japón) y de la TurboGrafx (que nunca se vendió en Europa): ahí la región única es la
verdad; acá es lo que tenemos. Si aparece una fuente regional, el modelo ya la admite sin cambios.

**De las seis tablas de la página entran dos.** Quedan afuera `Homebrew games` y `Prototypes` (no se
vendieron en su época), `Official aftermarket releases` y `Multi-game cartridges` (reediciones de los
2020 para la Atari 2600+), `Xonox double-sided cartridges` (cartuchos 2-en-1 cuyos juegos, dice la
propia sección, "were also available individually": ya están en la tabla de terceros, entrarían
duplicados bajo títulos compuestos) y `Non-game cartridges` (`Color Bar Generator`, `Venetian
Blinds`).

Ojo con el corte de secciones: las cuatro primeras que se excluyen son de **nivel 3** y cuelgan de
las dos que sí queremos, así que hay que cortar en el próximo encabezado **de cualquier nivel**.
Cortando en el próximo nivel 2 se colaban enteras: la primera versión traía 581 juegos con
multicarts y homebrew de AtariAge adentro, con la cobertura hundida (carátula 81% contra 96%).

**NEC: dos regiones, y es lo correcto.** PC Engine (Japón, 1987) y TurboGrafx-16 (Norteamérica,
1989) son la misma consola con dos nombres, más su periférico de CD —el **primer CD de consola de la
historia**—. Se arman de una sola página de Wikipedia (`List of TurboGrafx-16 games`), que mezcla en
**una tabla** el cartucho y el CD y los distingue por una columna `Format`: de ahí salen los cuatro
catálogos, filtrando por formato con `--format-cell` / `--format`.

| Catálogo | Juegos | año/editora/cover |
|---|---|---|
| `turbografx-16-usa.json` | 93 | 100/100/95 |
| `turbografx-16-jp.json` | 288 | 100/100/65 |
| `turbografx-cd-usa.json` | 43 | 100/100/90 |
| `turbografx-cd-jp.json` | 395 | 100/100/51 |

**El emparejamiento de carátulas por región, corregido (2026-08-10).** Preguntando por qué *R-Type*
mostraba la tapa americana en el catálogo japonés salieron **tres defectos que afectaban a todo el
dataset**, no solo a NEC:

1. **La región podía estar en el segundo paréntesis.** `_regiones()` leía solo el primero, así que
   para `Splash Lake (NEC Avenue) (Japan)` la región era «NEC AVENUE», no se reconocía como japonesa
   y perdía contra `Splash Lake (USA)`. Ahora se leen todos.
2. **El japonés casi siempre lleva subtítulo y el americano no.** `Dead Moon` vs `Dead Moon - Getsu
   Sekai no Akumu (Japan)`: el calce exacto solo encontraba el archivo `(USA)`. Se agregó un índice
   por la parte anterior al `" - "`, más el caso inverso (el catálogo dice `Ys III: Wanderers from
   Ys` y el archivo japonés es `Ys III (Japan)`), y ambos exigen que el archivo **empiece
   literalmente** por el título — sin eso `Ys I & II` y `Ys III` colapsan a la misma clave y se
   roban la tapa entre ellos.
3. **A igual región ganaba la variante, no el lanzamiento base.** `(Rev 1)` ordena antes que el
   nombre pelado, y las variantes son justo las que libretro guarda como **symlink** —que
   `raw.githubusercontent` sirve como texto y la app no puede decodificar—. Se agregó desempate por
   nombre más corto: reintroducía los symlinks que `fix_covers_symlink.py` ya había resuelto.

Resultado sobre los 47 catálogos: **367 carátulas pasaron a la región correcta**, 1801 pasaron de
una variante al nombre canónico y la cobertura total subió a **25.246/31.199 (80%)**. El contador de
`catalog_lint` bajó de 1999 a **1660** carátulas de otra región y la línea base se rebajó a ese valor.

Los que **no** tienen arreglo automático viven en `tools/overrides/`: *R-Type* japonés (en Japón se
partió en dos HuCards, `R-Type I` y `R-Type II`, no existe un `R-Type (Japan)`), *Gate of Thunder*
americano (solo se vendió en el CD triple del Duo), *SideArms*, *J.B. Harold* y *Sherlock Holmes
Vol. II*. *The Davis Cup Tennis* se queda con la tapa americana: libretro no tiene la japonesa.

**Sin PAL, a propósito.** NEC nunca lanzó la consola en Europa: las máquinas que se vendieron ahí
eran importaciones japonesas de mercado gris, modificadas para el mercado local. La página de juegos
no menciona Europa en ninguna fila, y el bloque de regiones de su tabla tiene **dos** columnas, no
tres —por eso `region_base`/`region_column` aceptan `colspan="2"` además de `colspan="3"`—. Inventar
un `turbografx-16-eu.json` sería fabricar un catálogo que nunca existió.

**Serial en 0%.** Ni No-Intro ni Redump publican el catalog number de HuCard/CD-ROM² en un formato
que podamos cruzar por título, así que las cuatro listas quedan sin serial. Es un hueco conocido,
no un error de parseo.

**Dos defectos de parseo que aparecieron acá y afectaban a todo el dataset:**

- **Editora por mercado.** La celda trae las dos juntas: `[[NEC]] (US)<br>[[Hudson Soft]] (JP)`.
  Quedarse con la primera le ponía la editora **americana al catálogo japonés**, con el `(US)`
  pegado — el japonés repetía las mismas 57 «NEC (US)» del americano. Lo resuelve
  `regional_publisher()`, hermano de `regional_title()` pero con el mercado entre paréntesis en vez
  de en un `<sup>`. *Aero Blasters* ahora dice **NEC** en el americano y **Hudson Soft** en el japonés.
- **`<br>` que no separa nombres.** A veces solo parte un título largo en dos renglones y el primer
  trozo queda colgando de su conector: `''Adventure Quiz: Capcom World /''<br/>''Hatena no
  Daibōken''`. Se detecta por el conector suelto al final y se vuelve a unir. Buscando esto apareció
  además que la lista de PS2 escribe el tag **partido por un salto de línea** (`<br /\n>`), que el
  separador no reconocía: por eso `ps2-eu` tenía dos entradas literales `Club Football<br /`.

**PlayStation: las tres regiones.** A diferencia de Sega —que reparte Europa entre UK, Alemania,
Francia, España, Australia y Brasil— Sony distribuyó PAL de forma unificada, con un único prefijo de
serial (`SCES`/`SLES`), así que Wikipedia lo lista en **una sola columna** y con un catálogo alcanza.

| Catálogo | Juegos | fecha | editora | serial | cover |
|---|---|---|---|---|---|
| `psx-usa.json` | 1344 | 91% (1155 al día) | 100% | 87% | 87% |
| `psx-jp.json` | 3148 | **100%** (3145 al día) | 98% | 58% | 67% |
| `psx-eu.json` | 1286 | **100%** (789 al día) | 99% | 76% | 85% |

Los regionales salen enteros de la tabla de Wikipedia (`build_catalog_from_wikipedia.py`): un juego
entra si su columna tiene **fecha**, porque la tabla marca `{{unreleased}}` donde no salió — la fecha
es a la vez el dato y la prueba de que se publicó ahí. Por eso llegan al 100%.

**GameCube.** Mismo patrón que PlayStation, y también con Europa como zona única. Su tabla ordena
las columnas `Japan | North America | PAL` —distinto de PlayStation, que pone Europa en el medio—,
otra razón para leer la cabecera en vez de fijar el índice.

| Catálogo | Juegos | fecha | editora | serial | cover |
|---|---|---|---|---|---|
| `gamecube-usa.json` | 537 | 100% (536 al día) | 100% | 83% | 82% |
| `gamecube-jp.json` | 274 | 100% (274 al día) | 100% | 42% | 74% |
| `gamecube-eu.json` | 433 | 100% (432 al día) | 100% | 81% | 81% |

**PlayStation 2: la tabla no trae fecha por región.** Es la única lista con otro layout, y por eso
`build_catalog_from_wikipedia.py` tiene un `--layout checkmarks`. En vez de tres columnas de fecha,
pone **una sola** —la del primer lanzamiento, etiquetada con su mercado (`2005-11-23{{sup|JP}}`)— y
tres columnas que son apenas una tilde de "salió acá".

De ahí venía el hueco de fechas: la fecha de la tabla se copia **solo si su etiqueta coincide con la
región del catálogo**. Un juego que debutó en Japón en 2003 y llegó a Europa en 2005 tiene una única
fecha, la japonesa; ponérsela al europeo le inventaría dos años de antigüedad. Con eso solo, `ps2-eu`
quedaba en **28%** —casi nada debutó en Europa—, y como la app ordena por lanzamiento, tres de cada
cuatro juegos caían al fondo de la estantería.

Las que faltaban salen de la **ficha del artículo de cada juego**
(`enrich_dates_wikipedia_infobox.py`), que sí trae las tres regiones. El artículo se saca del enlace
de la propia tabla, no adivinando la URL: hay desambiguaciones (`Killzone (video game)`) que no se
derivan del título.

| Catálogo | Juegos | fecha | editora | serial | cover |
|---|---|---|---|---|---|
| `ps2-usa.json` | 1815 | 91% (1652 al día) | 100% | 90% | 90% |
| `ps2-jp.json` | 2975 | 85% (2550 al día) | 100% | 51% | 57% |
| `ps2-eu.json` | 2226 | 72% (1593 al día) | 100% | 80% | 88% |

Otra particularidad de esa tabla: las filas **omiten las celdas vacías del final**, así que un
exclusivo europeo escribe seis celdas y no siete. Exigir un largo fijo descartaba justamente a los
exclusivos de EU y JP.

**PlayStation 3: carátulas por SteamGridDB.** El catálogo sale de la misma tabla por región que
PlayStation y GameCube, pero su lista **no tiene columna de editora** (va `Title | Developer |
regiones | Options | Ref`), así que las tres regiones salían con 0%. Las completa
`enrich_infobox_wikipedia.py` desde la ficha de cada artículo, que además distingue por mercado:
*Demon's Souls* lo publicó Sony en Japón, **Atlus** en América y **Namco Bandai Partners** en Europa.
Guarda el nombre de la época —*The Last of Us* queda como *Sony Computer Entertainment*, no
*Interactive*—, que es el que corresponde a un catálogo retro.

El problema real fueron las tapas: libretro-thumbnails publica **67 carátulas de PS3**, contra 8.503
de PS2 y 9.351 de PlayStation, así que el enriquecedor habitual dejaba el catálogo en 2%. Se resuelve
con `enrich_covers_steamgriddb.py`, la misma fuente que la app ya consulta en vivo para PS5.

Ese script **solo acepta coincidencia exacta de título**. Tomar el primer resultado del buscador daba
un 97% de "aciertos" que incluía tapas de otro juego: *Goosebumps: The Game* traía la de *Attack of
the Mutant*, y *Saint Seiya: Brave Soldiers* la de *Soldiers' Soul*, que es su secuela. En un
catálogo de colección una tapa equivocada es peor que ninguna, porque se ve legítima.

| Catálogo | Juegos | fecha | editora | serial | cover |
|---|---|---|---|---|---|
| `ps3-usa.json` | 1894 | **100%** (1893 al día) | 80% | 50% | 78% |
| `ps3-jp.json` | 1182 | **100%** (1181 al día) | 71% | 43% | 67% |
| `ps3-eu.json` | 1836 | **100%** (1836 al día) | 79% | 50% | 77% |

El género queda vacío en las cuatro consolas de Sony y en GameCube: libretro publica `metadat/genre/`
y `metadat/releaseyear/` **solo de PSP** entre las plataformas de PlayStation, y de GameCube no
publica ninguno. No es un bug del enriquecedor, es que la fuente no lo cubre.

**No-Sega (pendientes de una fuente con procedencia — ver roadmap E):**

| Catálogo | Builder | Fuente títulos | Serial | año/ed/serial/cover |
|---|---|---|---|---|
| `nes-usa.json` | `build_nes_catalog.py` | Wikipedia NES (col 5) | libretro DAT | 100/100/93/95 |
| `snes-usa.json` | `build_snes_catalog.py` | Wikipedia SNES (columna NA) | libretro DAT + **SNES Central** | 100/100/96/91 |
| `n64-usa.json` | `build_n64_catalog.py` | Wikipedia N64 (col 5) | libretro DAT | 100/100/96/100 |

Repos de carátula (libretro-thumbnails): NES `Nintendo_-_Nintendo_Entertainment_System`, SNES
`Nintendo_-_Super_Nintendo_Entertainment_System`, N64 `Nintendo_-_Nintendo_64`, PSX
`Sony_-_PlayStation`, Genesis `Sega_-_Mega_Drive_-_Genesis`, Master System
`Sega_-_Master_System_-_Mark_III`, Game Gear `Sega_-_Game_Gear`, Sega CD
`Sega_-_Mega-CD_-_Sega_CD`, 32X `Sega_-_32X`, Saturn `Sega_-_Saturn`, Dreamcast `Sega_-_Dreamcast`,
SG-1000 `Sega_-_SG-1000`, GameCube `Nintendo_-_GameCube`, PS2 `Sony_-_PlayStation_2`. PS3 no usa
libretro (solo tiene 67 tapas): va por SteamGridDB.

## Sega Retro — la fuente insignia (y el patrón por consola)

**Genesis es el catálogo modelo** del dataset con procedencia. Sus fechas (con mes, a veces día),
seriales (catalog number) y ratings salen de **[Sega Retro](https://segaretro.org)**, la fuente más
certera para las consolas Sega.

**Recolección: por API, automática.** Sega Retro corre MediaWiki con la extensión **Cargo**, o sea
que expone los datos *estructurados* (`action=cargoquery`). Lo que antes obligaba a bajar el HTML a
mano era el rechazo al User-Agent por defecto; con un UA descriptivo la API responde normal. El
recolector vive en `tools/local/segaretro_api.py` (**fuera del repo**, ver `tools/local/README.md`):

```bash
python3 tools/local/segaretro_api.py fetch --console MD --region JP \
    --out tools/sources/genesis-jp-segaretro.json
```

Reproduce la **lista curada** de la wiki replicando el filtro de `Template:GameList` (categoría
`<REGIÓN> <Consola> games` menos accesorios/hardware/aftermarket/download-only) y cruza `releases`
(fecha ISO, catalog number, rating) con `localisednames` (título de la región). Contrastado contra
las fuentes hechas a mano: **434/434** fechas y seriales idénticos en Mega Drive JP, **696/696** en
Mega Drive US, **102/102** en Master System US — y además cubre más títulos.

Luego se aplica con:

- `tools/enrich_from_segaretro.py <catalogo> <fuente>` — aplica la fuente a un catálogo existente
  (match por título; rellena `releaseDate`/`serial`/`rating`, alinea `year`). Ya aplicado a
  **Genesis**, **Master System** (cartuchos + Sega Cards) y **Dreamcast**.
- `tools/build_catalog_from_segaretro.py` — construye un catálogo regional nuevo desde cero.

El parser viejo de HTML (`tools/segaretro_source.py`) queda como respaldo para material que no esté
en las tablas Cargo.

Este es **el patrón a replicar: cada consola con su mejor fuente**, siempre trazada — Sega Retro para
las Sega, una fuente japonesa para los catálogos JP, un foro/base de datos confiable donde Wikipedia no
alcance (p. ej. PSX-US). El norte: fullset dice **de dónde sacó cada dato**.

## De dónde mejoramos — la capa de overrides

`tools/overrides/<catálogo>.json` = `{slug: {campo: valor, …}}`. Se aplican **al final** de la
generación y **pisan** el valor auto-derivado, así las correcciones a mano sobreviven un rebuild desde
cero. Es la vía canónica para mejorar la calidad sin perderla cuando se regenera.

**Procedencia obligatoria:** cada corrección declara de dónde salió, con claves de guion bajo que
`apply_overrides` **no copia al catálogo** (romperían el esquema de 11 campos). `catalog_lint.py`
exige `_source` y rechaza el archivo sin él, así que una corrección sin fuente no entra:

```json
{
  "disneys-aladdin": {
    "coverUrl": "https://…/Aladdin%20%28USA%29%20%28Final%20Cut%29.png",
    "_source": "libretro-thumbnails (boxart bajo 'Aladdin (USA) (Final Cut)')",
    "_note": "el catálogo lo tenía vacío por desajuste de nombre",
    "_date": "2026-07-29"
  }
}
```

Así, cuando dentro de un año revisemos por qué un dato es lo que es, la respuesta está al lado del dato.

El lint valida además que el slug exista en su catálogo, que los campos corregidos sean del esquema y
que `_date` sea `AAAA-MM-DD`. Y `catalog_report.py` cierra con cuántas correcciones hay por catálogo
y sobre qué campos, para poder leer de un vistazo **qué parte del dataset está confirmada a mano y
qué parte es scrape sin revisar** — en el JSON las dos se ven igual, y no lo son.

**Caso real (2026-07-30):** `snes-usa.json` traía doce catalog number de **otra región** — `SNSP-`
(Europa) y `SHVC-` (Japón) — porque su generador legacy tomaba de libretro la fila equivocada. En una
lista NTSC-U eso no identifica la copia que tenés, así que se verificó cada uno contra la entrada
`(USA)` de libretro-database: seis se corrigieron y **seis quedaron vacíos** porque libretro no tiene
la entrada estadounidense. Un dato equivocado es peor que ninguno. Ver `tools/overrides/snes-usa.json`,
donde cada corrección dice de dónde salió.

Los que libretro no cubría se cerraron con **[SNES Central](https://snescentral.com)** (Evan G.), que
publica la etiqueta del cartucho por región. Se consultaron **solo las fichas de los títulos que
faltaban**, respetando el `Crawl-delay: 10` de su robots.txt — no se replicó el sitio ni se copiaron
sus textos o escaneos: lo que se toma es el código impreso en el cartucho, un hecho, y se integra a
nuestra lista con la fuente anotada juego por juego. La cobertura de serial pasó de 93% a **98%**.

Eso además destapó **cinco pares de juegos que compartían catalog number** por el match difuso del
generador legacy (Brawl Brothers con Rival Turf!, The Lost Vikings con su secuela, Star Fox con el
cartucho de competencia Super Weekend…). Resueltos contra la ficha de cada uno.

## Región (multi-región: implementado)

**Modelo:** un **archivo de catálogo por (plataforma × región)**, linkeado por `slug`. `platforms.json`
mapea la consola a sus catálogos: `"catalogs": { "NTSC-U": "…usa.json", "NTSC-J": "…jp.json" }`
(o el legacy `"catalog": "…"` = NTSC-U). `Platform.catalogFor(region)` elige el archivo, con **fallback
a NTSC-U** cuando esa región no tiene lista. `GameCatalog` carga/cachea por archivo; `RegionFilter`
(Settings) elige la región activa y la app muestra ese catálogo en Add-game y el timeline.

**Estado:** **todas las consolas Sega tienen las tres regiones** (la SG-1000 solo NTSC-J: nunca salió
de Japón — en Europa su lugar lo ocupó el Master System). Las no-Sega (NES, SNES, N64, PSX) siguen
solo con NTSC-U y caen a esa lista en las otras regiones. **TurboGrafx-16 y TurboGrafx-CD tienen dos**
(NTSC-J y NTSC-U) y **no llevan PAL a propósito**: NEC nunca la lanzó en Europa, donde las máquinas
que se vendieron eran importaciones japonesas de mercado gris modificadas para el mercado local. El
selector de región solo ofrece las regiones **declaradas**, así que ahí muestra dos opciones, no tres. `catalogFor` cae a cualquier catálogo
disponible cuando la región pedida no tiene lista ni hay default, para que una consola de una sola
región no se vea vacía.

**PAL = unión de territorios, no un país.** Sega Retro separa Europa por país, pero cada país es
casi siempre la distribución local del lanzamiento paneuropeo (95%+ de cada uno cae dentro de `EU`):
un archivo por país sería el mismo listado repetido doce veces. Así que PAL se arma como la **unión**
de `EU` + los países europeos + Australia + Brasil. `EU` aporta el grueso y cada país sus exclusivos:
Australia 55 y sobre todo **Brasil 77**, los Tec Toy en portugués que no salieron en ningún otro lado.
Brasil es técnicamente PAL-M (60 Hz), pero va acá porque sus juegos no existen en ninguna otra lista.

**Dirección — datos por país, selector por región.** El `catalogs` map tiene **key de texto libre**, así
que soporta granularidad de **país** sin cambiar el modelo (Sega Retro separa Europa en UK/Francia/
Alemania/España/Australia/Brasil, cada uno con otra fecha/serial). El plan: los **datos** por país
(`genesis-uk.json`, `genesis-germany.json`…) y el **selector** por región agrupando países (PAL → [UK,
Francia…]), con drill-down a país como refinamiento. Japón es 1:1 con NTSC-J, por eso entró directo.

## Precisión de fechas y confianza (dirección)

Dos evoluciones de esquema que habilitan la "mejora en el tiempo" (a confirmar antes de implementar):

1. **Precisión de fecha:** poder pasar de `year: 1993` → `1993-09` → `1993-09-15`, por región, con
   fuente. Ej.: una revista/base de datos confirma la fecha NA exacta → el dato mejora y queda
   registrado de dónde. (Opción backward-compatible: mantener `year` para la app y agregar un
   `releaseDate` ISO de precisión variable + procedencia.)
2. **Confianza / confirmación:** distinguir lo **auto-derivado** (scrape) de lo **confirmado** (revisado
   contra una fuente), por campo. Permite reportar "cuánto tenemos confirmado" por catálogo y región.

Ambas son **aditivas**: la app usa kotlinx.serialization con `ignoreUnknownKeys`, así que agregar
claves nuevas no rompe nada.

## Roadmap (cimientos → calidad)

- **A. Documentar procedencia** — *este documento*. ✅
- **B. Registro machine-readable** — ✅ **el manifest** (`data/catalogs/manifest.json`, ver arriba):
  por archivo lleva plataforma, cantidad de juegos, tamaño, cobertura por campo y **sha256**, más un
  `schema` y una `version` derivada del contenido. Lo genera `catalog_manifest.py` y **el lint lo
  verifica en cada corrida**, diciendo qué archivo cambió. Lo que todavía no está —y es lo que
  quedaba de la idea original— es la **fuente por campo y el estado de revisión** por catálogo: eso
  hoy vive en la tabla de procedencia de este documento, no en el dato.
- **C. Evolución de esquema** — **a medias**: la precisión de fecha está ✅ (`releaseDate` ISO
  variable: 94% de los juegos la tienen, y de esos el 82% con día exacto), la **confianza por dato no** — hoy no hay forma de distinguir en el
  JSON un valor scrapeado de uno verificado, salvo mirando si tiene override.
- **D. Pipeline de corrección con procedencia** — ✅ overrides con `_source`/`_note`/`_date`;
  `catalog_lint.py` **exige** `_source` y valida slug, campos y formato de fecha; `catalog_report.py`
  separa confirmado a mano de auto-derivado. Hoy son **74 correcciones**, todas con fuente citada.
- **E. Cerrar huecos** — **Sega cerrado** ✅ (8 consolas × 3 regiones) y **GameCube, PS2 y PS3
  cerrados** ✅. La editora de los catálogos Sega ya no está en 0%: va de 77% (Saturn, Sega CD) a 89%
  (SG-1000), y **`snes-usa` ya tiene builder** (`build_snes_catalog.py`), que era el último catálogo
  que no se podía regenerar ni auditar. Lo que queda abierto es el **género en 0%** en 7
  consolas —PlayStation, PS2, PS3, GameCube, Dreamcast, Saturn y Sega CD—: 22.526 juegos, el 83%
  del dataset. No es el enriquecedor, es la fuente: libretro publica `metadat/genre` solo de PSP
  entre las PlayStation, y nada de las consolas de disco de Sega.
- **G. Vocabularios controlados** — **pendiente, medido el 2026-08-10.** El lint garantiza la
  **forma** (11 claves, orden, tipos, slug único, ordenado) y cuatro invariantes de significado,
  pero **no mira el vocabulario**: atrapa un serial japonés en un catálogo americano y no atrapa
  `Ubi Soft` conviviendo con `Ubisoft`. Auditoría sobre las 31.670 entradas:

  **Editoras — 1.726 formas distintas para 28.473 juegos, con 60 grupos que son la misma escrita
  de dos maneras.** Los de más peso: `Ubi Soft` (144) / `Ubisoft` (381) = 525 juegos partidos al
  medio; `D3 Publisher` (362) / `D3Publisher` (50); `Square Enix` (152) / `Square-Enix` (3);
  `From Software` (1) / `FromSoftware` (65); `Sammy` (62) / `Sammy Corporation` (8); `Namco JP` /
  `NamcoJP`; `Capcom JP/NA` / `CapcomJP/NA`. La normalización que las agrupa es simple —minúsculas,
  sacar `Inc/Ltd/Co/Corp/GmbH`, sacar puntuación— así que el canon se puede derivar y revisar a
  mano una sola vez.

  Aparte, **44 valores (0,15%) no parecen editoras**: 39 son editoras multi-región pegadas sin
  separador (`MajescoNAHudson SoftJPVivendi Universal Game`), que es el caso que
  [regional_publisher] resuelve cuando el marcador va entre paréntesis pero no cuando va en
  `<sup>NA</sup>` sin `<br>`; 4 son texto de nota (`Original Version`) y 1 es un número (`9003`).

  **Géneros — 60 valores para 4.796 juegos, de tres vocabularios que conviven.** Hay diferencias de
  sola mayúscula (`Role-Playing (RPG)` / `Role-playing (RPG)`, `Shooter` / `shooter`), cuatro formas
  de decir lo mismo (`Shooter`, `Shoot'em Up`, `Fixed shooter`, `Scrolling shoot'em up`) y valores
  compuestos (`Action, Simulation, Sports`) mezclados con simples. El género existe en **17 de 48**
  catálogos; el rating, en 19 de 48.

  El orden natural del trabajo es **editoras primero** —son 60 grupos, acotado y verificable— y que
  el lint pase a rechazar formas fuera del canon, que es lo que impide que vuelva a abrirse.

- **F. Eje de región** — ✅ **cerrado**. Las 14 consolas con catálogo multirregional tienen las tres:
  Sega (8), PlayStation, PS2, PS3, GameCube y, desde los seis catálogos nuevos, NES, Super Nintendo
  y Nintendo 64. La SG-1000 queda con una sola a propósito: salió solo en Japón. Son 30.380 juegos.

  El modelo ya soporta el drill-down por país dentro de PAL (la key del mapa `catalogs` es texto
  libre); eso sí no compensa hoy, porque los países son 95% el mismo listado.

**El título depende de la región.** Las listas escriben `Título<br />•Alternativo<sup>NA</sup>`, y ese
`<sup>` dice **en qué mercado se usó el otro nombre**. En un catálogo NTSC-U eso decide el título:
*Final Fantasy VI* se vendió como **Final Fantasy III**, *The Chaos Engine* como **Soldiers of
Fortune** y *Another World* como **Out of This World**. Quedarse siempre con el principal no solo
ponía el nombre equivocado — además **duplicaba**, porque el catálogo ya tenía el juego bajo su
nombre americano y los dos no se reconocían entre sí.

## Herramientas

Los catálogos viven en **`data/catalogs/`**, en la raíz del repo — no dentro del módulo de Android,
porque los consumen las dos plataformas (Android los suma vía `sourceSets`, iOS los referencia como
carpeta en `project.yml`).

**Inspección** (solo lectura, no tocan nada):

- `catalog_lint.py` — valida la forma canónica de los 37 catálogos y que cada override cite su
  fuente. Devuelve 1 si algo falla, así sirve de gate.
- `catalog_report.py` — cobertura por campo y por catálogo, y cuánto está confirmado a mano.

**Construcción** — uno por consola, salvo el genérico:

- `build_catalog_from_wikipedia.py` — el genérico, por región. Dos formatos de tabla: `regions-dated`
  (una fecha por región: PlayStation, GameCube, PS3) y `--layout checkmarks` (una sola fecha de
  primer lanzamiento + tildes: PS2).
- `build_snes_catalog.py`, `build_nes_catalog.py`, `build_n64_catalog.py`,
  `build_dreamcast_catalog.py`, `build_mastersystem_catalog.py`, `build_psx_catalog.py`.
- `build_catalog_from_segaretro.py` + `segaretro_source.py` — desde las capturas de `tools/sources/`.
- `catalog_common.py` — el motor compartido: `clean_cell`, `core`, `slug`, `write_catalog` y la
  aplicación de overrides.

**Enriquecimiento** — todos completan **solo lo vacío**, salvo donde se pida `--overwrite`:

| Herramienta | Campo | Fuente |
|---|---|---|
| `enrich_dates_wikipedia.py` | `releaseDate` | la columna de región de la lista |
| `enrich_infobox_wikipedia.py` | `releaseDate`, `publisher` | la ficha del artículo de cada juego |
| `enrich_serials_redump.py` | `serial` | DAT de Redump (consolas de disco) |
| `enrich_meta_libretro.py` | `publisher`, `genre`, `releaseDate` | libretro-database |
| `enrich_covers_libretro.py` | `coverUrl` | libretro-thumbnails |
| `enrich_covers_steamgriddb.py` | `coverUrl` | SteamGridDB (PS3, que libretro no cubre) |
| `enrich_from_segaretro.py` | fechas, serial, rating | Sega Retro |

`normalize_catalogs.py` rehornea los catálogos legacy. **Pisa los catálogos**: correr solo con
confirmación.

> **Nota:** los catálogos son documentación curada a mano. No se regeneran a la ligera (el script los
> pisa). Las mejoras van por override (con procedencia) o edición puntual documentada.

## El manifest

`data/catalogs/manifest.json` es el índice del dataset: qué archivos lo componen, cuánto pesan, qué
contienen y **el sha256 de cada uno**. Lo arma `catalog_manifest.py`, que solo lee y escribe el
manifest — no toca los catálogos, así que se puede correr sin miedo:

```
python3 tools/catalog_manifest.py           # lo reescribe
python3 tools/catalog_manifest.py --check   # falla si quedó desfasado
```

El lint lo verifica en cada corrida y dice **qué archivo** cambió, no solo que algo cambió.

Tres decisiones que no se ven en el archivo:

- **`schema`** describe la *forma* del dato, no su contenido. Existe para que un cliente que
  entienda hasta el esquema N pueda **negarse** a leer un manifest N+1 en vez de intentarlo y
  romperse. Hoy es 1 y solo sube si un cliente viejo dejara de poder leer el dato nuevo — cosa que
  agregar o quitar campos *no* provoca: el parser de la app ignora claves desconocidas y todos los
  campos del catálogo tienen valor por defecto.
- **`version`** sale del hash de los hashes, no de un timestamp. Correr el generador dos veces sin
  tocar nada no ensucia el repo, y el diff sigue reflejando avance real.
- **`platforms.json` está en el manifest** aunque no sea un catálogo. Lleva los conteos por
  consola, que son una copia del tamaño de cada catálogo: si viajaran por separado, un día los
  catálogos cambiarían y los conteos no, y la app mostraría un número que no corresponde.
