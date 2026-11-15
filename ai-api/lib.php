<?php
declare(strict_types=1);

/** Small, dependency-free helpers for the separately deployed AI proxy. */
function ai_b64url_encode(string $value): string
{
    return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
}

function ai_b64url_decode(string $value): ?string
{
    if ($value === '' || !preg_match('/^[A-Za-z0-9_-]+$/D', $value)) {
        return null;
    }
    $padding = (4 - (strlen($value) % 4)) % 4;
    $decoded = base64_decode(strtr($value, '-_', '+/') . str_repeat('=', $padding), true);
    return $decoded === false ? null : $decoded;
}

function ai_issue_token(string $subject, string $secret, string $issuer, int $ttl, ?int $now = null): string
{
    $now = $now ?? time();
    $header = ai_b64url_encode(json_encode(['alg' => 'HS256', 'typ' => 'JWT'], JSON_THROW_ON_ERROR));
    $payload = ai_b64url_encode(json_encode([
        'iss' => $issuer,
        'sub' => $subject,
        'iat' => $now,
        'nbf' => $now - 1,
        'exp' => $now + $ttl,
        'jti' => bin2hex(random_bytes(16)),
    ], JSON_THROW_ON_ERROR));
    $unsigned = $header . '.' . $payload;
    return $unsigned . '.' . ai_b64url_encode(hash_hmac('sha256', $unsigned, $secret, true));
}

/** Returns verified claims, or null. Supports only HS256 and the configured issuer. */
function ai_verify_token(string $token, string $secret, string $issuer, ?int $now = null): ?array
{
    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        return null;
    }
    [$headerPart, $payloadPart, $signaturePart] = $parts;
    $headerJson = ai_b64url_decode($headerPart);
    $payloadJson = ai_b64url_decode($payloadPart);
    $providedSignature = ai_b64url_decode($signaturePart);
    if ($headerJson === null || $payloadJson === null || $providedSignature === null) {
        return null;
    }
    try {
        $header = json_decode($headerJson, true, 16, JSON_THROW_ON_ERROR);
        $claims = json_decode($payloadJson, true, 32, JSON_THROW_ON_ERROR);
    } catch (Throwable $error) {
        return null;
    }
    if (!is_array($header) || ($header['alg'] ?? null) !== 'HS256' || ($header['typ'] ?? null) !== 'JWT'
        || !is_array($claims)) {
        return null;
    }
    $unsigned = $headerPart . '.' . $payloadPart;
    $expected = hash_hmac('sha256', $unsigned, $secret, true);
    if (!hash_equals($expected, $providedSignature)) {
        return null;
    }
    $now = $now ?? time();
    $issued = filter_var($claims['iat'] ?? null, FILTER_VALIDATE_INT);
    $notBefore = filter_var($claims['nbf'] ?? null, FILTER_VALIDATE_INT);
    $expires = filter_var($claims['exp'] ?? null, FILTER_VALIDATE_INT);
    $subject = $claims['sub'] ?? null;
    if (($claims['iss'] ?? null) !== $issuer || !is_string($subject) || $subject === ''
        || $issued === false || $notBefore === false || $expires === false
        || $issued > $now + 30 || $notBefore > $now + 30 || $expires <= $now
        || $expires <= $issued || ($expires - $issued) > 86_400) {
        return null;
    }
    return $claims;
}

/** Lock-protected, bounded file rate limiter. The file contains only hashed client identifiers. */
function ai_rate_limit(string $key, int $limit, int $windowSeconds, string $path, ?int $now = null): bool
{
    $now = $now ?? time();
    $handle = @fopen($path, 'c+');
    if ($handle === false) {
        return false;
    }
    @chmod($path, 0600);
    try {
        if (!flock($handle, LOCK_EX)) {
            return false;
        }
        rewind($handle);
        $raw = stream_get_contents($handle);
        if (!is_string($raw)) {
            return false;
        }
        if (trim($raw) === '') {
            $all = [];
        } else {
            try {
                $all = json_decode($raw, true, 32, JSON_THROW_ON_ERROR);
            } catch (Throwable $error) {
                return false;
            }
            if (!is_array($all)) {
                return false;
            }
        }
        $cutoff = $now - max(1, $windowSeconds);
        foreach ($all as $id => $times) {
            if (!is_array($times)) {
                unset($all[$id]);
                continue;
            }
            $all[$id] = array_values(array_filter($times, static function ($stamp) use ($cutoff) {
                return is_int($stamp) && $stamp > $cutoff;
            }));
            if ($all[$id] === []) {
                unset($all[$id]);
            }
        }
        $id = hash('sha256', $key);
        $times = $all[$id] ?? [];
        if (count($times) >= max(1, $limit)) {
            return false;
        }
        $times[] = $now;
        $all[$id] = $times;
        // Bound the file even if an attacker rotates source addresses.
        if (count($all) > 10_000) {
            uasort($all, static function ($a, $b) {
                return (max($a) ?? 0) <=> (max($b) ?? 0);
            });
            $all = array_slice($all, -8_000, null, true);
        }
        $json = json_encode($all, JSON_THROW_ON_ERROR);
        rewind($handle);
        if (!ftruncate($handle, 0)) {
            return false;
        }
        $written = fwrite($handle, $json);
        if ($written !== strlen($json) || !fflush($handle)) {
            return false;
        }
        return true;
    } catch (Throwable $error) {
        return false;
    } finally {
        flock($handle, LOCK_UN);
        fclose($handle);
    }
}

/** Extracts only rendered text from a Responses API result. */
function ai_responses_text(array $result): string
{
    if (isset($result['output_text']) && is_string($result['output_text'])) {
        return trim($result['output_text']);
    }
    $parts = [];
    foreach (($result['output'] ?? []) as $item) {
        if (!is_array($item)) {
            continue;
        }
        foreach (($item['content'] ?? []) as $content) {
            if (is_array($content) && ($content['type'] ?? '') === 'output_text'
                && isset($content['text']) && is_string($content['text'])) {
                $parts[] = $content['text'];
            }
        }
    }
    return trim(implode("\n", $parts));
}
