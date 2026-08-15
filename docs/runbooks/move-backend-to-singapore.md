# Runbook: move the backend from Oregon to Singapore

**DONE — completed 2026-08-13.** Kept as the record of what was changed and why,
and as the procedure to follow if the service is ever rebuilt in another region.

Result, measured through `portal.concept-edu.com` after cutover:

| | Oregon (before) | Singapore (after) |
|---|---|---|
| Plain page render | ~0.30s | **~0.10s** |
| App -> database round trip | ~170ms | **~0ms** |
| Page issuing ~10 queries | ~1.9s | **~0.1s** |

The live service is `acadia-backend-sg`. The Oregon service `acadia-backend`
(`acadia-backend-rx3l.onrender.com`) is suspended and returns 503, kept briefly
as rollback before deletion.

One-off migration. Follow the order — the cutover steps are sequenced so that
the old service keeps serving until the new one is proven, and so the only
irreversible step (deleting Oregon) happens last.

---

## Why

Measured 2026-08-13, against the live deployment:

| Leg | Latency |
|---|---|
| User (India) -> app (Oregon) | ~230ms |
| App (Oregon) -> database (Neon, ap-southeast-1) | ~170ms per query |

The users are in India and the database is in Singapore. The application is the
only component in Oregon, and it pays the distance twice — once to reach the
user, once for every query.

The ~170ms figure is the measured difference between `/actuator/health` (runs one
database query) and `/login` (does not). Both traverse Render's edge, so the edge
cancels out and the remainder is the app-to-database round trip.

Effect on a page issuing 10 queries: **~1.9s today, ~70ms after the move.** That
is not tuning, it is a different product. Render cannot change a service's region
in place, so this requires a new service and a cutover.

**Do it before the first school onboards.** Today it is near-zero risk: no users,
~30 MB of data, nothing to lose in a botched cutover. Afterwards the same move
needs a maintenance window, comms, and a rollback plan.

---

## Before you start

- [ ] Take a manual dump (see `database-backup-and-restore.md`). The database is
      not being migrated, but do not perform infrastructure surgery without one.
- [ ] Note the current hostname: `acadia-backend-rx3l.onrender.com`
- [ ] Have the Neon connection details to hand — `DB_*` are `sync: false`, so
      they exist only in the Render dashboard and must be re-entered by hand.

Nothing here touches the database. Neon stays exactly where it is; only the
thing that connects to it moves.

---

## Cutover

### 1. Create the new service

Render -> New -> Web Service -> same repo, **Region: Singapore**, Docker runtime,
`./backend/Dockerfile`, context `./backend`, Instance type Starter,
Health check path `/actuator/health`.

Give it a distinct name (e.g. `acadia-backend-sg`) so both can run side by side.

### 2. Set environment variables

Copy from the Oregon service. Re-enter by hand — `sync: false` values are not
carried over by the blueprint:

`SPRING_PROFILES_ACTIVE=postgres`, `APP_DEV_MODE=false`, `SENTRY_DSN`,
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

`JWT_SECRET` is generated fresh on the new service. That invalidates every
existing session and mobile token — free today with no users, disruptive later.
If you ever repeat this with users, copy the old value across instead.

### 3. Verify the new service before sending it any traffic

Against the new `*.onrender.com` hostname, not the custom domain:

```bash
NEW=https://acadia-backend-sg-xxxx.onrender.com
curl -s $NEW/actuator/health                                  # {"status":"UP"}
curl -s -o /dev/null -w "%{http_code}\n" $NEW/login           # 200
curl -s -o /dev/null -w "%{http_code}\n" $NEW/wp-admin        # 404
```

Then confirm the whole point of the exercise — the app-to-database round trip:

```bash
for i in 1 2 3; do curl -s -o /dev/null -w "login  %{time_total}s\n" $NEW/login; done
for i in 1 2 3; do curl -s -o /dev/null -w "health %{time_total}s\n" $NEW/actuator/health; done
```

The health/login gap should collapse from ~170ms to single-digit ms. **If it does
not, stop.** The move has not achieved anything and the cause needs finding
before you cut over.

Finally, log in as a real user and load the admin dashboard. `ddl-auto=validate`
means a clean boot already proves the schema matches, but only a real login
proves it is reading the right database with the right data.

### 4. Cut over the domains

- `portal.concept-edu.com`: remove the custom domain from the Oregon service,
  add it to the Singapore service, update the CNAME. Render will not serve the
  same custom domain from two services, so there is a brief gap here — this is
  the only user-visible moment of the migration.
- Update `EXPO_PUBLIC_API_HOST` in `render.yaml` to the new backend hostname and
  redeploy `acadia-mobile-web`, otherwise the mobile web build keeps calling
  Oregon.
- Update `healthCheckPath`/region in `render.yaml` to match the new service.

CORS needs no change: `https://*.onrender.com` and `https://*.concept-edu.com`
are already allowed.

### 5. Verify again, through the real domain

Repeat the step-3 checks against `portal.concept-edu.com`, and open the mobile
web app to confirm it is talking to the new backend.

### 6. Only then, delete the Oregon service

Leave it suspended for a day or two first. It costs one Starter instance and it
is the rollback: if something surfaces, re-point the domain back.

---

## Rollback

Before step 6, rollback is: point `portal.concept-edu.com` back at the Oregon
service and revert `EXPO_PUBLIC_API_HOST`. The database is untouched throughout,
so no data decision is involved — this is purely a routing change.

After step 6, rollback means recreating an Oregon service from the same repo.

---

## What actually happened

Two things worth knowing if this is ever repeated:

- **Render routes custom domains by Host header.** As soon as
  `portal.concept-edu.com` was attached to the Singapore service it began
  serving from there, even though the CNAME still pointed at the Oregon
  hostname. The site was correct before DNS was.
- **That made the stale CNAME a hidden trap.** It still resolved *through*
  `acadia-backend-rx3l.onrender.com`, a name that disappears when the Oregon
  service is deleted — which would have broken the domain at DNS level, with
  nothing in Render warning about it. Update the CNAME before deleting the old
  service, not after.

Also note the `gcp-us-west1-1.origin.onrender.com` suffix that appears in
Render CNAME chains is shared edge naming and is **not** a region indicator; it
shows up for the Singapore service too. Only the service hostname identifies
the service.

## Afterwards

- Watch Neon **CU-hrs/day**. Co-located compute makes the connection pool
  settings cheaper to get wrong; see the Hikari comments in
  `application.properties`.
- Re-measure the health/login gap after a few days and record the number here,
  so the next person can tell whether a future regression is new or normal.
- Update `docs/runbooks/database-backup-and-restore.md`, which references the old
  `acadia-backend-rx3l.onrender.com` hostname.
