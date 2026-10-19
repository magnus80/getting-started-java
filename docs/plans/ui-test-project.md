# План развития учебного проекта UI-автотестов

## Назначение

Выполнить домашнее задание [docs/homework.md](../homework.md), вариант A (фазы 0–8): от минимального Gradle-проекта с одним тестом поиска на github.com до CI с отчётами, уведомлениями и review, сохраняя Java-стек.

В этом документе хранятся согласованные решения, статусы фаз, результаты проверок, ограничения и открытые вопросы этого репозитория. Правила работы агента — в [AGENTS.md](../../AGENTS.md). План демонстрационного проекта курса (`qa-guru/getting-started-java-ai`) сохранён как справка в [reference-course-plan.md](reference-course-plan.md); его статусы и результаты к этому репозиторию не относятся.

## Согласованные решения

- Вариант задания — A (полный путь, фазы 0–8).
- План курса — справочный материал; статусы ведутся только в этом файле.
- Gradle Wrapper 9.6.0, bin-дистрибутив, `distributionSha256Sum` из официального `gradle-9.6.0-bin.zip.sha256`.
- Java 21 через Gradle Java toolchain.
- Selenide 7.18.2, JUnit Jupiter 6.1.3 (Selenium 4.49.0 транзитивно). Версии без согласования не менять.
- Defaults: baseUrl=https://github.com, browser=chrome, browserSize=1920x1080, headless=false. Переопределение через `-DbaseUrl`, `-Dbrowser`, `-DbrowserSize`, `-Dheadless`; Gradle явно передаёт в JVM тестов только эти четыре свойства.
- TestBase: настройка `Configuration` в `@BeforeEach`, `closeWebDriver()` в `@AfterEach` — отдельная сессия браузера на тест. Тесты открывают относительный `/search`.

## Текущее состояние

Последняя сверка файлов и git: 2026-10-19 (фаза 1).

| Фаза | Статус |
|---|---|
| 0 — Baseline | завершена, см. ниже |
| 1 — TestBase и воспроизводимость | завершена, см. ниже |
| 2 — Ещё два теста | не начата |
| 3 — Page Object | не начата |
| 4 — Allure-отчёты | не начата |
| 5 — GitHub Actions | не начата |
| 6 — Telegram | не начата |
| 7 — Jenkins | не начата |
| 8 — Review | не начата |

## Фаза 0 — Baseline

Статус: завершена 2026-10-19 с зафиксированными ограничениями. Код, `build.gradle` и тесты не менялись.

### Исходное состояние репозитория

- Ветка `hw1`, HEAD `09be4d9` (= `master`). `origin` — `qa-guru/getting-started-java` (репозиторий курса, не форк).
- Tracked-код: `build.gradle`, `src/test/java/SearchTests.java` (default package), `.gitignore` (`.gradle/`, `build/`).
- `AGENTS.md`, `docs/homework.md` и план курса на момент сверки были только в индексе (staged), не в коммитах.
- Не под git: `gradle/wrapper/` (jar + properties, Gradle 9.6.0-bin), `gradlew`, `gradlew.bat`, `.idea/`. В `gradle-wrapper.properties` нет `distributionSha256Sum`.
- Тест `SearchTests.successfulSearchTest`: абсолютный `open("https://github.com/search")`, ввод `qa.guru` в `[aria-label='Search GitHub']`, ожидание текста `QA.GURU` в `[data-testid='results-list']`. TestBase, Page Object, параметров и CI нет.
- `build.gradle`: плагин `java-library`, без toolchain, без передачи системных свойств в JVM тестов.

### Фактические версии

Источник — вывод команд, а не предположения.

- Java: Temurin 21.0.7 (`java -version`); toolchain в сборке не задан — используется `JAVA_HOME`.
- Gradle: 9.6.0 (`./gradlew --version`). Глобального `gradle` в PATH нет.
- Selenide 7.18.2; Selenium 4.49.0 — все `org.seleniumhq.selenium:*` (транзитивно), CDP-модули v151–v153.
- JUnit Jupiter 6.1.3; `junit-platform-launcher` → 6.1.3 (через `junit-bom`).
- Браузер: Google Chrome 155.0.8059.80 (Windows).

### Проверка wrapper

- SHA-256 `gradle/wrapper/gradle-wrapper.jar` = `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7` — совпадает с официальным `gradle-9.6.0-wrapper.jar.sha256` (services.gradle.org).
- Официальный SHA-256 `gradle-9.6.0-bin.zip`: `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`. Кэшированный дистрибутив уже распакован, архив удалён — его хеш проверить нельзя.

### Запуск исходного теста

```bash
./gradlew --version --console=plain
./gradlew dependencies --configuration testRuntimeClasspath --console=plain
./gradlew test --tests SearchTests.successfulSearchTest --rerun-tasks --console=plain
```

