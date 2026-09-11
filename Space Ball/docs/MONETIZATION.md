# Монетизація

## AdMob (`services/ads`)

| Формат | Де | Клас | Частота |
|---|---|---|---|
| App Open | холодний старт поверх лоадера, повернення з фону | `AppOpenAdManager` + `AppOpenGate` (лоадер чекає ≤4 с) | кожен вихід на передній план |
| Banner | низ екрана, адаптивний | `AdManager.addBannerAd` | завжди |
| Interstitial | після перемоги (Next/Menu), після кожної 2-ї поразки (Retry) | `AdManager.showInterstitial`, `AdPolicy` | не частіше ніж раз на 25 с |
| Rewarded | **Continue** після смерті (раз на спробу), **Free +15 stars** у магазині | `AdManager.showRewarded`, `MainActivity.isRewardedReady` | за бажанням гравця |

Ad unit ID: `app/src/main/res/values/strings.xml` — справжні (banner, interstitial, app open,
rewarded `…/1797660720`), їх бере release. `app/src/debug/res/values/strings_ads.xml` — тестові
Google ID, перекривають справжні в debug-збірці. `AndroidManifest.xml` → `APPLICATION_ID`
справжній `ca-app-pub-4052300465234748~7593537688` для обох збірок.

`FullScreenAdState.isShowingAd` не дає показати дві повноекранні реклами одночасно.
Усі виклики SDK — на main-потоці (інакше `#008 Must be called on the main UI thread`);
методи `AdManager` самі роблять `runOnUiThread`, колбеки — теж на main → у грі `runGDX`.

## Внутрішня економіка

- Зірка на рівні = +1 до `StarUtil.stars` (лідерборд Play Games, ніколи не зменшується).
- Магазин (`SpaceShopScreen`, `SkinUtil`): баланс = stars − spent. Скіни `BallSkin`:
  CLASSIC 0, NEBULA 30, VERDANT 50, MAGMA 80, CRYSTAL 120, GOLD 160, PRISM 220.
- Rewarded «Free stars» = +15. Щоденний рівень = +5 раз на добу.
- Максимум зірок з рівнів: 60 × 3 = 180, тож усі скіни (660) вимагають перепроходжень,
  щоденних рівнів або реклами — це і є мотивація.

## TikTok SDK (`services/tiktok/TikTokManager`)

Ініціалізується в `MainActivity.initTikTok()`. Ключі лежать у `gradle.properties` (комітиться —
власник свідомо обрав тримати їх у git, щоб проєкт повністю відновлювався з репозиторію):
```properties
tiktok.app.id=<TikTok App ID з Events Manager>
tiktok.app.secret=<App Secret>
```
`app/build.gradle.kts` читає їх (з пріоритетом `local.properties`, якщо там задані) і створює
`BuildConfig.TIKTOK_APP_ID` / `TIKTOK_APP_SECRET`. Без ключів debug збирається (SDK пропускає
ініціалізацію з логом), а `assembleRelease` падає на `preReleaseBuild` — щоб не випустити
версію без атрибуції. Ризик публічного репозиторію: хтось може слати фейкові події в
TikTok-аналітику; якщо таке станеться — перевипустити App Secret у TikTok Events Manager.
`openDebugMode()` у debug → події видно в TikTok Events Manager → Test Events. Billing-бібліотека підключена лише через SDK
(покупок у грі немає) — можна прибрати або `disableAutoIapTrack()`.

## Перед релізом

1. `gradle.properties` містить ключі TikTok (інакше release не збереться).
2. Play Console → Data safety: рекламний ID, аналітика; для ЄС — UMP-згода (не зроблено).
3. `versionCode` у `app/build.gradle.kts` більший за опублікований; `assembleRelease` / bundle; підпис.
