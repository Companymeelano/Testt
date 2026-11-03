<?php
declare(strict_types=1);

require_once __DIR__ . '/../lib.php';

function check(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$secret = random_bytes(48); // Generated for this process; never persisted or embedded.
$now = time();
$issuer = 'local-test-issuer';
$token = ai_issue_token('local-test-subject', $secret, $issuer, 900, $now);
$claims = ai_verify_token($token, $secret, $issuer, $now + 10);
check(is_array($claims) && ($claims['sub'] ?? '') === 'local-test-subject', 'JWT round-trip failed');
check(ai_verify_token($token, $secret, $issuer, $now + 901) === null, 'Expired JWT was accepted');
check(ai_verify_token($token . 'x', $secret, $issuer, $now + 10) === null, 'Modified JWT was accepted');
check(ai_verify_token($token, random_bytes(48), $issuer, $now + 10) === null, 'JWT with another key was accepted');

$rateFile = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'meelano-ai-test-' . bin2hex(random_bytes(8)) . '.json';
try {
    check(ai_rate_limit('ephemeral-client', 2, 60, $rateFile, $now), 'First rate-limit event rejected');
    check(ai_rate_limit('ephemeral-client', 2, 60, $rateFile, $now + 1), 'Second rate-limit event rejected');
    check(!ai_rate_limit('ephemeral-client', 2, 60, $rateFile, $now + 2), 'Rate limit did not reject the third event');
} finally {
    if (is_file($rateFile)) {
        unlink($rateFile);
    }
}

$corruptFile = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'meelano-ai-test-corrupt-' . bin2hex(random_bytes(8)) . '.json';
try {
    file_put_contents($corruptFile, '{not-json');
    check(!ai_rate_limit('ephemeral-client', 2, 60, $corruptFile, $now), 'Corrupt rate-limit storage did not fail closed');
} finally {
    if (is_file($corruptFile)) {
        unlink($corruptFile);
    }
}

$text = ai_responses_text([
    'output' => [[
        'content' => [
            ['type' => 'refusal', 'refusal' => 'ignored'],
            ['type' => 'output_text', 'text' => 'سلام'],
            ['type' => 'output_text', 'text' => 'دنیا'],
        ],
    ]],
]);
check($text === "سلام\nدنیا", 'Responses text extraction failed');
check(ai_responses_text(['output_text' => 'آماده']) === 'آماده', 'output_text shortcut failed');

echo "AI API helper tests passed\n";
