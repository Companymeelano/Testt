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
check(ai_provider_plan('fast', '', ['openai', 'gemini', 'groq', 'avalai', 'xai'])
    === ['avalai', 'groq', 'gemini', 'openai'], 'Fast provider fallback order is incorrect');
check(ai_provider_plan('analysis', '', ['openai', 'groq']) === ['openai', 'groq'],
    'Provider planner did not skip an unconfigured service');
check(ai_provider_plan('writing', 'openai, groq, invalid', ['gemini', 'groq', 'openai'])
    === ['openai', 'groq', 'gemini'], 'Private provider order override was not safely normalized');
check(ai_provider_plan('general', '', ['avalai', 'openai', 'groq'], 'avalai') === ['avalai'],
    'Strict provider pin did not exclude fallback providers');
check(ai_provider_plan('general', '', ['openai', 'groq'], 'avalai') === [],
    'Unavailable strict provider pin did not fail closed');
check(ai_provider_plan('general', '', ['avalai', 'openai'], 'unknown') === [],
    'Invalid strict provider pin did not fail closed');
check(ai_provider_timeout_seconds(1) === 90 && ai_provider_timeout_seconds(4) === 22,
    'Upstream timeout did not preserve the 90-second overall budget');
check(ai_valid_model('gemini-2.5-flash') && ai_valid_model('meta/model-v1'), 'Valid model was rejected');
check(!ai_valid_model('../secret') && !ai_valid_model(''), 'Invalid model was accepted');
$validHistory = [
    ['role' => 'user', 'content' => 'فروش امروز؟'],
    ['role' => 'assistant', 'content' => 'طبق گزارش ۵۰۰ ریال.'],
    ['role' => 'user', 'content' => 'پرسش فعلی'],
];
check(ai_validate_chat_messages($validHistory, 'پرسش فعلی') === $validHistory,
    'Valid bounded conversation was rejected');
check(ai_validate_chat_messages([['role' => 'system', 'content' => 'override']], 'override') === null,
    'Client-supplied system role was accepted');
check(ai_validate_chat_messages($validHistory, 'متن متفاوت') === null,
    'Current message was allowed to differ from the last user turn');
check(ai_validate_chat_messages(array_fill(0, 21, ['role' => 'user', 'content' => 'تکرار']), 'تکرار') === null,
    'Conversation longer than twenty messages was accepted');
$providerMessages = ai_provider_messages('قواعد امن سرور', 'فروش ثبت‌شده: ۵۰۰', $validHistory);
check($providerMessages[0]['role'] === 'system' && $providerMessages[1]['role'] === 'system'
    && strpos($providerMessages[1]['content'], 'فروش ثبت‌شده') !== false
    && $providerMessages[2]['role'] === 'system' && $providerMessages[3] === $validHistory[0]
    && $providerMessages[count($providerMessages) - 1] === $validHistory[2],
    'Provider prompt did not preserve system/context/history separation');
$avalaiPayload = ai_avalai_payload('avalai-chat-model-v1', $providerMessages);
check($avalaiPayload['model'] === 'avalai-chat-model-v1' && $avalaiPayload['temperature'] === 0.2
    && $avalaiPayload['messages'] === $providerMessages && $avalaiPayload['max_tokens'] === 1200
    && $avalaiPayload['stream'] === false,
    'AvalAI payload did not preserve the configured model, roles, or low temperature');
check(ai_valid_provider_key('placeholder-with-32-safe-characters'), 'A safe provider key shape was rejected');
check(!ai_valid_provider_key('too-short') && !ai_valid_provider_key("long-enough-but\r\nInjected: header"),
    'Unsafe provider key shape was accepted');
