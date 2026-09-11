# Збірка і тестування

## Збірка

```bash
cd "Space Ball"
sh ./gradlew :app:compileDebugKotlin -q     # тільки компіляція
sh ./gradlew :app:assembleDebug -q          # debug APK
sh ./gradlew :app:assembleRelease -q        # release + R8 (mapping у app/build/outputs/mapping/release)
```
Помилки шукати так: `... 2>&1 | grep -E "^e: |error:|BUILD|FAILED"`.

## Пристрій

Xiaomi (1080×2400) по USB. `adb` лежить у `~/Library/Android/sdk/platform-tools`.
```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.rostislav.spaceball/.MainActivity
adb logcat -d --pid=$(adb shell pidof com.rostislav.spaceball) | grep -E "Rostik|FATAL|Exception"
adb exec-out screencap -p > shot.png && sips -Z 800 shot.png --out shot_s.png
```
Логи гри — тег `Rostik` (`util/log`). Пристрій інколи на кілька секунд «відвалюється»
(`no devices/emulators found`) — просто почекати й повторити.

Після старту показується тестова App Open реклама — закрити тапом у правому верхньому куті
(`adb shell input tap 850 170`), потім гра. Внизу тестовий банер.

## Debug-прапорці (`DebugFlags`, тільки debug-збірка)

- Усі рівні відкриті в списку.
- `--ei level N` — одразу відкрити рівень N (0-based) після лоадера.
- `--ez win true` / `--ez die true` — автоперемога/смерть через 2 с (екран перемоги, оверлей Continue).
- `--ez tut true` — примусово показати туторіал.
- `--es play "D0.0,R0.3,D3.0,L3.2"` — **скриптовані натискання**: `U` короткий стрибок,
  `D` високий, `L`/`R` вбік; число — секунди від старту рівня (+1.5 с на інтро-банер).
  Кожне натискання пише в лог `play D@3.0 ball=(x, y) grounded=… air=…`.
  Це єдиний надійний спосіб тестувати фізику: `adb shell input tap` має затримку 0.4–1 с.

Приклад повного проходження туторіалу:
```bash
adb shell am start -n com.rostislav.spaceball/.MainActivity --ei level 0 --ez tut true \
  --es play "D0.0,R0.3,D3.0,L3.2,D6.0,R6.2,D6.9,D10.5,L10.7,D11.4,R14.0"
```

## Координати для `adb shell input tap`

Гра 1080×1920 летербоксована у 1080×2400: `screen_y = 240 + (1920 − game_y)`.
Кнопки: UPUP ≈ (399, 2035), UP ≈ (217, 2035), LEFT ≈ (747, 2035), RIGHT ≈ (929, 2035).
Меню: Levels ≈ (540, 960), Shop ≈ (351, 725), Daily ≈ (727, 725). Назад ≈ (85, 407).

## Що перевіряти після змін

1. `assembleRelease` збирається (R8 може зламати рефлексію/ресурси).
2. Логкат без `FATAL`/`Exception` з пакета гри на: меню → рівні → рівень → смерть →
   Continue → перемога → магазин.
3. Шейдери компілюються: у логах немає `UiShader compile failed` / `VortexShader compile failed`
   (якщо є — ефекти мовчки не малюються).
4. Скриптований прохід туторіалу (вище) закінчується екраном YOU WIN.
