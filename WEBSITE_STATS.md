# Website player statistics and application bridge

The existing private `plugins/MineNightAllInOne/perms/application-bridge.yml` enables the HTTPS bridge. Keep its secret outside source control and public website files. The matching private PHP backend accepts only authenticated snapshots.

Every minute the plugin samples online players on the server thread and reads saved vanilla `stats/*.json` and `usercache.json` asynchronously for offline players. It publishes UUID, username, player kills, deaths and playtime in seconds, in batches of at most 200. It does not modify statistics or count new gameplay independently. Online values replace older on-disk values for the same UUID.

The website exposes public leaderboards and searchable profiles from these snapshots. Victory Royales and Event Wins remain unavailable (Coming Soon). Snapshots do not contain Discord IDs, IP addresses, chat, inventory or moderation data. If the website is unavailable, the publisher retries on its next scheduled run and rate-limits warnings.

The application bridge also supports one-time in-game account verification and Owner-approved Helper/Mod/Admin/Media delivery. Higher administrative ranks cannot be downgraded by an application job. No Tebex commands are exposed through the public website.

After installing the updated JAR, perform a full restart. Verify that `/api/leaderboard` on the website contains real players and that the sync timestamp refreshes. Do not use a plugin hot-reloader.
