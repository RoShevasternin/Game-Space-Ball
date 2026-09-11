# Архітектура

## Шари

```
MainActivity (AppCompat)                    ← Android: реклама, Play Games, дозволи, TikTok
 └─ NavHostFragment → GdxFragment           ← libGDX AndroidFragmentApplication
     └─ GdxGame : AdvancedGame              ← менеджери, утиліти прогресу, один активний екран
         └─ AdvancedScreen / AdvancedBox2dScreen
             ├─ SpaceLoaderScreen   завантаження ассетів, чекає App Open рекламу (AppOpenGate)
             ├─ SpaceMenuScreen     логотип, м'яч-прев'ю, Levels/Records/Exit (btns.png), Shop/Daily
             ├─ SpaceLevelsScreen   список рівнів по планетах, рейтинг зірками, ручний скрол
             ├─ AbstractGameScreen  ІГРОВИЙ ЕКРАН (Box2D) — вся механіка, HUD, туторіал, смерть/перемога
             ├─ SpaceWinScreen      результат, зірки, Menu/Next/Replay, interstitial
             └─ SpaceShopScreen     скіни м'яча за зірки, rewarded «free stars»
```

`NavigationManager` створює екран за ім'ям класу і тримає back-stack імен; `game.updateScreen`
знищує попередній екран (`dispose`) і показує новий (`show`). Екрани — одноразові об'єкти:
кожен перехід = новий екземпляр.

## Пакети (`app/src/main/java/com/rostislav/spaceball`)

| Пакет | Що там |
|---|---|
| `game/screens` | екрани (див. вище) |
| `game/box2d` | `AbstractBody` (тіло + актор + колбеки контактів), `WorldUtil` (світ, крок 1/60, дебаг), `WorldContactListener`/`Filter` (контакти за `collisionList`), `BodyId` (рядкові ідентифікатори типів тіл), `BodyEditorLoader` (форми з `assets/physics/PhysicsData`) |
| `game/box2d/bodies` | `BBall`, `BPlat` (5 типів), `BTriangle` (шип, у т.ч. рухомий), `BStar` (сенсор), `BSpecials.kt`: `BPortal`, `BBlackHole`, `BLaser`, `BAsteroid`, `BGate` |
| `game/actors/game` | актори-«вигляд» тіл, намальовані ShapeDrawer/шейдером: `APlatform`, `ABall`, `AHazards.kt` (`ALaser`, `AAsteroid`, `ABlackHole`, `APortal`, `AGate`) |
| `game/actors/ui` | `PillButton` (неонова кнопка), `StarRating`/`StarShape`, `HintBubble` (підказка туторіалу) |
| `game/actors/button`, `actors/image` | старі текстурні кнопки керування (`AButton`) та `AImage` |
| `game/effects` | `UiShader`+`NeonPanel` (скляні панелі), `VortexShader`+`VortexActor` (портал/діра/ворота), `FxSystem`+`FxLayer` (частинки, слід), `CameraShake`, `StarfieldActor` |
| `game/utils/level` | `Planet`, `LevelData` (моделі), `LevelGenerator` (уся генерація + туторіал) |
| `game/utils/dataStore` | прогрес у DataStore: `LevelUtil` (відкриті рівні, рейтинг), `StarUtil` (зірки за весь час), `SkinUtil` (куплені/обраний скін, витрачено), `DailyUtil`, `HintUtil` (показані підказки) |
| `game/utils/skin` | `BallSkin` — перелік скінів (спрайт, ціна, кольори) |
| `game/utils/font` | `FontGenerator` (FreeType), `FontParameter` (`ui()` = обводка + тінь) |
| `game/utils` | `ShapeDrawerUtil` (ShapeDrawer + `additive{}` + `whiteRegion`), `Constants` (розміри, `toB2/toUI`), `DebugFlags`, `Util` (`runGDX`, `gdxGame`) |
| `game/manager` | `SpriteManager`/`SoundManager`/`MusicManager` (списки ассетів), `util/SpriteUtil` (регіони атласу: `aList` зірки, `bList` шипи, `pList` планети, `ballList` м'ячі), `GameDataStoreManager` (ключі DataStore) |
| `services/ads` | `AdManager` (банер/interstitial/rewarded для Activity), `AppOpenAdManager`+`AppOpenGate`, `MobileAdsInitializer`, `AdPolicy` (частота interstitial), `FullScreenAdState` |
| `services/tiktok` | `TikTokManager` |

## Потоки

- **GL-потік libGDX**: усе в `game/*`, зокрема колбеки Box2D (`beginContact`) — вони
  викликаються всередині `world.step`, тому **не можна знищувати тіла з контакту** — тільки
  через `runGDX { }` (postRunnable → наступний кадр).
- **Main/UI-потік Android**: AdMob, Play Games, `Lottie`. Колбеки реклами приходять сюди —
  у грі загортати в `runGDX`.
- **Coroutines Dispatchers.Default/IO**: DataStore (`*Util` у `dataStore`), ініціалізація AdMob.
  Утиліти прогресу мають `revision`, який зростає при кожній зміні — екрани опитують його
  раз на кадр/півсекунди, щоб підхопити асинхронно довантажені дані.

## Життєвий цикл тіла (`AbstractBody`)

`create(x, y, w, h)` (UI px, лівий-нижній кут) → створює Box2D body з форми `name` у
`PhysicsData`, масштабованої по ширині; додає `actor` на `stageUI` і синхронізує позицію
щокадру в `render()`. `renderBlockArray` — логіка щокадру (рух платформ, притягання діри,
таймер лазера). `beginContactBlockArray` — реакції на контакт. `destroy()` знищує тіло й
актор (`dispose` + `remove`). `id` порівнюється з `collisionList` іншого тіла у
`WorldContactFilter`: зіткнення є лише коли обидва списки містять id одне одного.

Сенсори (`fixtureDef.isSensor`): зірки, шипи, портали, ядро діри, лазер, ворота — не
відштовхують м'яч, лише дають контакт.

## Ігровий екран: порядок кадру

`AbstractGameScreen.render`: `update(delta)` (гальмування на землі, ліміти швидкості,
слід, астероїди, туторіал) → `worldUtil.update` (кроки фізики + `render()` тіл) → сцени
(act → скриптовані/відкладені `Actions`, draw). Трясіння камери — зсув `stageUI.camera`.

## Ассети

`assets/atlas/all.atlas` (кнопки, м'ячі `ball`, `ball1..4`, зірки `a1..4`, шипи `b1..4`,
планети `p1..4`, `BACK`, `stars`), `assets/textures/1..4.png` (фони планет 1080×1920),
`btns.png` (Levels/Records/Exit), `you_win.png` (не використовується після редизайну),
`assets/sound/*.mp3|wav`, `assets/music/music.mp3`, `assets/font/Inter-Bold.ttf`,
`assets/physics/PhysicsData` (форми: `circle`, `plat`, `a`, `b`, `h`, `v`, `box`, `laser_s/m/l/xl`).
Ассети завантажуються в `SpaceLoaderScreen` через `AssetManager`; нові файли треба додати
в `SpriteManager.EnumTexture`/`EnumAtlas`, `SoundManager.EnumSound` і відповідний `*Util`.
