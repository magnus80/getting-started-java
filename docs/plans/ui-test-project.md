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
- Перед вводом запроса тест ждёт загрузки приложения поиска: `react-app[app-name='blackbird-search']` с классом `loaded` (решение фазы 2, вариант 1). Это ожидание предусловия, не ретрай.
- Дополнительные сценарии фазы 2 (согласованы пользователем 2026-10-19): переход из выдачи `selenide` в репозиторий `selenide/selenide`; empty state для `stars:<0`.
- Page Object (согласовано 2026-10-19): packages `tests` (SearchTests, TestBase) и `pages` (SearchPage, SearchResultsPage, RepositoryPage); fluent API; проверки только в `should…`-методах страниц; `private final SelenideElement`/`ElementsCollection`-поля; методы называются действиями пользователя; `SearchPage` — поле теста; ожидание загрузки приложения поиска — в `SearchPage.open()`. BasePage, фабрик и компонентов нет.

## Текущее состояние

Последняя сверка файлов и git: 2026-10-19 (фаза 2).

| Фаза | Статус |
|---|---|
| 0 — Baseline | завершена, см. ниже |
| 1 — TestBase и воспроизводимость | завершена, см. ниже |
| 2 — Ещё два теста | реализована; есть нестабильность внешнего GitHub, см. ниже |
| 3 — Page Object | реализована; нестабильность внешнего GitHub сохраняется, см. ниже |
| 4 — Allure-отчёты | завершена, см. ниже |
| 5 — GitHub Actions | завершена, см. ниже |
| 6 — Telegram | завершена, см. ниже |
| 7 — Jenkins | реализована; сборки на Jenkins не проверены |
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

## Фаза 2 — Ещё два теста

Статус: реализована 2026-10-19; финальные прогоны набора зелёные, но наблюдается внешняя нестабильность загрузки страницы поиска (причина не установлена).

Изменённые файлы: `src/test/java/SearchTests.java`, этот план. Модификатор `public` у класса `SearchTests` убран вне агента (пользователь/IDE) во время фазы — оставлен как есть, JUnit Jupiter допускает package-private.

### Этап A — исследование в реальном UI (Playwright, Chrome), до кода

- [x] `selenide`: в `[data-testid='results-list']` ровно одна ссылка `a[href='/selenide/selenide']` — поиск по href, не по позиции; клик ведёт на `https://github.com/selenide/selenide`; `[data-testid='repo-name-wrapper']` = `selenide`.
- [x] `stars:<0`: «0 results», `h3` «Your search did not match any repositories», `results-list` отсутствует. Семантика: `stars:<1` → 73.2M репозиториев, у первых в выдаче 0 stars — квалификатор числовой, `stars:<0` логически невозможен. Счётчик `resolved-count-label` показывает «...» — не используется; классы заголовка хешированные — поиск по тексту.
- [x] Сценарии согласованы пользователем.

### Этап B — реализация и проверка

- [x] `openRepositoryFromSearchResultsTest`: URL содержит `/selenide/selenide`, имя репозитория — `exactText("selenide")`. Отличие от согласованного эскиза: `text` → `exactText` — мутацией показано, что `text("selenide")` + `urlContaining` пропускали переход в `selenide/selenide-appium`. Точный `url(...)` не используется: ломал бы переопределение `baseUrl=https://www.github.com` (редирект).
- [x] `noResultsForImpossibleStarsRangeTest`: заголовок empty state видим, `results-list` не существует.
- [x] Мутации (временные, откачены): чужой репозиторий → `exactText` падает (`Actual value: text="selenide-appium"`); `stars:<1` → заголовок empty state не найден; `cssClass("loaded-nope")` → ожидание загрузки падает.
- [x] Финальные прогоны после добавления ожидания загрузки: `openRepositoryFromSearchResultsTest` и `noResultsForImpossibleStarsRangeTest` отдельно — passed; весь набор с defaults (`./gradlew test --rerun-tasks --console=plain`) — 3/3; headless (`-Dheadless=true -DbrowserSize=1366x768`) — 3/3. `successfulSearchTest` отдельно — **failed** из-за незагрузившегося приложения поиска (см. ниже); повторно не перезапускался.

### Нестабильность: приложение поиска иногда не загружается

- Наблюдалось 3 раза за сессию: дважды до добавления ожидания (`openRepository…` отдельно и в headless-наборе) — Enter после ввода не запускал поиск, страница оставалась на `/search` с введённым текстом; один раз после — упало ожидание `cssClass("loaded")` на `successfulSearchTest`, до ввода текста.
- Во всех случаях в page source у `<react-app app-name="blackbird-search">` нет `class="loaded"` и `data-catalyst` (на рабочих страницах есть); в одном случае не применились и стили. Не HTTP 429 (совпадения «429» в HTML — хеши/SVG), не ошибка локатора.
- Временный замер (12 открытий `/search`, headed и headless; тест удалён): в норме приложение загружено уже к возврату `open()`, `loaded` через 40–100 мс — гонки ввода с гидрацией в норме нет.
- Причина незагрузки (сеть, ресурсы `githubassets.com`, холодный старт браузера) **не установлена**. Ожидание не устраняет сбой, а делает падение диагностируемым («приложение не загрузилось» вместо «ссылка не найдена»). Ретраи не добавлялись.

