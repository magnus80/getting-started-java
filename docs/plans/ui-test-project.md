# План развития учебного проекта UI-автотестов

## Назначение

Выполнить домашнее задание [docs/homework.md](../homework.md), вариант A (фазы 0–8): от минимального Gradle-проекта с одним тестом поиска на github.com до CI с отчётами, уведомлениями и review, сохраняя Java-стек.

В этом документе хранятся согласованные решения, статусы фаз, результаты проверок, ограничения и открытые вопросы этого репозитория. Правила работы агента — в [AGENTS.md](../../AGENTS.md). План демонстрационного проекта курса (`qa-guru/getting-started-java-ai`) сохранён как справка в [reference-course-plan.md](reference-course-plan.md); его статусы и результаты к этому репозиторию не относятся.

## Согласованные решения

- Вариант задания — A (полный путь, фазы 0–8).
- План курса — справочный материал; статусы ведутся только в этом файле.

## Текущее состояние

Последняя сверка файлов и git: 2026-10-19 (фаза 0).

| Фаза | Статус |
|---|---|
| 0 — Baseline | завершена, см. ниже |
| 1 — TestBase и воспроизводимость | не начата |
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

## Базовые команды проверки

```bash
./gradlew --version
./gradlew test --rerun-tasks --console=plain
git diff --check
```

## Критерий завершения фазы

1. Выполнен именно согласованный объём.
2. Проверки действительно запущены и результаты зафиксированы.
3. Временная диагностика удалена, ограничения и блокеры перечислены.
4. Этот план обновлён без превращения предположений в подтверждённые результаты.
5. Свои изменения закоммичены; чужой WIP не затронут.
6. Работа остановлена до подтверждения следующей фазы.
