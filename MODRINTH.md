# LightSetPlugin

Paper plugin that adds spotlights, lamps, light strips and light areas to your server. The light is made of invisible `minecraft:light` blocks that follow the shape of the scene, so no resource pack is needed.

## Features

- Four source types: `spot` (cone of rays that lights the surfaces it hits), `lamp` (one point), `strip` (line between two points), `area` (box filled with a grid of light).
- Brightness `0..15`, switching on and off, and effects: `flicker`, `pulse`, `blink`.
- Groups: control many sources with one command.
- Optional particle beam for spotlights and a visible model (item display) for every source.
- Several sources can light the same block; the brightest one wins.
- Light blocks left behind after a crash or restart are found and removed automatically.
- Work is spread over ticks and limited by configurable budgets; chunks are never loaded.
- Messages in English and Russian.

## Requirements

- Paper 1.21.11, Java 21
- No other plugins are needed.

## Installation

1. Put `LightSetPlugin-<version>.jar` into the `plugins` folder.
2. Start the server. `plugins/LightSetPlugin/config.yml` is created.
3. Stand where the light should be and run `/lightset create stage1 spot`.

Version 2.0.0 does not read data or configuration of 1.x (the old `plugins/LightSet` folder). Recreate your sources and settings.

## Commands

Names may contain Latin letters, digits, `_` and `-` (up to 32 characters).

| Command | Permission | Description |
|---|---|---|
| `/lightset` | any LightSet permission | Show the command list |
| `/lightset create <name> <spot\|lamp\|strip\|area>` | `lightset.edit` | Create a source at your position, facing your direction |
| `/lightset remove <name>` | `lightset.edit` | Remove a source and its light blocks |
| `/lightset list` | `lightset.view` | List all sources |
| `/lightset info <name>` | `lightset.view` | Show details of a source |
| `/lightset tp <name>` | `lightset.view` | Teleport to a source |
| `/lightset on <name>`, `off <name>`, `toggle <name>` | `lightset.control` | Switch a source |
| `/lightset brightness <name> <0-15>` | `lightset.control` | Set the brightness |
| `/lightset effect <name> <none\|flicker\|pulse\|blink>` | `lightset.control` | Set the effect |
| `/lightset aim <name> <yaw> <pitch>` | `lightset.edit` | Aim a spotlight by angles |
| `/lightset aimhere <name>` | `lightset.edit` | Aim a spotlight where you look |
| `/lightset lookat <name> <x y z>` | `lightset.edit` | Aim a spotlight at a point |
| `/lightset rotate <name> <dYaw> <dPitch>` | `lightset.edit` | Turn a spotlight by an offset |
| `/lightset move <name> [x y z]` | `lightset.edit` | Move a source (to you if no position is given; same world only) |
| `/lightset pos2 <name> [x y z]` | `lightset.edit` | Set the second point of a strip or area |
| `/lightset angle <name> <1-179>` | `lightset.edit` | Cone angle of a spotlight, degrees |
| `/lightset range <name> <blocks>` | `lightset.edit` | Range of a spotlight |
| `/lightset rays <name> <count>` | `lightset.edit` | Rays of a spotlight |
| `/lightset beam <name> <on\|off>` | `lightset.edit` | Show or hide the particle beam |
| `/lightset group set <name> <group>` | `lightset.edit` | Put a source into a group |
| `/lightset group clear <name>` | `lightset.edit` | Take a source out of its group |
| `/lightset group on\|off\|toggle <group>` | `lightset.control` | Switch every source of a group |
| `/lightset group brightness <group> <0-15>` | `lightset.control` | Set the brightness of a group |
| `/lightset reload` | `lightset.reload` | Reload `config.yml` |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `lightset.view` | op | List, inspect and teleport to sources |
| `lightset.control` | op | Switch, dim and animate sources and groups |
| `lightset.edit` | op | Create, remove and reconfigure sources |
| `lightset.reload` | op | Reload the configuration |
| `lightset.admin` | op | All of the above |

