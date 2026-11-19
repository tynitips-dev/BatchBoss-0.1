<?php
header('Content-Type: application/json');
header('Cache-Control: private, no-store');
// Xneelo may expose GeoIP as a server variable. Only consume proxy headers
// when the operator has configured a stripping/overwriting trusted proxy.
$code = $_SERVER['GEOIP_COUNTRY_CODE'] ?? '';
if (!$code && getenv('PADDLE_TRUSTED_COUNTRY_PROVIDER') === 'cloudflare') {
    $code = $_SERVER['HTTP_CF_IPCOUNTRY'] ?? '';
}
if (!$code && (getenv('VERCEL') === '1' || getenv('PADDLE_TRUSTED_COUNTRY_PROVIDER') === 'vercel')) {
    $code = $_SERVER['HTTP_X_VERCEL_IP_COUNTRY'] ?? '';
}
$code = strtoupper(trim($code));
echo json_encode(preg_match('/^[A-Z]{2}$/D', $code) && !in_array($code, ['XX','ZZ'], true)
    ? ['countryCode' => $code] : new stdClass());
