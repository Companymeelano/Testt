# Meelano AI proxy (test deployment)

This is a standalone PHP service. It is **not** the Atiran business server and has no SQL connection. The Android app runs its existing, fixed, read-only report queries locally, checks the active app role, removes contact/authentication columns, and sends only the selected report facts plus the bounded recent chat when the user has opted in. The model never receives SQL access and cannot write business records.

The proxy keeps the provider key on the server, authenticates the app with a short-lived HS256 JWT, limits requests, and calls the OpenAI Responses API with `store=false`. It does not persist prompts or responses. The single environment-configured login is intended for a controlled test, not a multi-tenant production deployment; it is not a replacement for Atiran's user/role authentication.

## Requirements and deployment

- PHP 8.1 or later with the cURL extension.
- A public HTTPS virtual host. Put this directory under a path such as `ai-api/`; the app setting should point to the full URL ending in `api.php`.
- Configure every secret in the hosting provider's private environment/secret manager. Do not put `.env`, keys, passwords, JWT secrets, or tokens in Git, the Android project, or chat.
- The web-server process must be able to create and write the rate-limit file. Prefer a private persistent path outside the public web root and set `AI_RATE_LIMIT_FILE` to it.

Required server environment:

| Variable | Purpose |
| --- | --- |
| `AI_LOGIN_USER` | One test login name for this proxy |
| `AI_LOGIN_PASSWORD_HASH` | Output of PHP `password_hash`; the raw password is not configured in the service |
| `AI_JWT_SECRET` | Random secret, at least 32 bytes, used only to sign access tokens |
| `OPENAI_API_KEY` | Provider key, held only in the host's secret manager |

Optional environment:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_MODEL` | `gpt-4o-mini` | Allowed model name sent to the Responses API |
| `AI_TOKEN_TTL` | 43200 seconds | JWT lifetime; accepted range is 600–86400 seconds |
| `AI_JWT_ISSUER` | `meelano-ai-api` | JWT issuer |
| `AI_RATE_LIMIT_FILE` | private system temp directory | Lock-protected rate-limit data file |
| `AI_TRUST_PROXY_TLS` | `0` | Set to `1` only when a trusted reverse proxy terminates TLS and controls `X-Forwarded-Proto` |

Generate the password hash and JWT secret directly on a trusted host or administrator machine, then transfer their results only through the host's secret-management channel. Never paste their values into this repository or the conversation. Configure TLS before enabling the endpoint. If the application server terminates TLS, leave `AI_TRUST_PROXY_TLS` unset; do not trust arbitrary forwarded headers.

## API contract

All POST endpoints require HTTPS and `Content-Type: application/json`.

- `GET api.php?action=health` returns a non-sensitive liveness response.
- `POST api.php` with `{"action":"login","user":"…","password":"…"}` returns a short-lived `token` and `expiresIn`. The Android app stores the token encrypted with Android Keystore; it does not save the password.
- `POST api.php` with `{"action":"chat","system":"…","message":"…"}` and `Authorization: Bearer <token>` returns `{"ok":true,"reply":"…"}`. The app sends a strict report-planner instruction or a Persian response prompt as `system`; server safety instructions are always added. The request body is capped, and errors never include upstream provider diagnostics or request contents.

Rate limits are applied to login attempts and authenticated chat calls. Failed or unavailable rate-limit storage fails closed. The service has no browser CORS access and does not follow redirects to another host.

## Test and lint

Run `php -l ai-api/lib.php`, `php -l ai-api/api.php`, and `php ai-api/tests/run.php` with PHP 8.1+.

This service is a deployment template, not a live endpoint. The Android proxy mode cannot connect until you deploy it to HTTPS, configure the environment variables on the host, and enter the resulting `api.php` URL and test login in the app. No provider key should be entered into the app when proxy mode is selected.