## Configuration

File: `plugins/LightSetPlugin/config.yml`. An invalid value is reported in the console and replaced by its default.

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `settings.language` | string | `auto` | `ru`, `en`, `auto` | Message language; `auto` follows the player's client, the console is English |
| `limits.max-range-blocks` | int | `320` | `1..2048` | Maximum spotlight range |
| `limits.max-angle-degrees` | number | `160.0` | `1.0..179.0` | Maximum full cone angle |
| `limits.max-rays` | int | `400` | `1..5000` | Maximum rays per spotlight |
| `limits.max-blocks-per-source` | int | `1024` | `1..100000` | Maximum light blocks per source |
| `limits.max-total-blocks` | int | `30000` | `1..10000000` | Maximum light blocks on the server |
| `defaults.brightness` | int | `15` | `0..15` | Brightness of new sources |
| `defaults.angle-degrees` | number | `40.0` | `1.0..limits.max-angle-degrees` | Cone angle of new spotlights |
| `defaults.range-blocks` | int | `250` | `1..limits.max-range-blocks` | Range of new spotlights |
| `defaults.rays` | int | `120` | `1..limits.max-rays` | Rays of new spotlights |
| `spot.distance-falloff` | number | `0.5` | `0.0..1.0` | Fade at the maximum range |
| `spot.edge-falloff` | number | `0.5` | `0.0..1.0` | Fade at the cone edge |
| `shapes.strip-spacing-blocks` | int | `4` | `1..64` | Distance between light blocks along a strip |
| `shapes.area-spacing-blocks` | int | `6` | `1..64` | Distance between light blocks inside an area |
| `performance.source-updates-per-tick` | int | `1` | `1..100` | Spotlights recalculated per tick |
| `performance.block-updates-per-tick` | int | `300` | `1..100000` | Light blocks placed or removed per tick |
| `effects.interval-ticks` | int | `4` | `1..100` | How often effects are evaluated |
| `effects.flicker.min` | number | `0.4` | `0.0..1.0` | Lowest brightness multiplier of flicker |
| `effects.flicker.change-chance` | number | `0.5` | `0.0..1.0` | Chance of a new brightness on each evaluation |
| `effects.pulse.period-ticks` | int | `60` | `4..100000` | Length of one pulse |
| `effects.pulse.min` | number | `0.2` | `0.0..1.0` | Lowest brightness multiplier of pulse |
| `effects.blink.on-ticks` | int | `20` | `1..100000` | Time the light stays on |
| `effects.blink.off-ticks` | int | `20` | `1..100000` | Time the light stays off |
| `beam.enabled-by-default` | boolean | `true` | `true`, `false` | New spotlights show the beam |
| `beam.interval-ticks` | int | `5` | `1..200` | Beam redraw interval |
| `beam.view-distance-blocks` | number | `64.0` | `4.0..512.0` | Players farther away do not see the beam |
| `beam.step-blocks` | number | `3.0` | `0.5..32.0` | Distance between beam particles |
| `beam.color` | string | `#FFF4C2` | `#RRGGBB` | Beam colour |
| `beam.size` | number | `1.0` | `0.1..4.0` | Beam particle size |
| `model.enabled` | boolean | `true` | `true`, `false` | Spawn a visible model for every source |
| `model.scale` | number | `1.0` | `0.05..16.0` | Model scale |
| `model.view-range-multiplier` | number | `2.0` | `0.1..16.0` | Render distance multiplier, `1.0` = 64 blocks |
| `model.yaw-offset-degrees` | number | `0.0` | `-360.0..360.0` | Yaw correction of the model |
| `model.pitch-offset-degrees` | number | `0.0` | `-360.0..360.0` | Pitch correction of the model |
| `model.items.<type>.material` | string | `SPYGLASS`, `SEA_LANTERN`, `END_ROD`, `SHROOMLIGHT` for `spot`, `lamp`, `strip`, `area` | any item | Item shown as the model |
| `model.items.<type>.item-model` | string | empty | `namespace:path` or empty | Optional resource pack model |

