# NextSay Backend

> Optional legacy/proxy infrastructure: the current Android app does not require this service. Its default BYOK path calls the user's OpenAI-compatible provider directly from the phone.

The backend is retained for legacy protocol testing, deterministic mock development, or teams that intentionally want to operate their own proxy. It validates reviewed chat context and returns three structured reply candidates.

## Local Setup

From the repository root on Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e "backend[test]"
$env:NEXTSAY_DEV_TOKEN = "local-dev-token"
.\.venv\Scripts\python.exe -m uvicorn nextsay_backend.app:app --app-dir backend/src --reload
```

The API is available at `http://127.0.0.1:8000`.

## Test Request

```powershell
$headers = @{ Authorization = "Bearer local-dev-token" }
$body = @{
  messages = @(@{ role = "other"; text = "明天下午能把方案交给我吗？" })
  relationship = "manager"
  locale = "zh-CN"
} | ConvertTo-Json -Depth 4
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8000/v1/replies" -Headers $headers -ContentType "application/json" -Body $body
```

## External Provider

Copy `.env.example` to `.env` or set the variables in the process environment:

```text
NEXTSAY_PROVIDER=openai
NEXTSAY_BASE_URL=https://provider.example/v1
NEXTSAY_MODEL=provider-model-name
NEXTSAY_API_KEY=provider-secret
NEXTSAY_DEV_TOKEN=client-to-backend-development-token
```

The external service must implement the OpenAI-compatible `POST /chat/completions` contract. These environment variables apply only when choosing to operate this optional proxy; they are unrelated to the per-user encrypted API configuration in the current Android app.

## Privacy

The server does not persist requests or responses. Operational logs contain request identifiers, paths, status codes, latency, and provider names only. Do not enable generic HTTP body logging in production.
