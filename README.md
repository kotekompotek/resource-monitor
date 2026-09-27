# Resource monitor

Android-приложение с плавающим оверлей-виджетом мониторинга ресурсов устройства
в реальном времени.

- **Пакет:** `com.kotekompotek.resourcemonitor`
- **Тип:** нативное Android-приложение, Kotlin
- **Минимальная версия:** Android 5.0 (API 21)

## Возможности

Поверх всех окон отображается перетаскиваемый виджет с 7 метриками
и мини-графиками истории (буфер 600 точек, ~60 секунд при опросе 100 мс):

| Метрика | Текст | График | Источник данных |
|---|---|---|---|
| Disk — свободное место (МБ / % / оба) | `tvFreeSpace` | `graphView` | `StatFs` внешнего хранилища |
| RAM — свободная память (МБ / % / оба) | `tvFreeRam` | `graphRamView` | `ActivityManager.MemoryInfo` |
| CPU — средняя частота (МГц) | `tvCpuLoad` | `graphCpuView` | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` |
| Battery Temp (°C, сглаженная) | `tvBatteryTemp` | `graphBatteryView` | `ACTION_BATTERY_CHANGED / EXTRA_TEMPERATURE` |
| Battery Current (A) | `tvBatteryCurrent` | `graphCurrentView` | `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` |
| Net — скорость Rx+Tx (КБ/с / МБ/с, сглаженная) | `tvNetSpeed` | `graphNetView` | `TrafficStats`, шкала 0–1024 КБ/с |
| Ping до 8.8.8.8 (мс / timeout / err) | `tvPing` | `graphPingView` | `ping -c 1 -W 1 8.8.8.8`, шкала 0–100 мс |

Каждая метрика настраивается: видимость текста (`Show`), точность (`Pr`, 0–5 знаков),
инверсия графика (`Rev`), видимость графика (`Gr`).
Дополнительно: режим Disk/RAM (`Val` / `%` / `Both`), масштаб виджета (0.5x–2.0x),
ширина (≥50dp), высота графиков (≥10dp). Все изменения применяются живьём,
без перезапуска сервиса.

## Технологический стек

- AGP `8.7.3`, Kotlin `2.1.0`, Gradle `8.11.1`, `jvmTarget 1.8`
- `compileSdk 36`, `minSdk 21`, `targetSdk 36`, `versionCode 1`, `versionName 1.0`
- Зависимости: `core-ktx:1.9.0`, `appcompat:1.6.1`, `material:1.8.0`,
  `constraintlayout:2.1.4`
- `gradle.properties`: `useAndroidX=true`, `enableJetifier=true`

## Структура проекта

```
app/src/main/
  AndroidManifest.xml
  java/com/kotekompotek/resourcemonitor/
    MainActivity.kt     # экран настроек, старт/стоп сервиса
    FloatingService.kt  # оверлей, сбор метрик каждые 100 мс
    SpaceGraphView.kt   # кастомный View графика
  res/layout/
    activity_main.xml   # ScrollView настроек
    floating_widget.xml # LinearLayout оверлея
    remove_view.xml     # зона «Удалить» снизу экрана
  res/values/strings.xml, themes.xml, colors.xml