Example: a heavier, smoother spotlight setup.

```yaml
defaults:
  rays: 200
  range-blocks: 120
performance:
  source-updates-per-tick: 2
beam:
  color: "#9FD8FF"
```

## Data

Files in `plugins/LightSetPlugin/`:

| File | Content |
|---|---|
| `lights.json` | Your light sources |
| `placed.bin` | Registry of light blocks placed by the plugin; used to clean up after a crash |

To back up, copy both files while the server is stopped or right after a save. To reset everything, stop the server and delete both files; light blocks already in the world are then not tracked any more and have to be removed by hand (for example with `/fill ... air replace light`). A file that cannot be read is renamed to `<name>.corrupt` and the plugin starts with empty data.

## Companion mod

None.

## Troubleshooting

| Problem | Solution |
|---|---|
| A spotlight lights only part of the scene | Chunks are never loaded by the plugin. The light is completed when the chunks load. Reduce `range-blocks` or raise `rays` |
| Light appears slowly | Raise `performance.block-updates-per-tick` or `performance.source-updates-per-tick` |
| Sources stop adding light | The server limit `limits.max-total-blocks` is reached |
| Console warnings such as `limits.max-rays must be in 1..5000` | A value in `config.yml` is invalid or outside its range; the default was used |
| No model is visible | Check `model.enabled` and that the chunk of the source is loaded |
| Messages are in the wrong language | Set `settings.language` to `ru` or `en` |

## License

All-Rights-Reserved.

## Русский

Плагин для Paper: прожекторы, лампы, световые ленты и световые области. Свет создаётся невидимыми блоками `minecraft:light`, ресурспак не нужен.

### Возможности

- Четыре типа источников: `spot` (конус лучей, освещает поверхности), `lamp` (одна точка), `strip` (линия между двумя точками), `area` (параллелепипед с сеткой света).
- Яркость `0..15`, включение и выключение, эффекты: `flicker`, `pulse`, `blink`.
- Группы: управление многими источниками одной командой.
- Необязательный луч из частиц у прожекторов и видимая модель (item display) у каждого источника.
- Если несколько источников освещают один блок, берётся самый яркий.
- Световые блоки, оставшиеся после сбоя или перезапуска, находятся и удаляются автоматически.
- Работа распределена по тикам и ограничена настраиваемыми бюджетами; чанки плагин не загружает.
- Сообщения на русском и английском.

### Требования

- Paper 1.21.11, Java 21
- Другие плагины не нужны.

### Установка

1. Положите `LightSetPlugin-<версия>.jar` в папку `plugins`.
2. Запустите сервер. Будет создан `plugins/LightSetPlugin/config.yml`.
3. Встаньте туда, где нужен свет, и выполните `/lightset create stage1 spot`.

Версия 2.0.0 не читает данные и конфигурацию версии 1.x (старая папка `plugins/LightSet`). Создайте источники и настройки заново.

### Команды

Имя может содержать латинские буквы, цифры, `_` и `-` (до 32 символов).