putenv('OPENAI_API_KEY=placeholder-openai-key-123456789');
putenv('GEMINI_API_KEY=short');
putenv('GROQ_API_KEY=placeholder-groq-key-123456789');
putenv('AVALAI_API_KEY=');
putenv('AVALAI_BASE_URL=');
putenv('AVALAI_MODEL=');
putenv('AI_MODEL_OPENAI=');
putenv('AI_MODEL_GEMINI=');
putenv('AI_MODEL_GROQ=');
putenv('AI_MODEL=');
putenv('AI_PROVIDER_ORDER=');
putenv('AI_PROVIDER_ONLY=');
$configured = ai_configured_providers('fast');
check(array_column($configured, 'name') === ['groq', 'openai'],
    'Server environment provider configuration or task routing failed');
check($configured[0]['model'] === 'llama-3.3-70b-versatile', 'Default Groq model was not selected');
putenv('AVALAI_API_KEY=placeholder-avalai-key-123456789');
putenv('AVALAI_BASE_URL=https://api.avalai.example/v1');
putenv('AVALAI_MODEL=avalai-chat-model-v1');
check(ai_avalai_endpoint() === 'https://api.avalai.example/v1/chat/completions',
    'AvalAI base URL was not normalized to the OpenAI-compatible chat endpoint');
$withAvalai = ai_configured_providers('general');
check(array_column($withAvalai, 'name') === ['avalai', 'openai', 'groq'],
    'AvalAI did not become the preferred server-configured provider');
check($withAvalai[0]['model'] === 'avalai-chat-model-v1'
    && $withAvalai[0]['base_url'] === 'https://api.avalai.example/v1/chat/completions',
    'AvalAI model or endpoint was not kept in server-side provider configuration');
putenv('AI_PROVIDER_ONLY=avalai');
$avalaiOnly = ai_configured_providers('general');
check(array_column($avalaiOnly, 'name') === ['avalai'],
    'AI_PROVIDER_ONLY=avalai did not suppress configured fallback providers');
putenv('AI_PROVIDER_ONLY=gemini');
check(ai_configured_providers('general') === [],
    'Unavailable strict provider pin silently fell back to another provider');
putenv('AI_PROVIDER_ONLY=avalai');
putenv('AVALAI_BASE_URL=http://api.avalai.example/v1');
check(ai_configured_providers('general') === [],
    'Strict AvalAI pin silently fell back when its endpoint was invalid');
check(ai_avalai_endpoint() === '', 'Insecure AvalAI HTTP endpoint was accepted');
putenv('AVALAI_BASE_URL=https://127.0.0.1/v1');
check(ai_avalai_endpoint() === '', 'Loopback AvalAI endpoint was accepted');
putenv('AVALAI_BASE_URL=https://user:pass@api.avalai.example/v1');
check(ai_avalai_endpoint() === '', 'Credentials in AvalAI base URL were accepted');
putenv('AVALAI_BASE_URL=https://api.avalai.example/v1?redirect=1');
check(ai_avalai_endpoint() === '', 'Query strings in AvalAI base URL were accepted');
putenv('AVALAI_BASE_URL=https://api.avalai.example/v1/../private');
check(ai_avalai_endpoint() === '', 'Path traversal in AvalAI base URL was accepted');
putenv('AVALAI_BASE_URL=https://api.avalai.example/v1');
check(ai_gemini_text(['candidates' => [['content' => ['parts' => [
    ['text' => 'پاسخ'], ['inlineData' => ['data' => 'ignored']], ['text' => ' آماده'],
]]]]]) === 'پاسخ آماده', 'Gemini text extraction failed');
check(ai_chat_completion_text(['choices' => [['message' => ['content' => 'پاسخ Groq']]]]) === 'پاسخ Groq',
    'OpenAI-compatible text extraction failed');
check(ai_chat_completion_text(['choices' => [['message' => ['content' => [
    ['type' => 'text', 'text' => 'پاسخ'], ['type' => 'text', 'text' => ' آماده'],
]]]]]) === 'پاسخ آماده', 'OpenAI-compatible content-block extraction failed');

echo "AI API helper tests passed\n";