PRIVACY_POLICY.md   # текст политики конфиденциальности (разместить по публичному URL)
.gitignore          # local.properties, keystore-файлы и build/ не коммитятся
```

## Архитектура

### `MainActivity.kt`

Экран настроек и управление разрешением `SYSTEM_ALERT_WINDOW`.

- `initViews()` — биндинг 7 групп `Show + Pr + Rev + Gr`,
  `rgDiskDisplay` / `rgRamDisplay`, `sbWidth` / `sbHeight` / `sbScale`.
- `loadSettings()` — чтение `SharedPreferences "monitor_prefs"`.
  Дефолты: всё видимо, `prec`: disk=2, ram=2, cpu=1, battery=1, current=3,
  net=2, ping=0; `widget_width=150dp`, `graph_height=30dp`, `widget_scale=1.0`.
- `saveAndNotify()` — запись prefs, пересчёт `disk_mode` / `ram_mode`
  (`0=Val, 1=%, 2=Both`), рассылка `ACTION_SETTINGS_CHANGED`
  (`com.kotekompotek.resourcemonitor.SETTINGS_CHANGED`) запущенному сервису.
- `btnStart` — переключение `Start Monitor` / `Stop Monitor`.
  При отсутствии права — in-app раскрытие (зачем нужен оверлей, что он не
  маскируется под систему и как останавливается), затем системный экран
  `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` через Activity Result API.
  На Android 13+ запрашивается `POST_NOTIFICATIONS` (сервис работает и без него).
- Запуск сервиса — `ContextCompat.startForegroundService()`; состояние —
  `FloatingService.isRunning` (без deprecated `getRunningServices()`).
- Кнопка `Privacy Policy` — диалог с краткой политикой и ссылкой
  (`PRIVACY_POLICY_URL`, заменить перед публикацией).

### `FloatingService.kt`

Foreground-`Service` (`START_STICKY`, тип `specialUse` — непрерывный
on-device мониторинг) с двумя окнами
`TYPE_APPLICATION_OVERLAY` (API 26+) / `TYPE_PHONE`.

- `onCreate()` — `WindowManager`, `ActivityManager`, `BatteryManager`,
  базовый замер `TrafficStats`, `createFloatingView()`, `createRemoveView()`,
  `startForegroundSafe()` с постоянным уведомлением (канал `monitor_fg`,
  действие Stop), запуск цикла `updateMetrics()` каждые 100 мс.
- Пауза опроса при выключенном экране (`ACTION_SCREEN_ON/OFF`,
  `RECEIVER_NOT_EXPORTED` на API 33+).
- `applySettingsToUi()` — видимости, `isReversed`, размер шрифта
  `12sp * scale`, размеры в `dp * density * scale`; сброс автомасштаба
  Disk/RAM/CPU/Temp/Current, фикс шкал Net (0–1024 КБ/с) и Ping (0–100 мс).
- Перетаскивание: `ACTION_DOWN` показывает `remove_view`, `MOVE` двигает окно,
  зона ниже 80% высоты экрана запускает 3-секундный таймер удаления,
  `ACTION_UP` отменяет.
- `onConfigurationChanged()` — удерживает виджет в пределах экрана при повороте.
- `updateMetrics()` — опрос всех включённых метрик; Net замеряется раз в ≥1 с
  со сглаживанием `easeInOut`, Ping — в фоновом потоке не чаще раза в 5 с.
  Disk читается из внутреннего раздела (`Environment.getDataDirectory()`).
  Размеры экрана — `WindowMetrics` на API 30+, fallback на `getSize()`.
- `onDestroy()` — снятие callbacks и ресивера, `stopForeground(REMOVE)`,
  удаление обоих окон, `isRunning=false`.
- Команда остановки: action `ACTION_STOP` (из уведомления) и удаление виджета.

### `SpaceGraphView.kt`

Кастомный `View`: белая линия 2px, сглаживание `quadTo`.
`minY`/`maxY = null` — автомасштаб; Net/Ping задают фиксированные границы
с расширением вверх при выбросах. `isReversed` инвертирует направление.

### Layouts / Manifest

- `activity_main.xml` — `ScrollView`: `btnStart`, 7 строк настроек,
  `RadioGroup` Disk/RAM, слайдеры Scale (max 200), Width (max 400),
  Height (max 200) с кнопками `-/+`.
- `floating_widget.xml` — вертикальный `LinearLayout`, чёрный фон, padding 8dp;
  пары `TextView` (bold 12sp, свой цвет) + `SpaceGraphView` (30dp).
- `remove_view.xml` — `MATCH_PARENT`, полупрозрачный красный фон, «Удалить».
- `AndroidManifest.xml` — разрешения `SYSTEM_ALERT_WINDOW`, `INTERNET`,
  `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`; `MainActivity` (launcher),
  `FloatingService` (`enabled=true`, `exported=false`,
  `foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).

## Настройки (`SharedPreferences "monitor_prefs"`)

Ключи: `show_disk/ram/cpu/battery/current/net/ping`, `prec_*`, `rev_*`,
`gr_*`, `disk_display_id` / `ram_display_id`, `disk_mode` / `ram_mode`,
`widget_width`, `graph_height`, `widget_scale`.

