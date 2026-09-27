# Публикация в Google Play — пошаговая инструкция для браузера

Приложение: **Resource monitor** (`com.kotekompotek.resourcemonitor`).
Тексты для листинга уже лежат в `fastlane/metadata/android/en-US/`
(можно копировать оттуда в веб-формы).

## 0. Что подготовить заранее (чек-лист)

- [ ] GitHub Pages включены (Settings репозитория → Pages → `main` + `/docs`),
      ссылка https://kotekompotek.github.io/resource-monitor/privacy-policy.html открывается
- [ ] Релизный keystore создан, его путь и пароли записаны в `local.properties`:
      `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
      (создать: Android Studio → Build → Generate Signed Bundle/APK → Create new)
- [ ] Подписанный `.aab` собран: `./gradlew bundleRelease`
      (файл: `app/build/outputs/bundle/release/app-release.aab`)
- [ ] Паспорт для верификации личности, карта для $25, включённая 2FA в Google-аккаунте
- [ ] Контактный email для листинга, иконка 512×512 PNG, feature graphic 1024×500,
      минимум 2 скриншота телефона (можно сделать позже, но релиз без них не выйдет)
- [ ] Короткое видео (до ~1 мин, можно записью экрана телефона):
      Start Monitor → виджет поверх приложений → уведомление с кнопкой Stop →
      остановка. Понадобится для декларации foreground-сервиса

## 1. Аккаунт разработчика (play.google.com/console)

1. Войти своим Google-аккаунтом → принять соглашение → оплатить **$25** (карта).
2. Тип аккаунта: **личный** (не организация — иначе попросят D-U-N-S).
3. Верификация личности: загрузить фото паспорта/ID, дождаться подтверждения
   (обычно от часов до пары дней). Без неё дальше не пустит.
4. Включить двухфакторную аутентификацию Google-аккаунта, если ещё нет.

## 2. Создание приложения

All apps → **Create app**:
- App name: `Resource monitor`
- Default language: English (United States)
- App or game: **App**
- Free or paid: **Free**
- Declarations: подтвердить, что соблюдаете правила и законы США об экспорте.
- Create → попадёте на **Dashboard** (список задач с галочками — идём по нему).

## 3. Первая (обязательная ручная) загрузка сборки

Без хотя бы одной ручной загрузки fastlane работать не будет.

1. В меню приложения слева: **Test and release → Internal testing** →
   **Create new release** (для первого раза подойдёт и Production, но internal
   проверяется быстрее и не виден всем).
2. Загрузить `app-release.aab` → подождать обработку → **Next**.
3. Release name/description можно оставить по умолчанию → **Save** →
   **Review release** → **Start rollout to internal**.
4. Каждый следующий апдейт: увеличить `versionCode` в `app/build.gradle`
   (1 → 2 → 3…), иначе Console отклонит загрузку как дубликат.

## 4. Анкеты и разделы Dashboard (заполнять все с красным маркером)

Идти по Dashboard сверху вниз. Что выбирать для нашего приложения:

1. **App access** — «All or some functionality is restricted?» → **No**,
   всё работает без логина. Save.
2. **Ads** — «Does your app contain ads?» → **No**. Save.
3. **Content rating** — Start questionnaire → категория: Utilities/Productivity;
   на все вопросы про насилие/секс/азартные игры/соцсети — **No**.
   Получите рейтинг (обычно Everyone). Submit.
4. **Target audience and content** —
   - Target age: **18 and over** (снять галочки с детских групп);
   - «Appeal to children?» → **No**;
   - соцсети/UGC отсутствуют. Save.
5. **News apps / COVID / Government** — везде **No / Not applicable**.
6. **Data safety** — ключевой раздел:
   - «Does your app collect or share any user data?» → **No** для всех типов.
     (Замеры CPU/RAM/трафика не покидают устройство; ping 8.8.8.8 — ICMP
     без персональных данных.)
   - Account deletion: аккаунтов нет → соответствующий пункт пропустить/«N/A».
   - Privacy policy URL: `https://kotekompotek.github.io/resource-monitor/privacy-policy.html`.
