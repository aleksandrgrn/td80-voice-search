# Voice Search App — Architecture Design

**Дата:** 2026-04-11
**Статус:** draft

---

## 1. Обзор

Нативное Android-приложение на Kotlin, которое перехватывает кнопку голосового поиска на пульте проектора TD80 Pro, распознаёт речь, ищет по TMDB и отображает карточки с постерами. При выборе карточки — запускает нужное медиа-приложение с поисковым запросом через Android Intent.

---

## 2. Целевое устройство

| Параметр | Значение |
|----------|----------|
| Модель | Droidlogic TD80 Pro |
| Android | 13 (API 33) |
| Архитектура | armeabi-v7a |
| Характеристики | tv, nosdcard, leanback |
| ADB | адрес спрашивать: порт меняется после ребута, IP — после аренды DHCP |
| Speech Services | com.google.android.tts (установлен и работает) |

---

## 3. Архитектура

### 3.1 Компоненты

```
┌──────────────────────────────────────────────────┐
│                 VoiceSearchApp                    │
│                                                  │
│  ┌─────────────────────┐  ┌───────────────────┐ │
│  │ AssistantService    │  │ SearchActivity     │ │
│  │ (AccessibilitySvc)  │→│ (SearchActivity UI) │ │
│  │                     │  │                     │ │
│  │ - перехват ASSIST   │  │ - голосовой ввод   │ │
│  │ - запуск SearchAct  │  │ - текстовый ввод   │ │
│  └─────────────────────┘  │ - карточки         │ │
│                            │ - провайдер-блоки   │ │
│                            └─────────┬─────────┘ │
│                                      │           │
│                    ┌─────────────────┼──────┐    │
│                    │                 │      │    │
│           ┌────────▼──────┐ ┌───────▼───┐  │    │
│           │ SearchProvider │ │ Intent    │  │    │
│           │ (interface)   │ │ Dispatcher│  │    │
│           └────────┬──────┘ └───────┬───┘  │    │
│                    │                 │      │    │
│           ┌────────▼──────┐         │      │    │
│           │ TmdbProvider  │         │      │    │
│           │ (HTTP → TMDB) │         │      │    │
│           └───────────────┘         │      │    │
│                                     │      │    │
│               ┌─────────────────────┘      │    │
│               ▼                            │    │
│  Целевые приложения:                       │    │
│  - ru.yourok.num (NUM)                     │    │
│  - org.smarttube.stable (SmartTube)        │    │
│  - ru.yourok.lampa (Lampa)                 │    │
│  - com.laxymedia.deluxe (LazyMediaDeluxe)  │    │
└──────────────────────────────────────────────────┘
```

### 3.2 AssistantService (AccessibilityService)

**Назначение:** Перехват нажатия ASSIST key (keyCode 219) на пульте.

**Поведение:**
- При получении `KEYCODE_ASSIST` → запуск `SearchActivity`
- Сервис должен быть включён пользователем в Settings → Accessibility
- Необходима декларация в манифесте с `BIND_ACCESSIBILITY_SERVICE`

**Ключевые детали:**
- `onKeyEvent()`: фильтр по `keyCode == KeyEvent.KEYCODE_ASSIST` и `action == ACTION_DOWN`
- После перехвата → `startActivity(Intent(this, SearchActivity::class.java))` с флагами `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`

### 3.3 SearchActivity (Leanback UI)

**Назначение:** Полноэкранная Activity с голосовым поиском и результатами.

**UI-структура:**
```
┌─────────────────────────────────────────────────────┐
│  🔍 [аватар 2___________________________] [🎤]     │  ← строка поиска + кнопка голоса
├─────────────────────────────────────────────────────┤
│  TMDB                                              │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐             │  ← горизонтальный ряд карточек
│  │постер│ │постер│ │постер│ │постер│             │
│  │ title│ │ title│ │ title│ │ title│             │
│  └──────┘ └──────┘ └──────┘ └──────┘             │
├─────────────────────────────────────────────────────┤
│  [NUM]  [SmartTube]  [Lampa]  [LazyMedia]         │  ← всегда видны, сырой запрос
└─────────────────────────────────────────────────────┘
```

Кнопки приложений внизу **всегда видны** — отправляют сырой голосовой запрос.
При нажатии на карточку → открывается DetailActivity.

**Тематика:** Тёмная (leanback-стиль), подходит для проектора/TV.

**Голосовой ввод:**
- При открытии — автозапуск SpeechRecognizer
- Текст распознанной речи подставляется в строку поиска
- Кнопка 🎤 — повторный запуск распознавания
- Можно также набрать текст вручную с виртуальной клавиатуры

**Навигация пультом:**
- D-pad навигация (up/down/left/right) для перемещения между карточками
- Enter — выбор карточки / нажатие кнопки приложения
- Back — закрытие SearchActivity

### 3.4 DetailActivity

**Назначение:** Детальная информация о выбранном фильме/сериале + кнопки запуска в приложениях.

