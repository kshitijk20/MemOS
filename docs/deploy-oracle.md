# Deploying MemOS on Oracle Cloud Always Free

This runs the whole stack — Postgres + pgvector, Redis, the backend, the frontend and Caddy
(automatic HTTPS) — on one free ARM server using `docker-compose.prod.yml`.

Free-tier terms change; check Oracle's current Always Free page before you start. As of 2026 the
Ampere A1 allowance is **2 OCPUs and 12 GB of RAM** in total, which is plenty for MemOS.

## 1. Create the server

1. Sign up at <https://www.oracle.com/cloud/free/>. A card is required for verification; Always
   Free resources are not charged. Your **home region** is permanent and free A1 servers can only
   be created there, so pick one close to your users.
2. **Compute → Instances → Create instance**:
   - **Image:** Canonical Ubuntu 24.04 (the aarch64 build is chosen automatically for Ampere).
   - **Shape:** Ampere → `VM.Standard.A1.Flex`, **2 OCPUs, 12 GB memory**.
   - **Networking:** create a new VCN with a public subnet and **assign a public IPv4 address**.
   - **SSH keys:** upload your public key (`~/.ssh/id_ed25519.pub`) or download the generated one.
   - **Boot volume:** the default (~47 GB) is fine; up to 200 GB of block storage is free.
3. If you get *"Out of capacity for shape VM.Standard.A1.Flex"*, try another availability domain
   in the same region, or retry later — free ARM capacity comes and goes.

## 2. Open ports 80 and 443

Two firewalls need opening.

**a. Oracle's network firewall:** Networking → Virtual cloud networks → your VCN → the subnet's
security list → **Add ingress rules**:

| Source CIDR | Protocol | Destination port |
|---|---|---|
| `0.0.0.0/0` | TCP | 80 |
| `0.0.0.0/0` | TCP | 443 |
| `0.0.0.0/0` | UDP | 443 (optional, HTTP/3) |

**b. The server's own firewall.** Oracle's Ubuntu images ship iptables rules that block
everything except SSH, even after step (a). SSH in and allow web traffic:

```bash
ssh ubuntu@<PUBLIC_IP>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Get a domain name

Caddy needs a hostname that points at the server to obtain a free Let's Encrypt certificate.
Free options:

- **DuckDNS** (<https://www.duckdns.org>): sign in, create e.g. `memos-yourname`, set its IP to
  the server's public IP. Your domain is `memos-yourname.duckdns.org`.
- **sslip.io** (no sign-up): use the IP with dashes, e.g. `129-146-12-34.sslip.io`.
- Your own domain: add an `A` record pointing at the public IP.

Check it resolves before continuing: `dig +short <your-domain>` should print the server IP.

## 4. Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit   # log out and back in so the group change applies
```

## 5. Deploy

```bash
ssh ubuntu@<PUBLIC_IP>
git clone https://github.com/kshitijk20/MemOS.git
cd MemOS
cp .env.prod.example .env.prod
nano .env.prod
```

Fill in `.env.prod`:

| Variable | Value |
|---|---|
| `DOMAIN` | your hostname from step 3 |
| `DB_PASSWORD` | output of `openssl rand -base64 32` |
| `JWT_SECRET` | output of `openssl rand -base64 64` (required; the app refuses to start without it) |
| `GEMINI_API_KEY` | from <https://aistudio.google.com/apikey> |

Lock it down and start everything:

```bash
chmod 600 .env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

The first build downloads all Maven and npm dependencies and takes roughly 10 minutes; later
builds reuse the cache. Watch progress with:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend caddy
```

When `backend` shows `healthy` and Caddy logs `certificate obtained successfully`, open
`https://<your-domain>` and register an account.

> Tip: `alias dc='docker compose -f docker-compose.prod.yml --env-file .env.prod'` saves typing.

## 6. Updating

```bash
cd ~/MemOS
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Database migrations run automatically when the backend starts.

## 7. Backups

The `db-backup` container writes a compressed `pg_dump` to `~/MemOS/backups/` once a day and
keeps the last 7 days (`BACKUP_KEEP_DAYS`). These are on the same server, so **also copy them
somewhere else** — for example to your laptop:

```bash
scp ubuntu@<PUBLIC_IP>:MemOS/backups/memos-*.sql.gz ./memos-backups/
```

or to Oracle Object Storage (20 GB free) with the OCI CLI.

To restore a dump into a fresh stack:

```bash
dc stop backend
gunzip -c backups/memos-YYYYMMDD-HHMMSS.sql.gz | dc exec -T postgres psql -U memos -d memos
dc start backend
```

Uploaded files that were still waiting for ingestion live in the `uploads` Docker volume, and
Caddy's certificates in `caddy-data`; neither needs backing up for normal operation.

## 8. Things to know

- **Gemini free tier:** about 5 chat requests per minute shared by every user. Follow-up query
  rewriting is off by default in production for this reason (`MEMOS_RAG_QUERY_REWRITE_ENABLED`).
- **Idle reclamation:** Oracle may reclaim Always Free instances that stay almost completely idle
  for 7 days (very low CPU, network and memory use). A JVM app with regular visitors normally
  stays above the threshold; check Oracle's current policy if the demo will sit unused.
- **Only ports 80/443 are public.** Postgres and Redis are reachable only inside the Docker
  network, and the API docs (Swagger) are disabled in this stack.
- **Security updates:** run `sudo apt update && sudo apt upgrade` regularly, or enable
  `unattended-upgrades`.