## Фаза 3 — Page Object

Статус: реализована 2026-10-19; семантика сохранена; один headless-прогон упал на навигации к `/search` (внешний фактор, см. ниже).

Изменённые файлы: `src/test/java/tests/SearchTests.java`, `src/test/java/tests/TestBase.java` (перенос через `git mv`, добавлен только `package tests;`), `src/test/java/pages/SearchPage.java`, `SearchResultsPage.java`, `RepositoryPage.java` (новые), этот план.

- [x] Стиль и структура согласованы до реализации (включая отдельный `RepositoryPage` и `SearchPage` полем теста).
- [x] `SearchPage.open()` вызывает `Selenide.open("/search")` явно: метод экземпляра `open()` перекрывает статически импортированный `open(String)`.
- [x] Сравнение с `eae129f`: локаторы, тестовые данные, assertions и их порядок те же; вычисляемые из параметров значения дают прежние строки (`/selenide/selenide`, `exactText("selenide")`). Отличие: ссылка на репозиторий ищется цепочкой `resultsList.$("a[href=…]")` вместо одного CSS-селектора — тот же поиск потомка.
- [x] `./gradlew testClasses --rerun-tasks` — BUILD SUCCESSFUL.
- [x] Каждый тест отдельно (`--tests "tests.SearchTests.<method>"`) — passed; весь набор с defaults — 3/3.
- [ ] Весь набор headless (`-Dheadless=true -DbrowserSize=1366x768`) — **2/3**: `openRepositoryFromSearchResultsTest` упал через 31 с на `driver.get("https://github.com/search")` в `SearchPage.open()` — `TimeoutException: Timed out receiving message from renderer`. Скриншот не создан. До логики страниц выполнение не дошло; повторно не запускался.

Открытая задача (решение пользователя — отложить): диагностика нестабильности загрузки `/search` — логи консоли браузера и сетевые запросы при падении.

Ограничения: четвёртый за сессию сбой загрузки `/search` — теперь зависание навигации, а не незагрузившееся приложение. Причина не установлена; вероятно, то же семейство внешних сбоев, но связь не доказана. Предупреждение CDP 155 vs 153 сохраняется — влияние на renderer timeout не исключено и не проверено.

## Фаза 4 — Allure-отчёты

Статус: завершена 2026-10-19 (этап A — согласование, этап B — реализация и проверка).

### Факты (проверено 2026-10-19 по реестрам, не по памяти)

- Локально: `allure` 2.35.1 (Scoop; в bucket доступна только 2.39.0), Node v24.20.0, npm 11.3.0.
- Maven Central: `allure-bom`/`allure-jupiter`/`allure-selenide` 3.0.0 (2026-09-11); `allure-junit5` — только линейка 2.x (2.35.5), для JUnit 6 не используется; `allure-commandline` 2.46.1 (2026-09-03), zip/tgz с `.sha1`; `aspectjweaver` 1.9.25.
- `allure-junit-platform` 3.0.0 зависит от `junit-jupiter-api`/`junit-platform-launcher` 6.1.0 (runtime) — та же мажорная линия, что JUnit 6.1.3 проекта.
- Release notes allure-java 3.0.0: Java 17 baseline (байткод `allure-java-commons` — class version 61), «Support Selenide 7.17.0», сборка обновлялась до Selenide 7.18.1; в проекте 7.18.2.
- npm `allure`: 3.21.0 опубликован 2026-10-16 (моложе 7 дней), 3.20.1 — 2026-10-06.

### Согласовано

- [x] Генератор — Allure Report 2 CLI 2.46.1 (Java, без Node.js в проекте): zip с Maven Central со сверкой checksum, распаковка вне репозитория. Scoop-установка 2.35.1 не меняется.
- [x] Адаптеры — `allure-jupiter` + `allure-selenide` 3.0.0 через `allure-bom` 3.0.0.
- [x] Шаги — `@Step` на методах страниц + `-javaagent:aspectjweaver` 1.9.25 в JVM тестов; плюс технические шаги Selenide от `AllureSelenide`. Weaving подтверждать по фактическим результатам.
- [x] Вложения при падении — Screenshot и Page source (дефолты `AllureSelenide`); listener регистрируется в `TestBase`.
- [x] Результаты — `build/allure-results` (очищается перед задачей `test`), отчёт — `build/allure-report`; оба в `build/`, в git не попадают.
- [x] Совместимость формата результатов allure-java 3 с генератором Report 2 подтвердить реальной генерацией.

### Реализация

Изменённые файлы: `build.gradle` (`allure-bom` + `allure-jupiter` + `allure-selenide`; конфигурация `aspectjAgent` с `aspectjweaver` 1.9.25 → `-javaagent` через `jvmArgumentProviders`; `doFirst` удаляет `build/allure-results`), `src/test/resources/allure.properties` (новый), `tests/TestBase.java` (`SelenideLogger.addListener("allure", new AllureSelenide())` в `@BeforeEach` — повторная регистрация с тем же именем заменяет listener), `@Step` на всех публичных методах `pages.*`, этот план.

