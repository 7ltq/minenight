<?php
declare(strict_types=1);
// Configure as a Plesk PHP scheduled task, never as a public HTTP endpoint.
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
require __DIR__ . '/app.php';