| Команда | Право | Описание |
|---|---|---|
| `/lightset` | любое право LightSet | Показать список команд |
| `/lightset create <имя> <spot\|lamp\|strip\|area>` | `lightset.edit` | Создать источник в вашей позиции, в вашем направлении |
| `/lightset remove <имя>` | `lightset.edit` | Удалить источник и его световые блоки |
| `/lightset list` | `lightset.view` | Список источников |
| `/lightset info <имя>` | `lightset.view` | Подробности об источнике |
| `/lightset tp <имя>` | `lightset.view` | Телепорт к источнику |
| `/lightset on <имя>`, `off <имя>`, `toggle <имя>` | `lightset.control` | Включить, выключить, переключить |
| `/lightset brightness <имя> <0-15>` | `lightset.control` | Задать яркость |
| `/lightset effect <имя> <none\|flicker\|pulse\|blink>` | `lightset.control` | Задать эффект |
| `/lightset aim <имя> <yaw> <pitch>` | `lightset.edit` | Направить прожектор углами |
| `/lightset aimhere <имя>` | `lightset.edit` | Направить прожектор туда, куда вы смотрите |
| `/lightset lookat <имя> <x y z>` | `lightset.edit` | Направить прожектор на точку |
| `/lightset rotate <имя> <dYaw> <dPitch>` | `lightset.edit` | Повернуть прожектор на заданные углы |
| `/lightset move <имя> [x y z]` | `lightset.edit` | Переместить источник (к вам, если позиция не указана; только в том же мире) |
| `/lightset pos2 <имя> [x y z]` | `lightset.edit` | Задать вторую точку ленты или области |
| `/lightset angle <имя> <1-179>` | `lightset.edit` | Угол конуса прожектора, градусы |
| `/lightset range <имя> <блоков>` | `lightset.edit` | Дальность прожектора |
| `/lightset rays <имя> <число>` | `lightset.edit` | Число лучей прожектора |
| `/lightset beam <имя> <on\|off>` | `lightset.edit` | Показать или скрыть луч из частиц |
| `/lightset group set <имя> <группа>` | `lightset.edit` | Добавить источник в группу |
| `/lightset group clear <имя>` | `lightset.edit` | Убрать источник из группы |
| `/lightset group on\|off\|toggle <группа>` | `lightset.control` | Переключить все источники группы |
| `/lightset group brightness <группа> <0-15>` | `lightset.control` | Задать яркость группы |
| `/lightset reload` | `lightset.reload` | Перезагрузить `config.yml` |

### Права

| Право | По умолчанию | Описание |
|---|---|---|
| `lightset.view` | оп | Список, просмотр и телепорт к источникам |
| `lightset.control` | оп | Включение, яркость и эффекты источников и групп |
| `lightset.edit` | оп | Создание, удаление и настройка источников |
| `lightset.reload` | оп | Перезагрузка конфигурации |
| `lightset.admin` | оп | Все перечисленные права |

### Конфигурация

Файл: `plugins/LightSetPlugin/config.yml`. Неверное значение выводится в консоль и заменяется значением по умолчанию.