CLI: `allure-commandline-2.46.1.zip` с Maven Central → `~/tools/allure-2.46.1` (вне репозитория, PATH не менялся; вызов по полному пути). SHA-1 `a33315cd…5b55` = `.sha1` Maven Central; SHA-256 `d25c519b…eb252f` = digest ассета `allure-2.46.1.zip` в GitHub Release. `allure --version` → 2.46.1; флаги взяты из фактического `--help`: `generate [-c] [-o dir] <results>`, `open [-h host] [-p port] <report>`.

### Проверено (2026-10-19, Windows 10, Chrome 155)

- [x] Разрешённые версии (`dependencies`): allure-* 3.0.0; транзитивные JUnit 6.1.0 подняты до 6.1.3; `aspectjweaver` 1.9.25.
- [x] Очистка: маркер `stale-marker-result.json` в `build/allure-results` удалён задачей `test`; после прогона — 3 `*-result.json` + 4 контейнера, после прогона с падением — плюс 2 вложения, чужих файлов нет.
- [x] Weaving `@Step` работает: в `*-result.json` пользовательские шаги с подставленными параметрами («Search for "qa.guru"», «Open repository selenide/selenide from search results»), внутри — технические шаги Selenide (`$("open")`, `set value`, `should have`, `webdriver create/close`).
- [x] Успешный прогон `./gradlew test --rerun-tasks --console=plain` — 3/3; `allure generate build/allure-results -c -o build/allure-report` → `summary.json` passed=3, total=3. Отчёт открыт (`allure open … -h 127.0.0.1 -p 45624`) и просмотрен в браузере (Playwright): дерево suites, шаги с параметрами.
- [x] Контролируемое падение (`QA.GURU-NO-SUCH-TEXT` в `successfulSearchTest`): BUILD FAILED; в результате status=failed, причина — `Element should have text "QA.GURU-NO-SUCH-TEXT"`; упал шаг «Check that search results contain …» и вложенный Selenide-шаг; на нём Screenshot (PNG, 157 КБ) и Page source (HTML, 249 КБ), файлы существуют. Отчёт падения собран в отдельный `build/allure-report-failure` и просмотрен в браузере: 1 failed / 2 passed, причина, failed step, вложения. Каталог затем удалён.
- [x] Assertion восстановлен; финальный прогон — 3/3, отчёт пересобран: passed=3, total=3. Серверы `allure open` остановлены.

Ограничения:

- При пересборке отчёта в тот же каталог уже запущенный `allure open` отдавал старые данные (3 passed + 404 на тест) — кэш браузера или сервера; после пересборки отчёт нужно открывать заново.
- Версия CLI не закреплена в репозитории (ручная установка в `~/tools`); в CI — тот же zip с проверкой checksum (фаза 5).
- Отчёт просмотрен агентом через Playwright; визуальная приёмка пользователем не зафиксирована.
- Отрицательная проверка «без агента `@Step` не появляется» не выполнялась — weaving подтверждён наличием шагов.

## Фаза 5 — GitHub Actions

Статус: завершена 2026-10-19 — workflow проверен в реальном GitHub Actions (успех, Pages, три контролируемых падения).

### Факты (2026-10-19)

- `gh` CLI локально не установлен; `magnus80/getting-started-java` — 404 через публичный API (GitHub-логин может отличаться от git-имени).
- Версии и SHA actions получены через releases API и `git ls-remote` (для annotated-тегов — `^{}`); выбираются версии старше 7 дней.
- Chrome for Testing: последние для milestone 151/152/153 — 151.0.7922.138 / 152.0.7977.82 / 153.0.8010.52; Stable — 155.0.8059.84. Selenium 4.49.0 поддерживает CDP v151–153.

### Согласовано

