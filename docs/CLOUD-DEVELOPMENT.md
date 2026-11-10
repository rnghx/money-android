# Полностью облачная разработка Money

Канонический репозиторий: `rnghx/money-android`, ветка `main`.
Телефон управляет работой через чат и проверяет установленное приложение.
Исходники, зависимости, сборки и инструменты находятся только в облаке.
Прочитайте `AGENTS.md` перед каждой задачей. Не удаляйте облачный checkout
как будто это папка на личном ПК: он нужен для следующих задач и кешей.

## Воспроизводимое окружение

Linux x86_64; Python 3.12+, curl, Git и GitHub CLI уже предоставляет базовая среда.
Из корня облачного checkout:

```sh
python3 scripts/cloud-environment/setup.py
source scripts/cloud-environment/env.sh
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
node --version
javac -version
bash gradlew --no-daemon help
```

Ознакомьтесь с лицензиями Android SDK и примите их в облачной среде.
Закреплены Temurin JDK 17.0.21+9, Node 22.23.4 и Android command-line tools 19.0.
Архивы проверяются по контрольным суммам официальных источников; для Android
используется SHA-1 из официального repository XML. Gradle 8.13 задаёт wrapper
проекта. Скрипт ничего не меняет в системных пакетах и не получает ключ подписи.

Инструменты сохраняются в `$HOME/.local/share/money-cloud`, загрузки и Gradle —
в `$HOME/.cache/money-cloud`. Сохраняйте оба каталога в снимке облачной среды.
`env.sh` нужно подключать в каждом новом shell. Он сохраняет настройки proxy,
добавляет их для Java и использует отдельное хранилище доверия с CA текущей
облачной среды, не отключая TLS. При смене CA пересоздайте файл
`$HOME/.local/share/money-cloud/cacerts`, повторно выполнив setup.py.
В обычной среде без Cloud proxy этот скрипт требует адаптации пути CA.

В Cloud Environment Onboarding добавьте репозиторий в reusable configuration,
затем Save draft и Publish для сохранения подготовленного снимка. Наличие
файлов в текущем executor само по себе не означает публикацию конфигурации.
Если инструменты onboarding недоступны, явно сообщите об этом и используйте
скрипты из Git для восстановления; не обещайте сохранение снимка.

## Доступы и сеть

GitHub CLI использует предоставленную платформой авторизацию. Никогда не
печатайте токены и не сохраняйте их в Git, shell-профиле или документации.
Проверки: `gh api user`, `gh api repos/rnghx/money-android`,
`git push --dry-run origin HEAD:main`, чтение списка workflows и runs.
Dry-run не доказывает прохождение правил защиты ветки при реальном push.
Для выпуска нужны Contents: write и Actions: write. Чтение Actions secrets
не требуется для выполнения выпуска; секрет остаётся на стороне GitHub.

Нужные HTTPS-сервисы: github.com, api.github.com, uploads.github.com,
release-assets.githubusercontent.com, objects.githubusercontent.com,
api.adoptium.net, nodejs.org, dl.google.com, services.gradle.org,
downloads.gradle.org, repo.maven.apache.org, plugins.gradle.org,
plugins-artifacts.gradle.org и maven.google.com; учитывайте redirect/CDN.
Исходный режим сети — unrestricted; при restricted сначала проверьте реальные
запросы и предложите точные дополнения через настройки среды.

Для безопасной проверки dispatch можно передать **заведомо отсутствующий** ref:
ожидается 422 `No ref found`, без создания run. Это подтверждает прохождение
проверки авторизации endpoint, но не заменяет успешный будущий выпуск.
Не запускайте единственный workflow `Android release` ради диагностики:
он сразу строит, подписывает и публикует приложение.

## Проверки и выпуск

Для JS-правок: `node --test tests/*.test.js` либо соответствующий файл.
Для проверки Android toolchain без APK и без ключей:
`bash gradlew --no-daemon compileDebugJavaWithJavac`.
Не повторяйте полный набор после документационных изменений.