**UI-структура:**
```
┌─────────────────────────────────────────────────────┐
│  ← Назад                                           │
├─────────────────────────────────────────────────────┤
│  ┌──────────┐  Название (год)                     │
│  │          │  Жанр · Длительность                 │
│  │  постер  │                                      │
│  │          │  Описание фильма...                  │
│  └──────────┘                                      │
│                                                    │
│  Искать в:                                         │
│  [NUM]  [SmartTube]  [Lampa]  [LazyMedia]        │  ← точный title из TMDB
└─────────────────────────────────────────────────────┘
```

**Два уровня кнопок приложений:**

| Кнопки | Где | Что передаёт | Когда использовать |
|--------|-----|-------------|-------------------|
| Внизу SearchActivity | Всегда видны | Сырой запрос "аватар 2" | Быстрый поиск без выбора карточки |
| В DetailActivity | После нажатия на постер | Точный title "Аватар: Путь воды" | Точный поиск конкретного фильма |

Кнопка "Назад" или Back на пульте → возврат в SearchActivity.

### 3.5 SearchProvider (интерфейс)

```kotlin
interface SearchProvider {
    val id: String              // уникальный идентификатор ("tmdb", "num")
    val displayName: String    // отображаемое имя ("TMDB", "NUM")
    val type: ProviderType     // CARDS (возвращает карточки) или LAUNCH_ONLY (только кнопка)

    suspend fun search(query: String): List<SearchResult>
}

enum class ProviderType { CARDS, LAUNCH_ONLY }

data class SearchResult(
    val id: String,
    val title: String,
    val posterUrl: String?,     // URL постера
    val year: String?,
    val overview: String?,
    val providerId: String,
    val metadata: Map<String, String> = emptyMap()  // провайдер-специфичные данные
)
```

### 3.6 TmdbSearchProvider

**Назначение:** Поиск по TMDB API v3.

**Эндпоинты:**
- `/search/multi` — поиск фильмов, сериалов, людей
- `/search/movie` — только фильмы
- `/search/tv` — только сериалы

**Маппинг:**
- `poster_path` → `https://image.tmdb.org/t/p/w500{path}`
- Язык: `language=ru-RU`

**API key:** через `BuildConfig.TMDB_API_KEY` (из `local.properties` при сборке).

### 3.7 IntentDispatcher

**Назначение:** Запуск целевого приложения с поисковым запросом.

**Конфигурация приложений (захардкожена):**

```kotlin
data class TargetApp(
    val packageName: String,
    val searchAction: String,
    val displayName: String
)

val TARGET_APPS = listOf(
    TargetApp("ru.yourok.num", Intent.ACTION_SEARCH, "NUM"),
    TargetApp("org.smarttube.stable", "android.media.action.MEDIA_PLAY_FROM_SEARCH", "SmartTube"),
    TargetApp("ru.yourok.lampa", Intent.ACTION_SEARCH, "Lampa"),
    TargetApp("com.laxymedia.deluxe", Intent.ACTION_SEARCH, "LazyMediaDeluxe"),
)
```

**Поведение:**
- Проверяет, установлено ли приложение (`PackageManager`)
- Формирует Intent с action и `putExtra(SearchManager.QUERY, query)`
- Добавляет флаг `FLAG_ACTIVITY_NEW_TASK`
- Если приложение не установлено — скрывает кнопку / показывает "не установлено"

---

## 4. Data Flow

```
1. Пульт: ASSIST key нажат
2. AssistantService.onKeyEvent() → перехват
3. startActivity(SearchActivity)
4. SearchActivity.onCreate() → автозапуск SpeechRecognizer
5. onResults() → query = "аватар 2"
6. query → TmdbSearchProvider.search() (coroutine)
7. TMDB API → List<SearchResult>
8. UI: отображение карточек в блоке "TMDB"
9. UI: отображение кнопок установленных приложений
10. Пользователь: нажатие на карточку → выбор приложения
11. IntentDispatcher.launch(app, query)
12. Целевое приложение открывается с поисковым запросом
```

---

