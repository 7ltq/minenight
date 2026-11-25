# MineNight website

The existing November 2026 homepage, Support, applications and statistics integration are preserved. Production uses PHP in `support-php`. Canonical sources are in `src`; generators use `scripts/templates`.

## Build

Install Python 3.13 and `requirements-dev.txt`, then run `python scripts/build.py`. Upload `dist` to the existing document root. `Build.ps1` also exports standalone HTML previews; live Support, votes, skins and statistics need the backend.

## Backend update

Use PHP 8.2+ with PDO SQLite, cURL, mbstring and GD. Update `support-php/private` files in the existing private `minenight-support` directory outside the document root. Preserve existing `config.php`, `application-bridge.key` and `data`. Schema additions retain tickets, verified links, applications and statistics. For new installations, fill private `config.example.php` and save it privately as `config.php`.

Keep `support-api.php`, `.htaccess` and `.user.ini` from `support-php/public` in the document root and retain the maintenance task. Never publish configuration, keys, databases or uploads.

The existing private bridge key must match `plugins/MineNightAllInOne/perms/application-bridge.yml`: `enabled: true`, HTTPS `endpoint: https://minenight.de` and a private secret of at least 40 characters. Replace the plugin JAR with version 1.8.13 and restart. Existing application and statistics routes remain compatible.

## Behavior

- Heads and full body renders use UUIDs through a restricted same-origin proxy. Known player UUIDs only, private six-hour PNG cache, browser caching, ETags and stale fallback. Provider: [MCHeads](https://mc-heads.net).
- Ban Appeals reuse Discord authentication, fresh staff verification and CSRF protection. Staff review another player's appeal with a reason. Acceptance freezes the UUID and exact ban identity. A separate Unban button queues the existing MineNight `/unban` handler. Changed bans are rejected; completion appears only after server acknowledgement.
- Staff identity, reason, date and delivery result are audited. Records survive ticket deletion. Authorized staff can read `/api/moderation-audit`, with optional `before` ID cursor. Plugin actions also append to private `moderation/moderation-audit.jsonl`.
- Latest Votes extends the existing Votifier listener. No browser action creates votes. Confirmed events persist until delivery and deduplicate by event identity. Name, platform and confirmation time are displayed; unavailable UUIDs stay absent. Securely configure the existing Votifier receiver and forwarding secrets. No incoming events means an empty list.
- English/German translations ship locally in `src/i18n-de.json`. Visitors do not contact a translation service. The preference uses local storage `minenight.language`. Player names, user messages, subjects, reasons and rank names remain unchanged.
- Media nametags follow the tablist name component, including its inherited pink fallback and explicit white or other configured colors. Scoreboard nametags use the nearest supported Minecraft team color. Tablist rendering, prefixes, glyphs and other ranks retain their existing behavior.

## Tests

Run `mvn clean verify` at the repository root. For website tests, install `requirements-dev.txt`, build `dist`, set `MINENIGHT_PHP` to a PHP executable and run `python scripts/test-support-php.py`. Extensions must be beside that runtime in `ext`. Browser tests currently use installed Google Chrome on Windows.

Tests use isolated SQLite and Discord fixtures under ignored `support-data/php-tests`. They do not message real Discord users, change live bans or create production votes. Browser tests cover all ten pages, language persistence/restoration, appeals, votes, skins and 1440/390/320-pixel layouts. Evidence goes to ignored `docs`.

Production deployment, real Discord login, Votifier forwarding and actual server unbans require deployed-environment validation. Existing legal placeholders remain for the operator to complete, including review/deletion periods for new records.
