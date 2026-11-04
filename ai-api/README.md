# Meelano AI proxy (multi-provider deployment template)

This is a separately deployed PHP service. It is **not** the Atiran business server and has no SQL connection. The Android app runs its existing, fixed, read-only report queries locally, checks the active app role, removes contact/authentication columns, and sends only the selected report facts plus bounded recent chat when the user has opted in. The model never receives SQL access and cannot write business records.

The proxy keeps provider credentials only in the hosting environment/secret manager and supports OpenAI Responses, Google Gemini `generateContent`, and Groq chat completions. It authenticates the app with a short-lived HS256 JWT, applies request limits, does not persist prompts/responses locally, and never returns upstream diagnostics. It can fail over to another configured provider. The sample has one environment-configured login for controlled testing; it is **not** a production multi-tenant identity or role service and must not be treated as a substitute for Atiran authentication.

## Requirements and deployment

- PHP 8.1 or later with the cURL extension.
- A public HTTPS virtual host. Put this directory under a path such as `ai-api/`; the app setting should point to the full URL ending in `api.php`.
- Configure every secret in the hosting provider's private environment/secret manager. Do not put `.env`, keys, passwords, JWT secrets, or tokens in Git, the Android project, APK, CI logs, or chat.
- The web-server process must be able to create and write the rate-limit file. Prefer a private persistent path outside the public web root and set `AI_RATE_LIMIT_FILE` to it.
- Allow a 60-second PHP/FPM request budget for bounded provider failover; hosting-level execution limits can override the request's attempt to set it.
- Before any organization-wide production use, replace the sample shared login with a reviewed, user-aware authentication/deployment design, set quotas and monitoring, and review each provider's data-retention terms.

Required server environment:

| Variable | Purpose |
| --- | --- |
| `AI_LOGIN_USER` | One controlled-test login name for this template |
| `AI_LOGIN_PASSWORD_HASH` | Output of PHP `password_hash`; the raw password is not configured in the service |
| `AI_JWT_SECRET` | Random secret, at least 32 bytes, used only to sign access tokens |
| `OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROQ_API_KEY` | Configure one or more provider keys in the host secret manager; only configured providers are eligible |

Optional environment:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_MODEL_OPENAI` | `gpt-4o-mini` | OpenAI model; `AI_MODEL` remains a legacy fallback for this provider |
| `AI_MODEL_GEMINI` | `gemini-2.5-flash` | Gemini model |
| `AI_MODEL_GROQ` | `llama-3.3-70b-versatile` | Groq model |
| `AI_PROVIDER_ORDER` | task-specific order below | Private comma-separated preference containing any of `openai,gemini,groq`; unavailable providers are skipped |
| `AI_TOKEN_TTL` | 43200 seconds | JWT lifetime; accepted range is 600–86400 seconds |
| `AI_JWT_ISSUER` | `meelano-ai-api` | JWT issuer |
| `AI_RATE_LIMIT_FILE` | private system temp directory | Lock-protected rate-limit data file |
| `AI_TRUST_PROXY_TLS` | `0` | Set to `1` only when a trusted reverse proxy terminates TLS and controls `X-Forwarded-Proto` |

Without `AI_PROVIDER_ORDER`, routing prefers Gemini/OpenAI/Groq for `analysis` and `writing`, Groq/Gemini/OpenAI for `fast`, and OpenAI/Gemini/Groq for `general`. The order is filtered to server-configured providers; if a provider call fails, the next configured provider is tried. Because this may send the same prompt/report facts to a backup provider, the app presents that fact in its consent text. No client request can select a provider or send a provider key.

Generate the password hash and JWT secret directly on a trusted host or administrator machine, then transfer their results only through the host's secret-management channel. Never paste their values into this repository or the conversation. Configure TLS before enabling the endpoint. If the application server terminates TLS, leave `AI_TRUST_PROXY_TLS` unset; do not trust arbitrary forwarded headers.

## API contract

All POST endpoints require HTTPS and `Content-Type: application/json`.

- `GET api.php?action=health` returns a non-sensitive liveness response.
- `POST api.php` with `{"action":"login","user":"…","password":"…"}` returns a short-lived `token` and `expiresIn`. The Android app stores the token encrypted with Android Keystore; it does not save the password.
- `POST api.php` with `{"action":"chat","task":"fast","system":"…","message":"…"}` and `Authorization: Bearer <token>` returns `{"ok":true,"reply":"…"}`. `task` accepts `analysis`, `fast`, `writing`, or `general` (omitted means `general`). It only affects server-side provider preference; it cannot expose or select a credential. The app sends a strict report-planner instruction or Persian response prompt as `system`; server safety instructions are always added. The request body is capped, and errors never include upstream provider diagnostics or request contents.

Rate limits are applied to login attempts and authenticated chat calls. Failed or unavailable rate-limit storage fails closed. The service has no browser CORS access and does not follow redirects to another host.

## Test and lint

Run `php -l ai-api/lib.php`, `php -l ai-api/providers.php`, `php -l ai-api/api.php`, and `php ai-api/tests/run.php` with PHP 8.1+.

This repository contains a deployment template, **not a live endpoint**. The Android proxy mode cannot connect until the service is deployed to HTTPS, required secrets and a test login are configured on the host, and the `api.php` URL is entered in the app. Do not put shared provider keys in the APK or ask end users to enter them. The optional direct-connection screen is for a user's own personal key only and is hidden under advanced settings.