## 5. Разрешения

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
```

- `RECORD_AUDIO` — для SpeechRecognizer (runtime permission, запрашивать при первом запуске)
- `INTERNET` — для TMDB API
- `BIND_ACCESSIBILITY_SERVICE` — для AssistantService

Примечание: `SYSTEM_ALERT_WINDOW` не нужен — используется обычная Activity с leanback-тёмной темой, а не overlay поверх других приложений. На TV-устройствах полноэкранная Activity работает как "overlay" с точки зрения UX.

---

## 6. Failure Modes

| Ситуация | Обработка |
|----------|-----------|
| AccessibilityService не включён | При запуске приложения — показать экран с инструкцией "Включите Accessibility" + ссылка в настройки |
| RECORD_AUDIO не выдан | Запросить при первом запуске; если отказано — показать текстовый ввод только |
| Нет интернета | Показать toast "Нет подключения"; оставить кнопку запуска приложений рабочей |
| TMDB API key невалиден | Показать toast "Ошибка TMDB"; кнопки запуска приложений работают |
| Целевое приложение не установлено | Скрыть кнопку / показать серой с подписью "не установлено" |
| SpeechRecognizer ошибка | Показать ошибку в UI; оставить текстовый ввод рабочим |
| Ничего не найдено в TMDB | Показать "Ничего не найдено"; кнопки приложений работают (intent всё равно отправится) |

---

## 7. Testing & Verification

### Стратегия тестирования

**Эмулятор НЕ используется.** Проектор — основное устройство тестирования.

Причины:
- Эмулятор не эмулирует пульт с keyCode 219 (ASSIST key)
- На headless-сервере нет микрофона для SpeechRecognizer
- NUM/SmartTube/Lampa/LazyMedia нет в эмуляторе — некому принять intent
- Поведение overlay на проекторе может отличаться от эмулятора

**Три слоя тестирования:**

| Слой | Где | Что покрывает |
|------|-----|---------------|
| Unit-тесты | Сервер (./gradlew test) | TmdbSearchProvider, IntentDispatcher, модели данных — ~60-70% логики |
| Интеграционные тесты на проекторе | Проектор через ADB | Полный цикл: ASSIST key → голос → карточки → запуск приложений |
| logcat мониторинг | Сервер → adb logcat | Ошибки, intents, crash, распознавание речи |

**Рабочий цикл разработчика:**
```
код → ./gradlew assembleDebug → adb install -r app-debug.apk → adb logcat | grep VoiceSearch
```
Переустановка через `adb install -r` — не засоряет устройство, данные сохраняются.

**Debug-сборка (buildType = debug):**
- Кнопка "Симулировать голос" в UI — подставляет тестовый запрос без микрофона
- Позволяет проверять полный flow (поиск → карточки → запуск) без реального голоса
- Подробный лог в logcat (тег VoiceSearch, уровень DEBUG)

**Release-сборка (buildType = release):**
- Debug-кнопка убрана
- Минимальный лог (только ошибки)
- ProGuard/R8 минификация

### Чеклист верификации

- [ ] APK собирается (`./gradlew assembleDebug`)
- [ ] Unit-тесты проходят (`./gradlew test`)
- [ ] Установка на проектор (`adb install -r`)
- [ ] AccessibilityService включён в настройках
- [ ] RECORD_AUDIO разрешён
- [ ] Нажатие ASSIST key → открывается SearchActivity
- [ ] Голосовой ввод работает (ru-RU)
- [ ] Текст отображается в строке поиска
- [ ] TMDB возвращает карточки с постерами
- [ ] Нажатие на карточку → выбор приложения → приложение открывается с запросом
- [ ] Повторный голосовой запрос из SearchActivity
- [ ] Кнопка Back закрывает SearchActivity
- [ ] Неустановленные приложения не показываются или серые
- [ ] Debug-кнопка "Симулировать голос" работает в debug-сборке
- [ ] Debug-кнопка отсутствует в release-сборке

---

## 8. Структура проекта (предварительная)

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/voicesearch/
│   │   ├── VoiceSearchApp.kt              (Application class)
│   │   ├── service/
│   │   │   └── AssistantService.kt        (AccessibilityService)
│   │   ├── ui/
│   │   │   └── SearchActivity.kt          (Leanback UI)
│   │   │   └── DetailActivity.kt          (Детальная карточка)
│   │   │   └── SearchAdapter.kt           (RecyclerView adapter)
│   │   ├── provider/
│   │   │   ├── SearchProvider.kt          (interface)
│   │   │   ├── ProviderType.kt            (enum)
│   │   │   └── TmdbSearchProvider.kt      (TMDB impl)
│   │   ├── dispatch/
│   │   │   └── IntentDispatcher.kt        (запуск приложений)
│   │   └── model/
│   │       ├── SearchResult.kt
│   │       └── TargetApp.kt
│   └── res/
│       ├── layout/activity_search.xml
│       ├── layout/activity_detail.xml
│       ├── layout/item_result_card.xml
│       ├── xml/accessibility_service_config.xml
│       └── values/themes.xml
├── build.gradle.kts
└── proguard-rules.pro
```

---

## 9. Расширение (вариант A — реверс API)

Когда появится реверс-инжиниринг API конкретного приложения (например, NUM):

1. Создаём `NumSearchProvider : SearchProvider`
2. `type = ProviderType.CARDS` (реальные карточки с результатами)
3. В UI автоматически появляется блок "NUM" с карточками
4. Нажатие на карточку из NUM → переход в NUM с конкретным результатом

Архитектура SearchProvider обеспечивает бесшовное добавление — без изменения UI или других компонентов.

---

## 10. Верификация package names

Имена пакетов целевых приложений нужно подтвердить на проекторе через ADB:

```
adb shell pm list packages | grep -i num
adb shell pm list packages | grep -i smart
adb shell pm list packages | grep -i lampa
adb shell pm list packages | grep -i lazy
```

Предварительные значения (из диалога):
- `ru.yourok.num` — NUM ✅ (подтверждён)
- `org.smarttube.stable` — SmartTube ✅ (подтверждён)
- `ru.yourok.lampa` — Lampa ⚠️ (в диалоге "не найден, но позже выяснилось что есть" — проверить точный package name)
- `com.laxymedia.deluxe` — LazyMediaDeluxe ⚠️ (не проверялся — проверить точный package name)

Верификация — первый шаг при подключении к проектору.
