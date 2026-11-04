<?php
declare(strict_types=1);

/**
 * Server-only provider adapters. Provider credentials are read from the hosting environment,
 * never from request data. Requests use fixed HTTPS endpoints, bounded JSON responses, and no
 * redirects. This file deliberately has no key-value logging or provider-response logging.
 */
final class AiProviderFailure extends RuntimeException
{
    public function __construct(public readonly int $statusCode = 0)
    {
        parent::__construct('AI provider request failed');
    }
}

function ai_normalize_task(string $task): ?string
{
    $task = strtolower(trim($task));
    return in_array($task, ['analysis', 'fast', 'writing', 'general'], true) ? $task : null;
}

/** The normal failover order is task-aware; a private host setting can change its preference. */
function ai_provider_order(string $task, string $configuredOrder = ''): array
{
    $task = ai_normalize_task($task) ?? 'general';
    $defaults = [
        'analysis' => ['gemini', 'openai', 'groq'],
        'fast' => ['groq', 'gemini', 'openai'],
        'writing' => ['gemini', 'openai', 'groq'],
        'general' => ['openai', 'gemini', 'groq'],
    ];
    $base = $defaults[$task];
    $allowed = ['openai', 'gemini', 'groq'];
    $custom = [];
    foreach (explode(',', strtolower($configuredOrder)) as $name) {
        $name = trim($name);
        if (in_array($name, $allowed, true) && !in_array($name, $custom, true)) {
            $custom[] = $name;
        }
    }
    return $custom === [] ? $base : array_merge($custom, array_values(array_diff($base, $custom)));
}

/** Pure planner kept separate so routing and fallback order can be tested without credentials. */
function ai_provider_plan(string $task, string $configuredOrder, array $available): array
{
    $available = array_values(array_unique(array_filter($available, static function ($name) {
        return is_string($name) && in_array($name, ['openai', 'gemini', 'groq'], true);
    })));
    return array_values(array_filter(ai_provider_order($task, $configuredOrder), static function ($name) use ($available) {
        return in_array($name, $available, true);
    }));
}

function ai_provider_env(string $name): string
{
    $value = getenv($name);
    if (!is_string($value) || strlen($value) > 4096) {
        return '';
    }
    return trim($value);
}

function ai_valid_provider_key(string $key): bool
{
    // Match bearer-token characters (including standard base64 padding) and reject header controls.
    return preg_match('/^[A-Za-z0-9._~+\/-]{16,4096}=*$/D', $key) === 1;
}

function ai_provider_model(string $provider): string
{
    $environment = [
        'openai' => 'AI_MODEL_OPENAI',
        'gemini' => 'AI_MODEL_GEMINI',
        'groq' => 'AI_MODEL_GROQ',
    ];
    $defaults = [
        'openai' => 'gpt-4o-mini',
        'gemini' => 'gemini-2.5-flash',
        'groq' => 'llama-3.3-70b-versatile',
    ];
    if (!isset($environment[$provider])) {
        return '';
    }
    $model = ai_provider_env($environment[$provider]);
    // Keep the previous OpenAI variable working during a safe deployment migration.
    if ($model === '' && $provider === 'openai') {
        $model = ai_provider_env('AI_MODEL');
    }
    if ($model === '') {
        $model = $defaults[$provider];
    }
    return ai_valid_model($model) ? $model : '';
}

function ai_valid_model(string $model): bool
{
    return preg_match('/^[A-Za-z0-9][A-Za-z0-9._:\/-]{0,119}$/D', $model) === 1;
}

/** Returns only providers with both a server-side key and a valid configured model. */
function ai_configured_providers(string $task): array
{
    $keys = [
        'openai' => ai_provider_env('OPENAI_API_KEY'),
        'gemini' => ai_provider_env('GEMINI_API_KEY'),
        'groq' => ai_provider_env('GROQ_API_KEY'),
    ];
    $available = [];
    foreach ($keys as $provider => $key) {
        if (ai_valid_provider_key($key) && ai_provider_model($provider) !== '') {
            $available[] = $provider;
        }
    }
    $plan = ai_provider_plan($task, ai_provider_env('AI_PROVIDER_ORDER'), $available);
    $configured = [];
    foreach ($plan as $provider) {
        $configured[] = [
            'name' => $provider,
            'key' => $keys[$provider],
            'model' => ai_provider_model($provider),
        ];
    }
    return $configured;
}