7. **Privacy policy** (поле в Store settings) — та же URL.
8. **Store listing** (Main store listing):
   - App name: `Resource Monitor: CPU RAM Net` (≤30 символов)
   - Short description: скопировать из `fastlane/metadata/android/en-US/short_description.txt` (≤80)
   - Full description: скопировать из `full_description.txt` (≤4000)
   - App icon: PNG **512×512**; Feature graphic: **1024×500**;
     Phone screenshots: **минимум 2**;
   - Category: **Tools** (или Personalization — на выбор, Tools точнее);
     Contact email: ваш support-email; Privacy policy URL (ещё раз здесь).
   - Сохранить **без отправки на review** до готовности всего остального.
9. **Foreground service permissions** (появится, т.к. в манифесте `specialUse`):
   - Выбрать тип **Special use** → описание своими словами:
     «Continuous on-device monitoring of CPU, RAM, disk, battery, network
     speed and ping, shown in a user-controlled overlay widget»;
   - загрузить видео из чек-листа (п. 0): запуск → уведомление → остановка;
   - подтвердить, что сервис виден пользователю и останавливается им.
10. **App signing**: оставить **Play App Signing** (Google управляет ключом
    подписи; ваш upload-ключ — keystore из `local.properties`).
11. **Target API / 64-bit**: с `targetSdk 36` зелёное автоматически.

## 5. Отправка на проверку

Когда все пункты Dashboard зелёные: **Publishing overview → Send for review**.
- Internal-трек проверяют обычно от часов до 1–2 дней.
- После одобрения internal-релиза можно готовить production.

## 6. Service account для fastlane (один раз, после первой загрузки)

1. [console.cloud.google.com](https://console.cloud.google.com) → создать проект
   (например `resource-monitor-play`) → в нём **APIs & Services → Enable APIs** →
   включить **Google Play Android Developer API**.
2. **APIs & Services → Credentials → Create Credentials → Service Account**
   (имя `fastlane-supply`) → создать → вкладка **Keys → Add key → JSON** →
   скачать файл.
3. Вернуться в **Play Console → Users and permissions → Invite new users** →
   вставить email сервисного аккаунта (`...@....iam.gserviceaccount.com`) →
   дать права **Release manager** (или Admin для простоты, потом сузить) →
   Invite. Доступ активируется до 24–48 часов (обычно быстрее).
4. Положить JSON в `fastlane/play-service-account.json` (**не коммитить**,
   он уже в `.gitignore`) либо задать путь в переменной `SUPPLY_JSON_KEY`.
5. Проверка: `fastlane run validate_play_store_json_key json_key:"fastlane/play-service-account.json"`.

## 7. Дальнейшие релизы через fastlane (терминал в корне проекта)

```powershell
gem install fastlane -NV   # один раз (нужен Ruby с rubyinstaller.org)
fastlane internal          # сборка AAB + заливка в internal-трек
fastlane metadata          # только тексты/картинки из fastlane/metadata
fastlane promote           # internal → production, rollout 20%
```

Перед каждым релизом: поднять `versionCode`, дописать changelog
(`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`).

## 8. Production-релиз

1. Internal-релиз одобрен и протестирован → `fastlane promote`
   (или вручную: Production → Create new release → Add from library).
2. Staged rollout 20% → наблюдать краши/отзывы 2–3 дня → Halt/Resume
   до 50% → 100%.
3. Апелляции при реджекте: Policy status → appeal, приложить видео и ссылку
   на политику. Типичные причины для такого приложения и что отвечать —
   в таблице ниже.

## Типичные реджекты и ответы

| Реджект | Ответ/фикс |
|---|---|
| «Foreground service without valid use case» | Указать `specialUse`: непрерывный on-device мониторинг с виджетом; приложить видео запуска/остановки |
| «Missing privacy policy» | Дать Pages-URL; проверить, что страница открывается без логина |
| «Data safety mismatch» | Убедиться, что везде «No collection», Device IDs не отмечать (Advertising ID не используем) |
| «Deceptive overlay / SYSTEM_ALERT_WINDOW» | Скриншоты виджета + текст in-app раскрытия; виджет не похож на системный UI, закрывается тремя способами |
| «Target API» | У нас 36 — приложить скрин `app/build.gradle`, если спросят |