| Ключ | Тип | По умолчанию | Диапазон | Описание |
|---|---|---|---|---|
| `settings.language` | string | `auto` | `ru`, `en`, `auto` | Язык сообщений; `auto` берёт язык клиента игрока, консоль всегда на английском |
| `limits.max-range-blocks` | int | `320` | `1..2048` | Максимальная дальность прожектора |
| `limits.max-angle-degrees` | number | `160.0` | `1.0..179.0` | Максимальный полный угол конуса |
| `limits.max-rays` | int | `400` | `1..5000` | Максимум лучей на прожектор |
| `limits.max-blocks-per-source` | int | `1024` | `1..100000` | Максимум световых блоков на источник |
| `limits.max-total-blocks` | int | `30000` | `1..10000000` | Максимум световых блоков на сервере |
| `defaults.brightness` | int | `15` | `0..15` | Яркость новых источников |
| `defaults.angle-degrees` | number | `40.0` | `1.0..limits.max-angle-degrees` | Угол конуса новых прожекторов |
| `defaults.range-blocks` | int | `250` | `1..limits.max-range-blocks` | Дальность новых прожекторов |
| `defaults.rays` | int | `120` | `1..limits.max-rays` | Число лучей новых прожекторов |
| `spot.distance-falloff` | number | `0.5` | `0.0..1.0` | Затухание на максимальной дальности |
| `spot.edge-falloff` | number | `0.5` | `0.0..1.0` | Затухание на краю конуса |
| `shapes.strip-spacing-blocks` | int | `4` | `1..64` | Расстояние между световыми блоками вдоль ленты |
| `shapes.area-spacing-blocks` | int | `6` | `1..64` | Расстояние между световыми блоками внутри области |
| `performance.source-updates-per-tick` | int | `1` | `1..100` | Сколько прожекторов пересчитывается за тик |
| `performance.block-updates-per-tick` | int | `300` | `1..100000` | Сколько световых блоков ставится или убирается за тик |
| `effects.interval-ticks` | int | `4` | `1..100` | Как часто вычисляются эффекты |
| `effects.flicker.min` | number | `0.4` | `0.0..1.0` | Минимальный множитель яркости мерцания |
| `effects.flicker.change-chance` | number | `0.5` | `0.0..1.0` | Шанс новой яркости при каждом вычислении |
| `effects.pulse.period-ticks` | int | `60` | `4..100000` | Длина одного пульса |
| `effects.pulse.min` | number | `0.2` | `0.0..1.0` | Минимальный множитель яркости пульса |
| `effects.blink.on-ticks` | int | `20` | `1..100000` | Сколько свет горит |
| `effects.blink.off-ticks` | int | `20` | `1..100000` | Сколько свет не горит |
| `beam.enabled-by-default` | boolean | `true` | `true`, `false` | Новые прожекторы показывают луч |
| `beam.interval-ticks` | int | `5` | `1..200` | Как часто луч перерисовывается |
| `beam.view-distance-blocks` | number | `64.0` | `4.0..512.0` | Игроки дальше этого расстояния луч не видят |
| `beam.step-blocks` | number | `3.0` | `0.5..32.0` | Расстояние между частицами луча |
| `beam.color` | string | `#FFF4C2` | `#RRGGBB` | Цвет луча |
| `beam.size` | number | `1.0` | `0.1..4.0` | Размер частиц луча |
| `model.enabled` | boolean | `true` | `true`, `false` | Создавать видимую модель для каждого источника |
| `model.scale` | number | `1.0` | `0.05..16.0` | Масштаб модели |
| `model.view-range-multiplier` | number | `2.0` | `0.1..16.0` | Множитель дальности прорисовки, `1.0` = 64 блока |
| `model.yaw-offset-degrees` | number | `0.0` | `-360.0..360.0` | Поправка поворота модели по yaw |
| `model.pitch-offset-degrees` | number | `0.0` | `-360.0..360.0` | Поправка поворота модели по pitch |
| `model.items.<type>.material` | string | `SPYGLASS`, `SEA_LANTERN`, `END_ROD`, `SHROOMLIGHT` для `spot`, `lamp`, `strip`, `area` | любой предмет | Предмет, который показывается как модель |
| `model.items.<type>.item-model` | string | пусто | `namespace:path` или пусто | Необязательная модель из ресурспака |

Пример: более тяжёлый и плавный прожектор.

```yaml
defaults:
  rays: 200
  range-blocks: 120
performance:
  source-updates-per-tick: 2
beam:
  color: "#9FD8FF"
```

### Данные

Файлы в `plugins/LightSetPlugin/`: `lights.json` (источники) и `placed.bin` (реестр поставленных световых блоков для очистки после сбоя). Для резервной копии скопируйте оба файла при остановленном сервере. Для полного сброса остановите сервер и удалите оба файла; уже стоящие в мире световые блоки перестанут отслеживаться, их придётся убрать вручную (например, `/fill ... air replace light`). Нечитаемый файл переименовывается в `<имя>.corrupt`, плагин стартует с пустыми данными.

### Парный мод

Нет.

### Решение проблем

- Прожектор освещает только часть сцены: плагин не загружает чанки, свет достроится при их загрузке. Уменьшите `range-blocks` или увеличьте `rays`.
- Свет появляется медленно: увеличьте `performance.block-updates-per-tick` или `performance.source-updates-per-tick`.
- Источники перестали добавлять свет: достигнут лимит `limits.max-total-blocks`.
- В консоли предупреждения о значении: значение в `config.yml` вне диапазона, использовано значение по умолчанию.
- Нет модели: проверьте `model.enabled` и загружен ли чанк источника.
- Сообщения не на том языке: задайте `settings.language` равным `ru` или `en`.

### Лицензия

All-Rights-Reserved.