- [x] Репозиторий: форк `qa-guru/getting-started-java`, создаёт пользователь и сообщает URL; добавляется отдельным remote, `origin` не меняется; push — только с разрешения пользователя.
- [x] События: `push` (любая ветка) + `workflow_dispatch`; `pull_request`/`pull_request_target` не используются.
- [x] Публикация: артефакты (retention 14 дней) всегда; GitHub Pages — только из default branch при сгенерированном отчёте, в том числе при упавших тестах (Pages публичны, вложения упавших прогонов тоже).
- [x] Inputs `workflow_dispatch`: только `browserSize` (regex `^[0-9]{3,4}x[0-9]{3,4}$`); browser=chrome, baseUrl=https://github.com, headless=true фиксированы.
- [x] Runner `ubuntu-24.04`, `timeout-minutes: 30`, Temurin 21.
- [x] Actions по SHA: `actions/checkout` v7.0.1 `3d3c42e5aac5ba805825da76410c181273ba90b1`; `actions/setup-java` v6.0.1 `de7274f081f381c8f8158605e0321c36c376e2e6` (v6.1.0 от 2026-10-14 моложе 7 дней); `gradle/actions/setup-gradle` v6.4.0 `3f5f9adaf7d9fecd50b5935e54106014257a94e6`; `browser-actions/setup-chrome` v2.2.0 `48ad923757ca74d66703209fe939badbdf80f2f4`; `actions/upload-artifact` v7.0.2 `cf430e030ddbb5b0abf93d22962f4752f3646cd9`; `actions/upload-pages-artifact` v5.0.0 `fc324d3547104276b827a68afc52ff2a11cc49c9`; `actions/deploy-pages` v5.0.1 `368f82528645a54fb793d4d04e342629a3f51346`.
- [x] Браузер: Chrome for Testing 153.0.8010.52 + ChromeDriver той же версии; workflow проверяет совпадение версий. Пути передаются в тесты через Gradle только если заданы (локальное поведение не меняется).
- [x] Allure CLI 2.46.1 — zip с Maven Central, SHA-256 `d25c519bbde940dc953cff8901130ce18b73013dc19a8f8b90b8b4b1c5eb252f`.
- [x] Permissions: `contents: read` на workflow; `pages: write` + `id-token: write` только у job публикации. Secrets не используются.
- [x] Compile (`testClasses`) отдельно от `test`; после падения тестов загружаются JUnit XML, `allure-results` и отчёт, job остаётся failure; без результатов генерация явно падает. Без `continue-on-error`, `|| true`, ретраев. Concurrency — один запуск, текущий не отменяется.
- [x] `--no-sandbox` заранее не добавляется; при падении Chrome на runner — сначала причина, затем отдельное согласование.

### Перед реализацией

- [x] Форк: https://github.com/magnus80/getting-started-java — public, fork of `qa-guru/getting-started-java`, default branch `master`, `has_pages=true`. Добавлен remote `fork`; `origin` не менялся.
- [x] Ветка для Pages: пользователь сделал `hw1` default branch форка (подтверждено API: `default_branch=hw1`) и добавил правило `hw1` в environment `github-pages`.
- [x] Actions в форке включены пользователем (подтверждено фактическим запуском).
- Push выполняет пользователь: у агента нет учётных данных GitHub (`git push` → `Invalid username or token`); `gh` не установлен. Логи и артефакты через API без токена недоступны — диагностика через публичные check-run annotations и скачанные пользователем артефакты.

### Реализация

Файлы: `.github/workflows/ui-tests.yml` (новый), `build.gradle` (`selenide.browserBinary` и `webdriver.chrome.driver` передаются в JVM тестов только если заданы), этот план.

Проверено локально (2026-10-19, Windows 10):

- [x] `actionlint` 1.7.12 (release-бинарь для Windows, SHA-256 сверен с `actionlint_1.7.12_checksums.txt`, во временном каталоге вне проекта) — 0 ошибок. ShellCheck локально отсутствует — shell-блоки им не проверены.
- [x] Передача свойств (временный init-скрипт вне проекта, задача `test` остановлена до запуска тестов): без свойств — только `baseUrl`, `browser`, `browserSize`, `headless` с defaults; с `-Dselenide.browserBinary`/`-Dwebdriver.chrome.driver` и путём с пробелом — оба значения доходят до `Test.systemProperties`, пробел сохраняется.
- [x] Имена свойств подтверждены по байткоду: `selenide.browserBinary` — в `SelenideConfig` (selenide-core 7.18.2), `webdriver.chrome.driver` — в `ChromeDriverService` (selenium-chrome-driver 4.49.0).
- [x] `setup-chrome` v2.2.0: inputs `chrome-version`, `install-chromedriver`, `install-dependencies` и outputs `chrome-path`, `chromedriver-path` взяты из `action.yml` на закреплённом SHA.
- [ ] UI-тесты локально после правки `build.gradle` не перезапускались (изменение только добавляет необязательные свойства; defaults подтверждены init-скриптом).

### Реальные запуски в GitHub Actions

- **39298963165** (`hw1`, `45f41fc`, push): ui-tests — failure, deploy-report — failure.
  - Успешны: checkout, Java 21, Gradle, CfT 153.0.8010.52 + ChromeDriver (совпадение версий проверено шагом), Allure CLI (`sha256sum --check --strict` прошёл, версия 2.46.1), compile.
  - `Run UI tests` — failure: JUnit XML из артефакта `junit-and-gradle-report` (скачан пользователем, распакован агентом во временный каталог): `tests=3 failures=3`, во всех — `SessionNotCreatedException: Chrome instance exited`, binary — CfT из `/opt/hostedtoolcache`, без `--no-sandbox`.
  - Логика после падения сработала как задумано: загружены `allure-results`, `junit-and-gradle-report`, `allure-report`, `github-pages`; отчёт сгенерирован.
  - deploy-report: `Branch "hw1" is not allowed to deploy to github-pages due to environment protection rules` — environment разрешал только `master`; исправлено пользователем в настройках.