/** POST a bounded JSON request to a fixed provider endpoint. No redirects are followed. */
function ai_provider_post_json(string $url, array $headers, array $payload): array
{
    if (!function_exists('curl_init')) {
        throw new AiProviderFailure();
    }
    try {
        $body = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    } catch (Throwable $error) {
        throw new AiProviderFailure();
    }
    $handle = curl_init($url);
    if ($handle === false) {
        throw new AiProviderFailure();
    }
    $response = '';
    $tooLarge = false;
    $httpHeaders = array_merge([
        'Content-Type: application/json',
        'Accept: application/json',
    ], $headers);
    try {
        $configured = curl_setopt_array($handle, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_HTTPHEADER => $httpHeaders,
            CURLOPT_RETURNTRANSFER => false,
            CURLOPT_WRITEFUNCTION => static function ($curl, string $chunk) use (&$response, &$tooLarge): int {
                if (strlen($response) + strlen($chunk) > 1_048_576) {
                    $tooLarge = true;
                    return 0;
                }
                $response .= $chunk;
                return strlen($chunk);
            },
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_CONNECTTIMEOUT => 6,
            CURLOPT_TIMEOUT => 18,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
        ]);
        if (!$configured) {
            throw new AiProviderFailure();
        }
        $executed = curl_exec($handle);
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    } catch (Throwable $error) {
        if ($error instanceof AiProviderFailure) {
            throw $error;
        }
        throw new AiProviderFailure();
    } finally {
        curl_close($handle);
    }
    if ($executed !== true || $tooLarge || $status < 200 || $status >= 300
        || strlen($response) > 1_048_576) {
        throw new AiProviderFailure($status);
    }
    try {
        $decoded = json_decode($response, true, 64, JSON_THROW_ON_ERROR);
    } catch (Throwable $error) {
        throw new AiProviderFailure($status);
    }
    if (!is_array($decoded)) {
        throw new AiProviderFailure($status);
    }
    return $decoded;
}

function ai_gemini_text(array $response): string
{
    $candidates = $response['candidates'] ?? null;
    $content = is_array($candidates) && isset($candidates[0]) && is_array($candidates[0])
        ? ($candidates[0]['content'] ?? null) : null;
    $parts = is_array($content) ? ($content['parts'] ?? null) : null;
    if (!is_array($parts)) {
        return '';
    }
    $text = '';
    foreach ($parts as $part) {
        if (is_array($part) && isset($part['text']) && is_string($part['text'])) {
            $text .= $part['text'];
        }
    }
    return trim($text);
}

function ai_chat_completion_text(array $response): string
{
    $choices = $response['choices'] ?? null;
    $message = is_array($choices) && isset($choices[0]) && is_array($choices[0])
        ? ($choices[0]['message'] ?? null) : null;
    $content = is_array($message) ? ($message['content'] ?? null) : null;
    if (is_string($content)) {
        return trim($content);
    }
    if (!is_array($content)) {
        return '';
    }
    $parts = [];
    foreach ($content as $part) {
        if (is_array($part) && isset($part['text']) && is_string($part['text'])) {
            $parts[] = $part['text'];
        }
    }
    return trim(implode('', $parts));
}

function ai_provider_complete(array $provider, string $system, string $message): string
{
    $name = $provider['name'] ?? '';
    $key = $provider['key'] ?? '';
    $model = $provider['model'] ?? '';
    if (!is_string($name) || !is_string($key) || $key === '' || !is_string($model)
        || !ai_valid_model($model) || !in_array($name, ['openai', 'gemini', 'groq'], true)) {
        throw new AiProviderFailure();
    }
    if ($name === 'openai') {
        $response = ai_provider_post_json('https://api.openai.com/v1/responses', [
            'Authorization: Bearer ' . $key,
        ], [
            'model' => $model,
            'store' => false,
            'max_output_tokens' => 1200,
            'input' => [
                ['role' => 'system', 'content' => [['type' => 'input_text', 'text' => $system]]],
                ['role' => 'user', 'content' => [['type' => 'input_text', 'text' => $message]]],
            ],
        ]);
        $text = ai_responses_text($response);
    } elseif ($name === 'gemini') {
        $url = 'https://generativelanguage.googleapis.com/v1beta/models/'
            . rawurlencode($model) . ':generateContent';
        $response = ai_provider_post_json($url, [
            'x-goog-api-key: ' . $key,
        ], [
            'systemInstruction' => ['parts' => [['text' => $system]]],
            'contents' => [['role' => 'user', 'parts' => [['text' => $message]]]],
            'generationConfig' => ['maxOutputTokens' => 1200],
        ]);
        $text = ai_gemini_text($response);
    } else {
        $response = ai_provider_post_json('https://api.groq.com/openai/v1/chat/completions', [
            'Authorization: Bearer ' . $key,
        ], [
            'model' => $model,
            'messages' => [
                ['role' => 'system', 'content' => $system],
                ['role' => 'user', 'content' => $message],
            ],
            'max_tokens' => 1200,
            'stream' => false,
        ]);
        $text = ai_chat_completion_text($response);
    }
    if ($text === '' || strlen($text) > 32_000) {
        throw new AiProviderFailure();
    }
    return $text;
}

/** Uses the next configured provider when a provider is unavailable; never exposes its diagnostics. */
function ai_complete_with_fallback(array $providers, string $system, string $message): string
{
    foreach ($providers as $provider) {
        try {
            return ai_provider_complete($provider, $system, $message);
        } catch (Throwable $error) {
            // Keep provider errors, response bodies, and credentials out of logs and client messages.
        }
    }
    throw new AiProviderFailure(502);
}
