# ADR 0001 — Interface / Application / Data layering

Status: Accepted · 2026-08-07

## Context

The core of the backend lives in one 94-file `com.schoolos.management` package
where controllers bind requests, make business decisions, **and** call
repositories directly. Two consequences keep hurting us:

1. **Recurring cross-tenant IDOR leaks.** Because the tenant check is
   hand-written in each of ~36 controllers, someone always forgets one. The
   same class of bug (`repository.findById(id)` with no tenant filter) has
   leaked student PII on production more than once.
2. **Low cohesion.** 500–700 line grab-bag controllers
   (`UnifiedDashboardWebController`, `AdminManagementController`) mix unrelated
   concerns, which is exactly where defects hide.

## Decision

Every class belongs to exactly one of three layers, named by convention, with
a **one-directional dependency rule**: `interface → application → data`.
Nothing calls outward.

| Layer | Package | Job | Must NOT |
|-------|---------|-----|----------|
| **Interface** | `<domain>.web` | Bind HTTP params, CSRF, resolve auth context, serialize responses | Contain business rules; import repositories or entities |
| **Application** | `<domain>.app` | Make decisions, orchestrate, own `@Transactional` boundaries, **enforce tenant isolation** | Import `jakarta.servlet` / Thymeleaf / web types |
| **Data** | `<domain>.data` | Read/write rows | Decide *whether* a read is allowed; import `web`/`app` |

### What crosses each boundary

- `web → app`: a **Command/Query** object (+ the caller's `tenantId`). Back: a **DTO**, never a JPA entity.
- `app → data`: plain args / a spec. Back: domain **entities**.
- **Entities never leave the application layer.** Controllers get DTOs.

### Tenant isolation is structural, in the data layer

Repositories extend `TenantScopedRepository` and expose only tenant-scoped
finders (`findByIdAndTenantId`, ...). **Bare `findById` is banned.** The
per-request `TenantContext` supplies the `tenantId`; the application layer is
the single place that reads it and passes it down. The leak class becomes
impossible to write, not merely discouraged.

### Enforcement

An ArchUnit test (`ArchitectureTest`) fails the build on any wrong-direction
import or a servlet import inside `app`/`data`. This runs in CI, so the layers
cannot rot back into a single package.

## Rollout

Vertical slice first. `roster/` is the reference implementation (it is where
the leaks lived); every later domain — `fees`, `attendance`, `messaging`,
`timetable`, `curriculum` — is carved out of `management/` using the same
`web/app/data` template until `management/` is empty and deleted.

## Consequences

- **+** One home for the tenant check; the recurring IDOR class is designed out.
- **+** Thin, testable, single-responsibility classes; storage swappable behind the data layer.
- **+** New engineers can name any class's layer in one second.
- **−** More files (DTOs, commands) and up-front refactoring effort.
- **−** Two conventions coexist during migration until `management/` is gone.

## Example — a request through all three layers

```
web   StudentProfileController.show(id, Authentication auth)
        tenantId = tenantContext.requireTenantId()      // context only, no logic
        return profileService.getProfile(id, tenantId)  // hand off

app   StudentProfileService.getProfile(id, tenantId)
        Student s = studentRepo.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(StudentNotFound::new)   // isolation enforced ONCE
        return ProfileResult.from(s)                          // DTO out, not entity

data  StudentRepository.findByIdAndTenantId(id, tenantId)     // fetch only
```