- **39300547939** (временная ветка `ci-check/chrome-sandbox`, `7b67797`): диагностика, job намеренно останавливается до тестов.
  - `kernel.apparmor_restrict_unprivileged_userns=1`, `unprivileged_userns_clone=1`; `chrome_sandbox` есть, без SUID (`runner:runner`, 0755).
  - CfT как есть: exit 134, `FATAL … zygote_host_impl_linux.cc:129] No usable sandbox! If you are running on Ubuntu 23.10+ … disabled unprivileged user namespaces with AppArmor …`.
  - С `--no-sandbox`: exit 0, DOM получен. После `sudo sysctl -w kernel.apparmor_restrict_unprivileged_userns=0` с включённой песочницей: exit 0, DOM получен.
  - Причина установлена: AppArmor на ubuntu-24.04 запрещает непривилегированные user namespaces, нужные песочнице Chrome; у CfT в нестандартном пути нет AppArmor-профиля.

### Решение по песочнице Chrome (согласовано 2026-10-19)

- [x] Шаг workflow `sudo sysctl -w kernel.apparmor_restrict_unprivileged_userns=0` (с проверкой применения) перед тестами. Песочница Chrome остаётся включённой; ослабляется ограничение ядра только на ephemeral runner. `--no-sandbox` и SUID-helper не используются. Локальные запуски не затрагиваются. Это компромисс конкретного runner — не переносить в Jenkins без отдельной проверки.
- [x] Временная ветка `ci-check/chrome-sandbox` удалена в форке (пользователь; подтверждено `git ls-remote`) и локально.

### Проверка после исправления

- **39301206218** (`hw1`, `e328355`, push): ui-tests и deploy-report — success. Артефакты: `allure-results`, `junit-and-gradle-report`, `allure-report`, `github-pages`. https://magnus80.github.io/getting-started-java/ — HTTP 200; `widgets/summary.json`: passed=3, total=3; `data/suites.json`: все три теста passed.
- Контролируемые падения (временные ветки от `hw1`, по одному изменению; локально заранее проверено, что compile-fail даёт `';' expected`, а `--tests NoSuchTest` — `No tests found for given includes` и пустой `allure-results`):
  - **39301635750** `ci-check/assertion-fail` (`QA.GURU-NO-SUCH-TEXT`): `Run UI tests` — failure; загружены `allure-results` (231 КБ против 4.4 КБ у зелёного прогона — вложения), `junit-and-gradle-report`, `allure-report`; Pages пропущен (не default branch). JUnit XML (скачан пользователем): `tests=3 failures=1`, причина — `Element should have text "QA.GURU-NO-SUCH-TEXT"`, остальные два теста passed; не HTTP 429.
  - **39301635898** `ci-check/compile-fail` (пропущена `;`): первая попытка — **cancelled** без jobs (см. ограничение concurrency); после Re-run (attempt 2) — `Compile tests` failure с аннотацией `SearchTests.java:28 ';' expected`; тесты, загрузки, генерация и Pages пропущены; артефактов нет.
  - **39301635107** `ci-check/no-results` (`--tests NoSuchTest`): `Run UI tests` — failure; `Upload Allure results` — warning «No files were found»; `Generate Allure report` — failure с `::error::No Allure test results`; отчёт и Pages пропущены; загружен только `junit-and-gradle-report`.
- [x] Временные ветки `ci-check/assertion-fail`, `ci-check/compile-fail`, `ci-check/no-results` удалены в форке (пользователь; подтверждено `git ls-remote`: осталась только `hw1`) и локально.

Ограничения:

- Concurrency: одна глобальная группа без отмены текущего запуска; GitHub держит в группе не больше одного ожидающего запуска — при почти одновременных push более ранний ожидающий запуск отменяется (фактически случилось с `compile-fail`). Коммит может остаться непроверенным; альтернатива — группа на ветку (параллельные запуски, выше риск HTTP 429) — открытый вопрос.
- Агент не может сам push/скачивать артефакты/читать логи (нет токена): часть проверок опирается на артефакты и лог, предоставленные пользователем.
- `--no-sandbox` не используется; `sysctl` ослабляет ограничение AppArmor только на ephemeral runner.
- Визуальная приёмка отчёта на Pages пользователем не зафиксирована (проверено: HTTP 200 и JSON).
- Внешний github.com: нестабильность загрузки `/search` и HTTP 429 по-прежнему возможны; ретраев нет.

## Фаза 6 — Telegram

Статус: завершена 2026-10-19 — проверена локально (Windows 10, Python 3.13.13) и в реальном GitHub Actions с реальной отправкой.

### Согласовано

