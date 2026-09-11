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

- Реклама — тестові блоки Google (`app/src/debug/res/values/strings_ads.xml`); замки рівнів —
  як у release (відкрити все — `--ez unlock true`).
- `--ei level N` — одразу відкрити рівень N (0-based) після лоадера.
- `--ez win true` / `--ez die true` — автоперемога/смерть через 2 с (екран перемоги, оверлей Continue).
- `--ez tut true` — примусово показати туторіал.
- `--es play "D0.0,R0.3,D3.0,L3.2"` — **скриптовані натискання**: `U` короткий стрибок,
  `D` високий, `L`/`R` вбік; число — секунди від старту рівня (+1.5 с на інтро-банер).
  Кожне натискання пише в лог `play D@3.0 ball=(x, y) grounded=… air=…`.
  Це єдиний надійний спосіб тестувати фізику: `adb shell input tap` має затримку 0.4–1 с.
- `--ez unlock true` — відкрити всі рівні. Без нього debug (зокрема Run з Android Studio)
  має ті самі замки, що й release.
- `--es progress "v2:3333333333....."` — підмінити прогрес рівнів у пам'яті (символ на рівень:
  `.` не пройдено, `0..3` зірки; не зберігається). Разом із `locks` — перевірка правил відкриття.
- `--ef freeze 1.2` — через N с (та сама шкала, що й `play`) зупинити фізику й частинки:
  кадр застигає для скріншота.
- `--ez shots true` — **режим зйомки для маркету**: без банера й App Open реклами, без
  туторіалу, з фейковим прогресом (пройдено 22 рівні, 186 зірок, куплені скіни); нічого
  не зберігає в DataStore. Разом із ним: `--es skin NEBULA` (підмінити скін),
  `--es screen shop|levels` (одразу відкрити екран).

## Скріншоти для Google Play

Сирі кадри знімаються в режимі `shots` (див. вище), маркетингові картинки складає
`tools/compose_store_shots.py` (Pillow): заголовок, «телефон» із неоновою рамкою на
затемненому фоні планети → `store/NN_<name>.png` (1080×1920) і `store/feature_graphic.png`
(1024×500). Список кадрів і заголовків — у `SHOTS` усередині скрипта.
```bash
python3 -m venv /tmp/venv && /tmp/venv/bin/pip install pillow
/tmp/venv/bin/python tools/compose_store_shots.py          # читає store/raw/*.png
```
## Рекламне відео (TikTok, 9:16)

`screenrecord` не пише звук, тому в режимі `shots` гра логує кожен звук як
`SFX <файл> <epoch ms> <гучність> <висота>`, а скрипт збірки відтворює доріжку з оригінальних
файлів гри. На кожен кліп потрібні три файли в `store/video/raw/` (у .gitignore):
```bash
adb logcat -c
adb shell am start -n com.rostislav.spaceball/.MainActivity --ez shots true --ei level 45 --es play "D0.0,R0.3,D0.9"
adb shell "date +%s%3N > /sdcard/x.t; screenrecord --time-limit 20 --bit-rate 16000000 --size 1080x2400 /sdcard/x.mp4"
adb pull /sdcard/x.mp4 store/video/raw/nebulon.mp4; adb pull /sdcard/x.t store/video/raw/nebulon.t
adb logcat -d | grep " SFX " | awk '{for(i=1;i<=NF;i++) if($i=="SFX") print $(i+1),$(i+2),$(i+3),$(i+4)}' > store/video/raw/nebulon.sfx
```
Ролик складає `tools/build_tiktok_video.py` (ffmpeg із пакета `imageio-ffmpeg`): сегменти,
підписи, xfade, фінальна заставка, аудіо → `store/video/spaceball_tiktok_27s.mp4` і `_15s.mp4`.
Список сегментів — `SEGMENTS`/`SHORT`; `LATENCY` (0.35 с) — затримка старту screenrecord
відносно `date`, перевірена по кадрах стрибка. Музику додавати в TikTok (Commercial Music
Library), оригінальний звук у редакторі TikTok — на 30–50 %.

Ролик 16:9 для YouTube / хедера сторінки Play — `tools/build_youtube_video.py` (ті самі
записи `store/video/raw`, телефон із неоновою рамкою праворуч, підписи зліва, інтро та
фінал на панорамі планет) → `store/video/spaceball_youtube_16x9.mp4`.

## Іконка

`tools/make_icon_variants.py` малює варіанти адаптивної іконки (1024 px = 108dp, безпечна
зона 66dp) і порівняльний лист → `store/icon_variants/`. Кожен варіант має `_bg`, `_fg`,
`_mono` (для Material You) і `_full`.

**Увага:** debug- і release-збірки ділять один DataStore на пристрої, тож автоперемоги
(`--ez win`) під час тестів відкривають рівні «по-справжньому». Скинути прогрес:
`adb shell pm clear com.rostislav.spaceball`.

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