Перед выпуском объедините готовые изменения, увеличьте versionCode/versionName,
сохраните `applicationId local.money`, прежний сертификат и данные пользователя.
Для изменений хранения нужны безопасные миграции и проверки на прежних данных;
не удаляйте хранилище и не требуйте переустановки.

Production-подпись выполняется только существующим workflow. Оригинальный ключ
хранится в GitHub Actions secret `ANDROID_KEYSTORE_BASE64`; не копируйте его
в облачный checkout и не создавайте замену. Ожидаемый SHA-256 сертификата указан
в `AGENTS.md`. Workflow проверяет его и удаляет временный keystore.

После разрешённого выпуска:

```sh
git push origin main
gh workflow run android-release.yml --repo rnghx/money-android --ref main
# Найдите новый run и сверьте head_sha с отправленным commit:
gh run list --repo rnghx/money-android --workflow android-release.yml --limit 5
gh run watch RUN_ID --repo rnghx/money-android --exit-status
gh release view --repo rnghx/money-android
```

Проверьте опубликованный `update.json`, versionCode/versionName, размер и SHA-256
скачанного в облако APK, его applicationId и исходную подпись. Только затем
сообщайте ссылку на готовую версию. Различайте отправленный код, успешный run
и опубликованный release. На телефоне: Настройки → Обновить приложение;
работа на устройстве считается проверенной только после реальной проверки.

## Кеши Actions — отдельное предложение

Текущий workflow не содержит `actions/cache` или `setup-gradle`, кеширование
setup-java/setup-node также не включено. Предлагается отдельно добавить
`gradle/actions/setup-gradle` с закреплённым SHA после setup-java, с кешем Gradle
user home и без кеширования keystore, APK или пользовательских данных.
Проверить совместимость с текущим wrapper и правилами доступа к кешу.
Это предложение, workflow при первоначальной настройке не изменяется.

## Исходный аудит 2026-11-10

HEAD приложения: `44cabc14b8f00865737d087297e0a4152df71ff3`, версия 0.13.0 (13).
Git clone, GitHub API, права push в metadata и Git dry-run работают.
Dispatch endpoint вернул ожидаемый 422 для отсутствующего ref; run не создан.
Чтение настроек Actions и списка secrets возвращает 403 integration permissions.
Последний существующий run `42862380731` завершился успешно, включая восстановление
подписи, сборку/lint и публикацию. Это историческое свидетельство, а не проверка
текущего значения секрета. Релиз v0.13.0 доступен, его APK совпадает с манифестом
по размеру и SHA-256. Первоначальная настройка не создаёт новый выпуск.

В текущей облачной среде установлены JDK/Node/command-line tools указанных выше
версий, platform-tools 37.0.1, Android platform 35 revision 2, build-tools 35.0.0.
Приняты SDK licenses. Под Node 22 прошли все 9 файлов JS-тестов; Gradle `help`
и `compileDebugJavaWithJavac` завершились успешно. Повторный setup.py использовал
готовые инструменты без загрузок. APK не собирался, ключ подписи не создавался.
Проверка скачанного существующего APK через apksigner и aapt подтвердила
исходный сертификат и `local.money`, версию 0.13.0 (13).

Runtime сообщает running, актуальные observations и enforced unrestricted policy.
Недостающих сетевых разрешений в проведённых проверках не обнаружено.
В этом чате доступны только runtime status/diagnostic инструменты Cloud Environment;
инструменты Onboarding для изменения draft и публикации snapshot не предоставлены.
Поэтому подготовка executor выполнена, но reusable configuration и её snapshot
не опубликованы этой настройкой. Для завершения откройте на телефоне Cloud
Environment Onboarding → Setup, добавьте `rnghx/money-android` и сохраните/опубликуйте
подготовленную среду через Save draft / Publish. Не вводите ключ подписи в чат
или настройки среды: он уже обслуживается существующим workflow на GitHub.