## Сборка и запуск

1. Открыть проект в Android Studio, выполнить Gradle sync
   (потребуется JDK из поставки Studio; platform `android-36` ставится через SDK Manager).
2. Собрать `debug` для проверки или `release` (minify + shrink включены).
   Для подписи релиза добавьте в `local.properties`:
   `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD` — без них release собирается без подписи
   (подпись через Play App Signing). Keystore-файлы не коммитятся (`.gitignore`).
3. Публикуйте формат **App Bundle (`.aab`)**, а не APK.
4. Установить, прочитать in-app раскрытие, выдать «Поверх других приложений»,
   нажать `Start Monitor`. Остановка: кнопка в приложении, действие Stop
   в уведомлении или удержание виджета 3 с в красной зоне.

## Соответствие правилам Google Play

Проверено по Центру правил для разработчиков
(https://play.google/intl/ru/developer-content-policy/,
разделы «Данные пользователя», «Разрешения», «Злоупотребление ресурсами
устройства и сети», «Целевой уровень API», «Вредоносное ПО»)
и требованиям к targetSdk на 2026 год
(с 31.08.2026 новые приложения и обновления — **API 36**,
поддержка существующих — минимум **API 35**).

### Вердикт: ✅ кодовые требования закрыты, остались шаги в Play Console

Все блокирующие нарушения из аудита исправлены в коде (2026-09-26):

| № | Было | Статус |
|---|---|---|
| 1 | `targetSdk 33` | ✅ `targetSdk/compileSdk 36`, AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1 |
| 2 | Фоновый `Service` без уведомления | ✅ Foreground-сервис `specialUse` + постоянное уведомление с Stop, декларация типа в манифесте, `POST_NOTIFICATIONS` на API 33+ |
| 3 | Нет политики конфиденциальности | ✅ Диалог в приложении (`btnPrivacy`), `PRIVACY_POLICY.md` для хостинга; осталось: разместить по URL и вставить ссылку в Console |
| 4 | `SYSTEM_ALERT_WINDOW` без раскрытия | ✅ In-app диалог перед системным запросом; осталось: описать оверлей в листинге со скриншотами |
| 5 | Опрос 100 мс + ping каждую секунду | ✅ Ping не чаще раза в 5 с, пауза опроса при выключенном экране, перерисовка только видимых графиков |
| 6 | Deprecated API | ✅ `isRunning`-флаг вместо `getRunningServices()`, `WindowMetrics`, `getDataDirectory()`, Activity Result API, `RECEIVER_NOT_EXPORTED` |
| 7 | Пароль keystore в коде | ✅ Секреты только в `local.properties`, `.gitignore` покрывает `*.keystore`/`local.properties`/`build/` |
| 8 | Нет материалов для модерации | ⏳ Готовится вручную (см. план ниже) |

### Что уже соответствует

- Нет SMS/Call Log, локации, `QUERY_ALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`,
  Accessibility API — соответствующие декларации не нужны.
- Нет сторонних SDK, рекламы, покупок, самообновлений и загрузки исполняемого
  кода — разделы «SDK», «Монетизация», «Вредоносное ПО» не нарушены.
- Все данные обрабатываются локально; передача персональных данных
  на сервер отсутствует (ping 8.8.8.8 — ICMP без персональных данных).
- Оверлей перемещаемый и удаляемый, не маскируется под системный UI,
  не блокирует систему необратимо.

### Осталось сделать вручную в Play Console

1. Разместить `PRIVACY_POLICY.md` по публичному URL без геоблока (не PDF);
   вставить ссылку в листинг и в `PRIVACY_POLICY_URL` в `MainActivity.kt`.
2. Заполнить Data safety: сбор/передача данных — «нет» по всем типам.
3. Заполнить декларацию Foreground Service (`specialUse`, мониторинг ресурсов):
   описание, влияние на пользователя, демо-видео запуска/остановки.
4. Заполнить листинг (описание оверлея + скриншоты), контентный рейтинг
   (утилита, не для детей), категорию.
5. Загрузить подписанный `.aab`, пройти review. Продление дедлайна targetSdk
   (до 01.11.2026) запрашивать только если review затянется.