Результат: BUILD SUCCESSFUL, exit code 0. JUnit XML `build/test-results/test/TEST-SearchTests.xml`: `tests=1 skipped=0 failures=0 errors=0`, время 5.8 с.

### Ограничения и открытые вопросы

- Один успешный запуск не доказывает стабильность: внешний github.com, возможен HTTP 429; данные `qa.guru` изменяемы.
- Предупреждение Selenium: CDP 155 не поддерживается, используется ближайшая 153. Тест не затронут; обновление стека — отдельное согласование.
- Wrapper не под git и создан вне зафиксированного процесса; добавить его штатно с `distributionSha256Sum` — задача фазы 1.
- `origin` указывает на репозиторий курса. До любого push нужен собственный форк и смена remote — решение пользователя.
- Проверено только на Windows 10, Chrome, headed-режим.

## Фаза 1 — TestBase и воспроизводимость

Статус: завершена 2026-10-19.

Изменённые файлы: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (добавлены в git), `build.gradle`, `src/test/java/TestBase.java` (новый), `src/test/java/SearchTests.java` (`extends TestBase`, `open("/search")`), этот план. Локаторы, данные и assertion теста не менялись.

- [x] Wrapper пересоздан штатной задачей: `./gradlew wrapper --gradle-version 9.6.0 --distribution-type bin --gradle-distribution-sha256-sum bbaeb2fe…529a01`. `gradle-wrapper.jar` SHA-256 `497c8c2a…a194a9c7` = официальный. Ранее неотслеживаемый wrapper перезаписан (jar идентичен, добавилась строка checksum).
- [x] Скачивание с проверкой checksum: `./gradlew --version` с пустым временным `GRADLE_USER_HOME` — дистрибутив скачан заново и принят wrapper'ом. Временный каталог удалён. Негативный сценарий (неверный checksum) не проверялся.
- [x] Toolchain: `./gradlew javaToolchains` — для языка 21 найден Temurin 21.0.7; внутри JVM тестов `java.version=21.0.7`.
- [x] Передача свойств подтверждена внутри JVM тестов временным диагностическим тестом (удалён, в коммит не входит):
  - defaults: `System.getProperty` и `Configuration` = `https://github.com`, `chrome`, `1920x1080`, `false`; окно 1920x1080; URL `https://github.com/search`;
  - `-DbaseUrl=https://www.github.com -Dbrowser=chrome -DbrowserSize=1366x768 -Dheadless=true`: значения дошли, окно 1366x768;
  - `-DbaseUrl=https://example.com`: открыт `https://example.com/search` — `open("/search")` использует `baseUrl`;
  - постороннее `-Dfoo=bar` в JVM тестов не попало (`null`);
  - до `open()` браузер не запущен (`hasWebDriverStarted=false`) — настройка в `@BeforeEach` браузер не стартует.
- [x] Закрытие браузера: выборка процессов во время прогона (`chromedriver.exe` и дочерние `chrome.exe`). Успешный прогон — во время теста 1 driver + 1 chrome, после — 0/0. Контролируемое падение (`QA.GURU-NO-SUCH-TEXT`, exit code 1, `failures=1`, причина — намеренный assertion, страница с результатами загружена, не HTTP 429) — во время теста 1/1, после — 0/0.
- [x] Assertion восстановлен, диагностика удалена. Финальные прогоны: `./gradlew test --rerun-tasks --console=plain` и `./gradlew test --rerun-tasks --console=plain -DbaseUrl=https://www.github.com -Dbrowser=chrome -DbrowserSize=1366x768 -Dheadless=true` — BUILD SUCCESSFUL, `tests=1 failures=0 errors=0 skipped=0`.

Ограничения:

- Одна из выборок процессов (первый прогон падения) не зафиксировала ни одного процесса даже во время теста — причина не установлена; выводы о закрытии браузера опираются на два повторных прогона с построчным выводом выборок.
- Выборка процессов дискретная: процесс, живший меньше интервала опроса (~0.3–1 с), мог быть не замечен.
- Предупреждение CDP 155 vs 153 сохраняется. Проверено только на Windows 10 и Chrome; другие браузеры не проверялись.
- `-Dbrowser` передаётся, но фактически проверено только значение `chrome`.

## Базовые команды проверки

```bash
./gradlew --version
./gradlew test --rerun-tasks --console=plain
./gradlew test -Dheadless=true -DbrowserSize=1366x768 --rerun-tasks --console=plain
git diff --check
```

## Критерий завершения фазы

1. Выполнен именно согласованный объём.
2. Проверки действительно запущены и результаты зафиксированы.
3. Временная диагностика удалена, ограничения и блокеры перечислены.
4. Этот план обновлён без превращения предположений в подтверждённые результаты.
5. Свои изменения закоммичены; чужой WIP не затронут.
6. Работа остановлена до подтверждения следующей фазы.
