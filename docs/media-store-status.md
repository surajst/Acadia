# Media store (PR B) — status

Branch: `wip/pr-b-media-store` (3 WIP commits, head `8355541`, based on
`97fb6ad`). Read on 2026-10-15. This is a report, not a review of a PR; nothing
on the branch was changed.

## Update, 2026-10-27: built

The gallery is now built end to end, in three PRs: the media store (#119),
the API (#120) and the screens (this one). What this report listed as
missing is done, with these choices:

- **Pipeline memory:** subsampled decode to about 2x the display size,
  rotation on the small image, one decode for both sizes, at most 2 at once.
- **Upload:** the raw request body with the pipeline's 12 MB cap, not
  multipart, so the app-wide 2 MB limit stays. Row written last; stored
  images deleted again if it fails.
- **Read path:** 15-minute links, only after the audience and consent checks.
- **Dev-media:** dev mode only, and only for signed, unexpired links.
- **Removal:** deletes the stored images; the rows stay for the consent record.
- **Consent withdrawal:** per-child tags (#114); a photo of a child without
  consent is never sent to families.
- **HEIC:** the pickers ask for JPEG (web `accept`, and the app's picker in
  "compatible" mode); anything that still arrives as HEIC is refused in words.
- **No bucket when deployed:** photos are switched off with a clear message;
  the app does not crash and does not write to the container's disk.
- **AWS SDK:** 2.55.0, on the JDK HTTP client.

**Still yours (infrastructure):** the bucket, its IAM user, and the four
environment variables on Render (see #119), then one real upload to prove
signing, permissions and CORS. Until then the gallery says photos are not set
up.

The original report follows, unchanged.

---

**One-line summary:** the storage port and the image pipeline are done and
well tested. Nothing calls them yet. There is no upload endpoint, no album
service, no read path, and no app or portal screen. In size, the branch is
roughly the first third of "parents can see photos of their child's class".

It merges cleanly onto current `master` (`git merge-tree`, no conflicts).
I did not run its tests from here. The last commit message says
"596 backend tests pass, up from 569".

---

## What is done

| Piece | File | State |
|---|---|---|
| Storage port | `media/MediaStore.java` | `put`, `readUrl` (presigned, expiring), `delete`. There is deliberately no presigned PUT: originals never reach the bucket with their GPS still in them. |
| S3 adapter | `media/S3MediaStore.java` | Put, presigned GET, idempotent delete. Relies on the bucket's default encryption rather than signing an SSE header. |
| Filesystem adapter | `media/LocalMediaStore.java` | For dev and CI. Has a path-traversal guard on keys. |
| Wiring | `media/MediaConfig.java` | S3 when `app.media.bucket` is set, the filesystem otherwise, with a loud startup line. Credentials come from `DefaultCredentialsProvider` (env), never from app config. |
| Key layout | `media/MediaKeys.java` | `tenant/{tenantId}/albums/{albumId}/{photoId}-{thumb,display}.jpg`. The tenant prefix makes per-school revoke, audit and delete possible. |
| Image pipeline | `media/ImagePipeline.java` | Sniffs JPEG/PNG by magic bytes. 12 MB byte cap. **Reads dimensions from the header and refuses anything over 40 MP before decoding** (decompression-bomb guard). HEIC is refused with "Please upload a JPEG or PNG photo". Rotates per EXIF, then always re-encodes to JPEG, which discards all metadata, GPS included. Transparent PNGs get a white background. Outputs: display 1600px and thumb 400px longest edge. |
| EXIF orientation | `media/ExifOrientation.java` | Hand-written parser (no new dependency). Anything unparseable reads as upright. Hardened against truncation, lying counts, self-referencing IFDs and bad byte order, and each of those tests is time-bounded. |
| Tests | `ImagePipelineTest` (17), `ExifOrientationTest` | Real EXIF and PNG fixtures, not stubs. They assert pixels as well as dimensions. |
| Architecture | `LayeringArchitectureTest` | `media` is added to the non-domain allow-list next to `language` and `parentapp`, with a comment saying album or consent logic must not live there. |
| Dependency | `pom.xml` | `software.amazon.awssdk:s3` pinned at `2.20.162`. |

The data model already exists on `master`: `V26__photo_consent_and_albums.sql`
(PR A) adds `students.photo_consent`, `photo_albums` and `album_photos`, with
`thumb_key` and `display_key` columns that match `MediaKeys`.

## What is missing

In the order it would need building:

1. **The album slice (`gallery/web|app|data`).** Entities and tenant-scoped
   repositories for `photo_albums` and `album_photos`. There is no bare
   `findById` (ADR 0001). The service decides who may create an album, checks
   consent, and resolves the audience (SCHOOL / GRADE / SECTION) to who may see it.
2. **Upload endpoint.** Multipart, calling `ImagePipeline.render` twice
   (display and thumb) and then `MediaStore.put` for both, then the row. Two
   things to watch:
   - `application.properties` sets `spring.servlet.multipart.max-file-size`
     and `max-request-size` to **2MB**. The pipeline's 12 MB cap is unreachable
     until those are raised, and until then most phone photos fail with a
     framework error, not the friendly one. Raising them is app-wide (it also
     covers the bulk-import upload), so consider a per-endpoint limit.
   - The two puts and the DB insert are not atomic. A failure between them
     leaves orphaned objects. Either write the row last and sweep orphans, or
     delete what was put on failure.
3. **Read path.** Listing an album returns `readUrl(key, short TTL)` per photo,
   and only after the audience check.
4. **The dev-media endpoint.** `LocalMediaStore.readUrl` returns
   `{base}/dev-media/{key}` and its `read()` says it is "for the dev-only
   endpoint that serves them". That endpoint is not on the branch, so locally
   every image link would 404. It must only exist when the local store is
   active, and it must still check tenant and audience. Otherwise it is an
   unauthenticated read of every photo in dev.
5. **Removal.** `removed_at` is a soft delete. Decide whether removing a photo
   also deletes the objects. For photos of children, and for a consent withdrawn
   later, it probably should.
6. **Consent withdrawal.** When `students.photo_consent` goes false, what
   happens to existing photos of that child? Albums record who checked consent
   and when, but not per-child membership, so this cannot be answered from the
   schema as it stands. That is a product question (see below).
7. **Clients.** Upload and gallery screens in `mobile-app` (and/or a portal
   page). Each needs a Playwright test and a `page_health.spec.js` entry
   (AGENTS.md).
8. **Infrastructure (yours, not code).** The bucket (private, default
   encryption), CORS for presigned GET, an IAM user limited to `PutObject` /
   `GetObject` / `DeleteObject` on the prefix, and `app.media.bucket` /
   `app.media.region` / AWS keys on Render. `app.media.region` has **no default**,
   so setting the bucket without the region fails at startup.
9. **One real round trip against the actual bucket**, as `LocalMediaStore`'s
   own Javadoc says. Signing, permissions and CORS are exactly the parts the
   filesystem adapter cannot exercise.

## Risks

- **Memory per upload is still large, and nothing limits concurrency.** The
  40 MP cap bounds one decode at about 160 MB (4 bytes per pixel), but a photo
  that needs rotating holds the decoded image, the `getRGB` copy, the output
  `int[]` and the rotated image at the same time: **about 640 MB peak** at the
  cap. `render` produces one size per call, so producing both the display and
  the thumb, as the schema expects, decodes twice. A 1 GB
  container survives one such upload and not two at once. Cheapest fixes:
  - decode subsampled (`ImageReadParam.setSourceSubsampling`) straight to about
    2× the display edge;
  - rotate the small image, not the full one;
  - decode once for both outputs;
  - put a semaphore around the pipeline.
  This matters most because a phone's full-resolution portrait shot is exactly
  the image that needs rotating.
- **CMYK / Adobe JPEGs.** `ImageIO.read` cannot decode some of these (scanner
  output, images saved from design tools). They will be refused as
  "not an image this server can read". That is rare from phones, but a school
  uploading a flyer will hit it.
- **HEIC is refused rather than converted.** That is the right call for now,
  and the message is actionable. But iPhone parents will see it on their first
  try unless the picker asks for JPEG (`expo-image-picker` can request a
  compatible format). This is worth deciding before launch, not after.
- **AWS SDK pin.** `2.20.162` is from 2023. The pom comment says it was chosen
  because it was already in a local Maven cache for offline builds. Upgrading
  before merge is cheaper than after, but it is a dependency change and needs
  its own decision.
- **Dev fallback in production.** `MediaConfig` falls back to the container's
  disk whenever `app.media.bucket` is unset, and only logs a line. On Render
  that disk is wiped on deploy. Consider refusing to start when not in dev mode
  and no bucket is set, rather than warning.
- **Architecture exemption.** `media` is exempt from the layering rule. That is
  fine while it only moves bytes; the comment says so. Album logic must go in
  its own slice.

## Estimate

For someone who knows the codebase, assuming the bucket exists:

| Work | Estimate |
|---|---|
| Memory fixes in the pipeline (subsample, rotate small, decode once, semaphore) + tests | 0.5–1 day |
| Gallery slice: entities, tenant-scoped repos, service, audience and consent rules, backend tests | 1.5–2 days |
| Upload / list / remove endpoints, multipart limits, orphan handling, dev-media endpoint, MockMvc tests | 1–1.5 days |
| Mobile upload + gallery screens, Playwright tests, page health | 1.5–2 days |
| Real-bucket round trip, CORS/IAM verification, fixes from it | 0.5 day |
| **Total** | **about 5–7 working days** |

That excludes the product decisions below, any portal (Thymeleaf) gallery, and
an SDK upgrade.

## Decisions needed (not taken here)

- What happens to existing photos when a parent withdraws photo consent?
  (This shapes whether `album_photos` needs per-child tagging.)
- Does removing a photo delete the stored objects, or only hide them?
- Should HEIC be converted, or should the app ask the picker for JPEG?
- Should a deployed instance refuse to start without a bucket?
