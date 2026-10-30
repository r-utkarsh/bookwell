# Bookwell frontend

React 19 + TypeScript + Vite SPA for the Bookwell appointment platform.

## Local development

```powershell
npm ci
npm run dev
```

Vite serves the app on `http://localhost:5173` and proxies `/api` to the
backend on `http://localhost:8080`.

## Checks

```powershell
npm test
npm run lint
npm run build
```

## Production

Set `VITE_API_BASE_URL` to the public backend origin at build time. The value is
public configuration, not a secret. See the root [`README.md`](../README.md) and
[`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md) for the Render/Vercel setup.
