# MemOS Frontend

React + TypeScript + Vite UI for the MemOS RAG backend (auth, document ingestion, and chat with citations).

## Stack

- React 18 + TypeScript + Vite
- Tailwind CSS v4 + shadcn/ui
- React Router
- TanStack Query (React Query) + Axios

## Prerequisites

- Node.js 22 (see `.nvmrc`): `nvm use`
- MemOS backend running on `http://localhost:8080`

## Setup

```bash
npm install
```

Configure the API base URL in `.env.development` (defaults to `http://localhost:8080`):

```
VITE_API_BASE_URL=http://localhost:8080
```

## Run

```bash
npm run dev
```

The dev server runs on **http://localhost:3000**. This port is required to match the
backend's default CORS configuration (`memos.cors.allowed-origins=http://localhost:3000`) —
no backend changes are needed.

## Other scripts

```bash
npm run build   # type-check + production build
npm run lint     # eslint
npm run preview  # preview the production build
```

## Authentication

- Access tokens are kept in memory and refresh tokens in `localStorage`.
- An Axios response interceptor automatically refreshes the access token on a `401`
  and retries the request once; if refresh fails the user is logged out.