- [x] Источник уведомлений — GitHub Actions; Jenkins (фаза 7) переиспользует тот же скрипт.
- [x] Уведомлять обо всех исходах (success, failure тестов, ошибка сборки/шага до тестов, отмена) для всех веток и ручных запусков.
- [x] Secrets: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` (те же имена — для Jenkins Credentials). Значения в чат не передаются; пользователь заводит их в Settings → Secrets.
- [x] Нет secrets → `::warning::`, отправка пропускается, exit 0. Ошибка Telegram API (HTTP ≠ 200, `ok != true`, timeout) → `::error::`, exit 1.
- [x] Язык — Python 3, только stdlib (новый язык в проекте согласован пользователем); runner ubuntu-24.04 — Python 3.12.3, локально — 3.13.13.
- [x] Отдельный job `notify-telegram` (`needs: ui-tests`, `if: always()`): ошибка уведомления не меняет статус `ui-tests`, без `continue-on-error`; токен доступен только этому job.
- [x] Статистика — по `<testcase>` всех JUnit XML (`failure` → failed, `error` → broken, `skipped` → skipped), без атрибутов-счётчиков suite и без дублирования вложенных `testsuite`; не из файлов Allure-отчёта. Статусы различают: PASSED, FAILED, BUILD ERROR (тесты не запускались), NO RESULTS (нет/битый XML), NO TESTS (0 testcase), CANCELLED.
- [x] Сообщение — plain text: проект, CI, branch, commit, статус, passed/failed/broken/skipped/total, ссылка на запуск. HTTP: POST `sendMessage`, timeout 10 с; токен и chat ID не логируются, в текстах ошибок заменяются на `***`.
- [x] Проверка без реальной отправки — fixtures + локальный mock HTTP; реальная отправка — только после отдельного разрешения пользователя.

### Реализация

Файлы: [ci/telegram_notify.py](../../ci/telegram_notify.py), [ci/test_telegram_notify.py](../../ci/test_telegram_notify.py), `.github/workflows/ui-tests.yml` (outputs `compile-outcome`/`tests-outcome` у `ui-tests`; job `notify-telegram`), `.gitignore` (`__pycache__/`), этот план.

- Скрипт: аргументы `--results-dir`, `--job-status`, `--compile-outcome`, `--tests-outcome`, `--project`, `--ci`, `--branch`, `--commit`, `--run-url`, `--timeout` (10 с), `--dry-run`; secrets — из env `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`; `TELEGRAM_API_URL` переопределяет адрес API (для mock в тестах). Пустой outcome трактуется как `skipped`. Приоритет статусов: CANCELLED → BUILD ERROR (compile ≠ success, XML не читается — старые результаты не попадают в сообщение) → NO RESULTS → NO TESTS → FAILED → TEST STEP FAILED (Gradle упал при зелёном XML) → PASSED. Вывод в UTF-8 (`reconfigure`), чтобы консоль cp1251 на Windows-агенте не ломала печать.
- `notify-telegram`: `needs: ui-tests`, `if: always()`, `timeout-minutes: 5`; checkout (`persist-credentials: false`); JUnit XML скачивается только при `compile-outcome == success` через `actions/download-artifact` v8.0.2 `9000827ccba6bdab643e8b6fd33ac0654aef8333` (2026-10-07) с `pattern` + `merge-multiple: true` в `build/`. По исходникам v8.0.2: `name` при отсутствии артефакта бросает `Artifact '…' not found`, `pattern` без совпадений — не ошибка; «нет результатов» разбирает скрипт. Все значения передаются через `env` и процитированные аргументы; токен доступен только этому job.

### Проверено локально (2026-10-19)

- [x] `python -m unittest test_telegram_notify` (из `ci/`, `PYTHONDONTWRITEBYTECODE=1`) — 30 тестов OK: подсчёт passed/failed/broken/skipped; несколько XML; вложенные `testsuite` без двойного счёта; атрибуты-счётчики suite игнорируются; 0 testcase; нет каталога; пустой каталог; битый XML; чужой root; PASSED с проверкой пути, `chat_id` и текста запроса; FAILED (уведомление при упавших тестах — exit 0); broken; BUILD ERROR при наличии старых XML; ошибка до compile; CANCELLED; NO RESULTS; NO TESTS; TEST STEP FAILED; нет обоих secrets / нет chat ID (warning, запросов нет); HTTP 401, HTTP 502, HTTP 200 + `ok=false`, не-JSON, JSON-массив; timeout (0.5 с, завершение < 1.9 с); connection refused; редактирование токена и chat ID в описании ошибки; `--dry-run` без запроса. В каждом сценарии проверено отсутствие токена и chat ID в stdout/stderr.
- [x] Мутации (на копии во временном каталоге): без `redact` падает тест редактирования; без учёта `broken` в FAILED падает тест broken.
- [x] Найден и исправлен шум тестов: на Windows mock-сервер получал `ConnectionAbortedError` после таймаута клиента — перехват расширен до `ConnectionError`.
- [x] Реальный JUnit XML: `./gradlew test --rerun-tasks -Dheadless=true` — 3/3; `--dry-run` на `build/test-results/test` → `UI tests: PASSED`, `passed 3, failed 0, broken 0, skipped 0, total 3`.
- [x] Команда шага `Send Telegram notification` выполнена локально в bash с теми же env без secrets → warning, exit 0; отмена с пустыми outcome → `CANCELLED`.
- [x] Структура артефакта `junit-and-gradle-report` (по фактически скачанному zip из CI): `test-results/test/TEST-*.xml` → после скачивания в `build/` путь `build/test-results/test` совпадает с `--results-dir`.
- [x] `actionlint` 1.7.12 — 0 ошибок; ShellCheck локально отсутствует.

### Проверено в реальном GitHub Actions (2026-10-19)

- [x] **39303783004** attempt 1 (`hw1`, `834d982`, secrets ещё не заданы): `ui-tests`, `notify-telegram`, `deploy-report` — success; аннотация `notify-telegram`: `warning | TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not set, notification skipped`. Подтверждены регистрация job, скачивание артефакта через `pattern`, `python3` на runner.
- [x] Secrets `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID` добавлены пользователем; реальная отправка разрешена пользователем. Значения агенту не передавались.
- [x] **39303783004** attempt 2 (Re-run пользователем): все три job — success, у `notify-telegram` аннотаций нет. Фактическое сообщение (прислано пользователем) совпало с ожидаемым построчно: `UI tests: PASSED` / проект / `CI: GitHub Actions` / `Branch: hw1` / `Commit: 834d98261bb3` / `Tests: passed 3, failed 0, broken 0, skipped 0, total 3` / ссылка на run.
- [x] **39306919324** `ci-check/notify-assertion` (`QA.GURU-NO-SUCH-TEXT`): `ui-tests` failure, `notify-telegram` success, deploy skipped; сообщение `UI tests: FAILED`, `Tests: passed 2, failed 1, broken 0, skipped 0, total 3`.
- [x] **39306919806** `ci-check/notify-compile` (пропущена `;`): `ui-tests` failure, `notify-telegram` success (артефакт не скачивался — compile ≠ success); сообщение `UI tests: BUILD ERROR`, `Details: tests did not run`, строки `Tests:` нет.
- [x] **39310385637** `ci-check/notify-bad-token` (assertion + во временной ветке к токену дописан символ; настоящий secret не менялся): `ui-tests` failure (исходное падение сохранено), `notify-telegram` failure на шаге `Send Telegram notification`, аннотация `Telegram API returned HTTP 401: Unauthorized`; сообщение не пришло. Токена в аннотации нет.
- [x] Временные ветки `ci-check/notify-*` удалены локально.
- [ ] Временные ветки `ci-check/notify-assertion`, `ci-check/notify-compile`, `ci-check/notify-bad-token` удалить в форке (пользователь).

Ограничения:

- Полные логи job без токена недоступны агенту: проверены статусы job/шагов, публичные аннотации и фактические сообщения, присланные пользователем.
- Публичный API (60 запросов/час) был исчерпан частым опросом агента — опрос замедлен до 1 запроса в 90 с; ни один запуск это не затронуло.
- Сценарии NO RESULTS / NO TESTS / CANCELLED / timeout в CI не воспроизводились — покрыты unit-тестами.

## Фаза 7 — Jenkins

Статус: реализована 2026-10-19; реальные сборки на Jenkins не выполнялись.

- Адрес Jenkins из ДЗ (сообщён пользователем 2026-10-19): https://jenkins.qa.guru/. Анонимный GET `/` — HTTP 200; `/login` — 302, сервер nginx 1.24.0 (Ubuntu).

### Факты (анонимное чтение, 2026-10-19)

- Jenkins 2.581 (заголовок `X-Jenkins`); анонимное чтение разрешено (`whoAmI`: anonymous), `pluginManager/api/json` — 403.
- Агенты для Java: `java-jdk21-jenkins-agent-1…5`, labels `java`, `java-jdk21`, `docker`, по 1 executor; ещё warm-pool, js-node24 и python-python314 агенты. Всего 19 executors.
- 79 job (47 Pipeline, 32 Freestyle).
- Плагины — эвристика по ответу `/plugin/<id>/` (400 — установлен, 404 — нет), не список из менеджера плагинов: есть workflow-aggregator, junit, allure-jenkins-plugin, credentials-binding, plain-credentials, timestamper, git, ws-cleanup; нет htmlpublisher, docker-workflow, ansicolor, pipeline-utility-steps.
- Неизвестно: Chrome/ChromeDriver/python3 на агентах, sudo, `/dev/shm`, user namespaces (агент, вероятно, контейнер — решение `sysctl` из GitHub Actions не переносится без проверки), версия Allure-плагина и его CLI.

### Согласовано

- [x] У пользователя есть логин и право создавать job; job и Credentials создаёт пользователь, агент — без учётных данных Jenkins.
- [x] Отчёт: Allure Report 2 CLI 2.46.1 с проверкой SHA-256 (как в GitHub Actions) + `archiveArtifacts` (results, отчёт); JUnit — шаг `junit`. Allure-плагин не используется.
- [x] Статусы: упавшие тесты → UNSTABLE; ошибка компиляции, нет результатов, ошибка генерации отчёта/инфраструктуры → FAILURE.
- [x] Разведка агента временной Pipeline-job `magnus80-agent-inventory` (создана пользователем; только чтение; вывод прислан пользователем):
  - `java-jdk21-jenkins-agent-4`: Debian 13 (trixie) в контейнере, ядро хоста 6.17 Ubuntu 24.04; **uid 0 (root)**; Temurin 21.0.12; есть `curl`, `unzip`, `apt-get`; **нет** Chrome/Chromium/ChromeDriver, `python3`, `sudo`, `sysctl`, `docker`; `/dev/shm` 64 МБ (Selenide по умолчанию передаёт `--disable-dev-shm-usage`); `max_user_namespaces=255794`.
  - CfT 153.0.8010.52 + ChromeDriver той же версии, скачанные в каталог workspace: `ldd` — недостающих библиотек нет; без `--no-sandbox` — `Running as root without --no-sandbox is not supported`, DOM не получен; с `--no-sandbox` — DOM получен (ошибки GPU/EGL в headless без GPU не влияют). Коды выхода в выводе относились к `tail`, а не к Chrome — вывод сделан по тексту ошибки и наличию DOM.
  - [ ] Стадия на агенте `python` (версия Python, curl) — вывода нет, запрошен у пользователя.
- [x] Chrome: CfT 153.0.8010.52 + ChromeDriver скачиваются в workspace сборки (как в GitHub Actions); пакеты на общий агент не ставятся.
- [x] Песочница (решение пользователя): `--no-sandbox` только в Jenkinsfile через `-Dchromeoptions.args=--no-sandbox`; `build.gradle` передаёт `chromeoptions.args` в JVM тестов только если задано. Причина — сборка в контейнере идёт от root, Chrome запрещает песочницу для root (не AppArmor, решение `sysctl` из GitHub Actions неприменимо). Принятый риск: отключена песочница рендерера; смягчение — изолированный контейнер агента, открывается только github.com. Локально и в GitHub Actions песочница остаётся. Имя свойства подтверждено по байткоду: `ChromeDriverFactory` (selenide-core 7.18.2) читает `chromeoptions.args`.
- [x] Telegram: на `java-jdk21` нет `python3` — уведомление отдельной стадией на агенте label `python`, JUnit XML через `stash`/`unstash`; Credentials `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID` (secret text) создаёт пользователь.

### Реализация

Файлы: [Jenkinsfile](../../Jenkinsfile) (новый), `build.gradle` (`chromeoptions.args` передаётся в JVM тестов только если задано), этот план.

- Pipeline from SCM; `agent none`; родительская стадия `UI tests` на `java-jdk21` с вложенными стадиями в одном workspace: Validate parameters (+ stash скрипта уведомления) → Set up Chrome (CfT + ChromeDriver в `$WORKSPACE/.ci-tools`, проверка версий) → Install Allure CLI (SHA-256 `--check --strict`, проверка версии) → Compile tests (`./gradlew testClasses`) → Run UI tests (`./gradlew test` с `-Dheadless=true`, путями к CfT/ChromeDriver и `-Dchromeoptions.args=--no-sandbox`; `returnStatus`; stash JUnit XML; `junit`) → Generate Allure report (без `*-result.json` — ошибка). `post always` стадии — `archiveArtifacts` (allure-results, allure-report, JUnit XML, Gradle HTML).
- Статусы: упавшие тесты → `junit` выставляет UNSTABLE; Gradle упал, а в JUnit нет падений → `error` (FAILURE); нет JUnit XML → `junit` падает (FAILURE); compile/инфраструктура/нет Allure-результатов → FAILURE. Ретраев, `catchError` для маскировки и `|| true` нет.
- Параметры: `BROWSER_SIZE` (проверка `^[0-9]{3,4}x[0-9]{3,4}$`), ID Credentials Telegram (по умолчанию `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`); для первой сборки, когда параметры ещё не зарегистрированы, — значения по умолчанию через `?:`. `disableConcurrentBuilds`, `timeout 30 min`, `buildDiscarder`.
- Уведомление — pipeline `post always` в `node('python')`: unstash скрипта и (если тесты запускались) JUnit XML; `withCredentials` + `sh` с одинарными кавычками — секреты не интерполируются в Groovy; `--ci Jenkins`, `--project $JOB_NAME`, `GIT_BRANCH`/`GIT_COMMIT`/`BUILD_URL`. Нет Credentials (`Could not find credentials entry`) → предупреждение и пропуск; ошибка скрипта/нет `python3`/другая ошибка → `currentBuild.result = 'FAILURE'` (результат Jenkins может только ухудшаться — исходный UNSTABLE/FAILURE не скрывается; FAILURE не перекрывает ABORTED).

### Проверено локально / статически (2026-10-19)

- [x] Валидатор Jenkins `POST /pipeline-model-converter/validate` на https://jenkins.qa.guru (анонимно, с crumb): `Jenkinsfile successfully validated.`; контрольная испорченная копия (`stagez`) отклонена с `Undefined section "stagez" @ line 26` — валидатор действительно проверяет. Проверяется только декларативная структура, не выполнение и не одобрение sandbox-методов.
- [x] Передача свойств (временный init-скрипт вне проекта): без `-Dchromeoptions.args` — свойство не передаётся; с ним — `chromeoptions.args:--no-sandbox` в `Test.systemProperties`.

### Не проверено

- [ ] Реальные сборки на jenkins.qa.guru: успех; падение assertion (UNSTABLE); ошибка компиляции (FAILURE); нет результатов (FAILURE); уведомления.
- [ ] Агент `python`: наличие `python3` (вывод второй стадии разведки не получен).
- [ ] Credentials: на общем Jenkins глобальные Credentials доступны всем, кто может настраивать job, — скоуп и риск согласовать с пользователем.

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
