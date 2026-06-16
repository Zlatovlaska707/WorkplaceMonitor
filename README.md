# WorkplaceMonitor Android IoT

Android-приложение для мониторинга качества рабочего места и состояния работника.  
Собирает телеметрию с датчиков устройства, отправляет ее по MQTT (в том числе в Yandex IoT Core) и поддерживает интеграцию с внешними сервисами уведомлений.


## Возможности

- Сбор данных с датчиков:
  - акселерометр (`X`, `Y`, `Z`);
  - освещенность (`L`);
  - шагомер (`S`);
  - микрофон peak (`M`).
- Гибкая конфигурация в `Settings`:
  - параметры MQTT;
  - включение/выключение датчиков;
  - интервал публикации;
  - режим уведомлений (`MODE`).
- Безопасное MQTT-подключение по TLS.
- Динамический payload: отключенные датчики не отправляются.
- Превью отправляемой строки на главном экране.
- Интеграция с внешним alerting-слоем (боты/скрипты/Cloud Functions).

## Технологический стек

| Компонент | Технология |
|---|---|
| Мобильное приложение | Android (Java) |
| UI | AndroidX AppCompat + Material |
| Протокол передачи | MQTT |
| MQTT клиент | Eclipse Paho (`mqttv3`, `android.service`) |
| Сенсоры | `SensorManager` / `SensorEventListener` |
| Аудиопик | `MediaRecorder.getMaxAmplitude()` |
| Хранение настроек | `SharedPreferences` |
| TLS | `SSLSocketFactory` + `assets/rootca.crt` |

## Требования

- Android 9.0+ (API 28+);
- Интернет-соединение;
- MQTT-брокер (например, Yandex IoT Core);
- Желательные датчики:
  - `TYPE_ACCELEROMETER`
  - `TYPE_LIGHT`
  - `TYPE_STEP_COUNTER`
  - микрофон

Приложение корректно работает при отсутствии части датчиков: неактивные/недоступные поля исключаются из payload.

## Структура проекта

```text
WorkplaceMonitor/
├─ app/                                              # Android-модуль приложения
│  ├─ build.gradle                                   # зависимости и параметры сборки модуля
│  └─ src/main/
│     ├─ AndroidManifest.xml                         # permissions, activity/service declarations
│     ├─ assets/
│     │  └─ rootca.crt                               # CA-сертификат для TLS MQTT
│     ├─ java/com/example/accelerometer/
│     │  ├─ MainActivity.java                        # сбор датчиков, payload, MQTT publish, главный экран
│     │  ├─ MqttSettings.java                        # экран настроек и сохранение конфигурации
│     │  ├─ SplashScreen.java                        # стартовый экран
│     │  └─ AboutUs.java                             # информационный экран
│     └─ res/
│        ├─ layout/
│        │  ├─ activity_main.xml                     # UI главного экрана (Sensor Data + Payload Preview)
│        │  ├─ activity_mqtt_settings.xml            # UI экрана настроек
│        │  ├─ activity_splash_screen.xml            # UI splash-экрана
│        │  └─ activity_about_us.xml                 # UI экрана About
│        └─ xml/
│           └─ network_security_config.xml           # доверенные домены/политика network security
├─ NodeREDDashbaord/
│  └─ flows.json                                     # пример Node-RED flow для приема/визуализации MQTT данных
├─ docs/
│  ├─ USER_GUIDE_RU.md                               # подробная пользовательская документация
│  └─ TECHNICAL_DOCUMENTATION_RU.md                  # подробная техническая документация
└─ README.md                                         # краткая обзорная документация проекта
```

## Быстрый старт

1. Клонируйте репозиторий:
   ```bash
   git clone <your-repo-url>
   cd WorkplaceMonitor
   ```
2. Откройте проект в Android Studio.
3. Дождитесь `Gradle Sync`.
4. Соберите APK:
   - через меню `Build > Build APK(s)`, или
   - командой:
     ```bash
     ./gradlew assembleDebug
     ```
5. Установите приложение на устройство.
6. Заполните настройки подключения и нажмите `Save settings`.
7. На главном экране нажмите `Start`.

## Настройка Yandex IoT Core

На экране `Settings` укажите:

- `Broker`: `ssl://mqtt.cloud.yandex.net`
- `Port`: `8883`
- `Topic`: `$devices/<device-id>/events`
- `Username`: MQTT username
- `Password`: MQTT password

После сохранения настроек:
1. Нажмите `Start`.
2. Убедитесь, что в приложении есть `Payload Preview`.
3. Проверьте входящие сообщения у подписчика.

## Настройка бота (кратко)

Приложение не отправляет сообщения в мессенджер напрямую.  
Оно публикует MQTT-телеметрию, включая режим уведомлений:
- `MODE:1` — уведомление на каждое сообщение;
- `MODE:0` — уведомление только при предупреждениях.

Рекомендуемая схема:
1. MQTT -> 2. Cloud Function/Python Worker -> 3. Bot API (VK/Telegram/др.).

## Формат payload

Пример:
```text
X:0.03,Y:9.81,Z:0.12,L:325.40,S:21,M:38,MODE:0
```

Поля неактивных датчиков не отправляются.

## Документация

- Подробная пользовательская: `docs/USER_GUIDE_RU.md`
- Подробная техническая: `docs/TECHNICAL_DOCUMENTATION_RU.md`

## Типовые проблемы

- `UnknownHostException` — неверный `Broker` (опечатка/не тот домен).
- `S (Steps): N/A` — нет шагомера или не выдано `ACTIVITY_RECOGNITION`.
- `Microphone start failed` — нет `RECORD_AUDIO` или микрофон занят.


