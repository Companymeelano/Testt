<?php
declare(strict_types=1);

require_once __DIR__ . '/../lib.php';
require_once __DIR__ . '/../providers.php';

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

check(ai_normalize_task('analysis') === 'analysis', 'Valid task was rejected');
check(ai_normalize_task('sql') === null, 'Unknown task was accepted');
check(ai_provider_plan('fast', '', ['openai', 'gemini', 'groq', 'xai']) === ['groq', 'gemini', 'openai'],
    'Fast provider fallback order is incorrect');
check(ai_provider_plan('analysis', '', ['openai', 'groq']) === ['openai', 'groq'],
    'Provider planner did not skip an unconfigured service');
check(ai_provider_plan('writing', 'openai, groq, invalid', ['gemini', 'groq', 'openai'])
    === ['openai', 'groq', 'gemini'], 'Private provider order override was not safely normalized');
check(ai_valid_model('gemini-2.5-flash') && ai_valid_model('meta/model-v1'), 'Valid model was rejected');
check(!ai_valid_model('../secret') && !ai_valid_model(''), 'Invalid model was accepted');
check(ai_valid_provider_key('placeholder-with-32-safe-characters'), 'A safe provider key shape was rejected');
check(!ai_valid_provider_key('too-short') && !ai_valid_provider_key("long-enough-but\r\nInjected: header"),
    'Unsafe provider key shape was accepted');
putenv('OPENAI_API_KEY=placeholder-openai-key-123456789');
putenv('GEMINI_API_KEY=short');
putenv('GROQ_API_KEY=placeholder-groq-key-123456789');
putenv('AI_MODEL_OPENAI=');
putenv('AI_MODEL_GEMINI=');
putenv('AI_MODEL_GROQ=');
putenv('AI_MODEL=');
putenv('AI_PROVIDER_ORDER=');
$configured = ai_configured_providers('fast');
check(array_column($configured, 'name') === ['groq', 'openai'],
    'Server environment provider configuration or task routing failed');
check($configured[0]['model'] === 'llama-3.3-70b-versatile', 'Default Groq model was not selected');
check(ai_gemini_text(['candidates' => [['content' => ['parts' => [
    ['text' => 'پاسخ'], ['inlineData' => ['data' => 'ignored']], ['text' => ' آماده'],
]]]]]) === 'پاسخ آماده', 'Gemini text extraction failed');
check(ai_chat_completion_text(['choices' => [['message' => ['content' => 'پاسخ Groq']]]]) === 'پاسخ Groq',
    'OpenAI-compatible text extraction failed');
check(ai_chat_completion_text(['choices' => [['message' => ['content' => [
    ['type' => 'text', 'text' => 'پاسخ'], ['type' => 'text', 'text' => ' آماده'],
]]]]]) === 'پاسخ آماده', 'OpenAI-compatible content-block extraction failed');

echo "AI API helper tests passed\n";
