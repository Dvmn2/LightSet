# LightSet

Динамические прожекторы и освещение на световых блоках (`minecraft:light`) для Paper 1.21.11.

## Сборка
`gradle build` → `build/libs/LightSet-1.0.0.jar` (тестовый сервер: `gradle runServer`).

## Как это работает
* **spot** — N лучей в конусе; каждый луч ищет первую твёрдую поверхность, свет ставится в воздух перед ней.
  Блоков на прожектор ≈ числу лучей, независимо от дальности.
* **lamp** — один световой блок. **strip** — линия между `create`-точкой и `pos2`.
  **area** — сетка блоков в параллелепипеде между точкой и `pos2`.
* Яркость 0–15 = уровень света в пятне; свет уровня L освещает радиус ≈ L блоков.
* Смена яркости/эффекта/вкл-выкл не пересчитывает лучи (геометрия кэшируется).
* Несколько источников на одном блоке — берётся максимальный уровень.
* Файлы: `lights.yml` (источники), `placed.yml` (реестр поставленных блоков для очистки после краша).

## Команды (право `lightset.admin`)
```
/lightset create <имя> <spot|lamp|strip|area>   (позиция и направление — по вам)
/lightset remove|info|tp <имя>      /lightset list
/lightset on|off|toggle <имя>
/lightset brightness <имя> <0-15>
/lightset aim <имя> <yaw> <pitch> | aimhere <имя> | lookat <имя> <x y z> | rotate <имя> <dYaw> <dPitch>
/lightset move <имя> [x y z]        /lightset pos2 <имя> [x y z]
/lightset angle|range|rays <имя> <значение>
/lightset beam <имя> <on|off>
/lightset effect <имя> <none|flicker|pulse|blink>
/lightset group set <имя> <группа> | clear <имя>
/lightset group on|off|toggle <группа> | brightness <группа> <0-15>
/lightset reload
```
