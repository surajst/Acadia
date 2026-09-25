#!/bin/sh
#
# Nightly logical backup of the production Neon database to S3.
#
# Runs as a Render cron job (acadia-db-backup). Exits non-zero on any problem so
# Render marks the run failed and the dashboard shows it -- a backup job that
# fails quietly is worse than no backup job, because it looks like cover.
#
# Deliberately never prints the database URL, and there is no `set -x`: the URL
# carries the password, and cron logs are retained and readable by anyone with
# dashboard access.
#
# The uploader's IAM user may only PutObject and AbortMultipartUpload under
# daily/ and weekly/. It cannot list, read or delete, so nothing here may verify
# an upload by reading it back, prune old files, or check whether a key already
# exists -- retention is the bucket's lifecycle policy's job, not this script's.
set -eu

# ─── Inputs ─────────────────────────────────────────────────────────────────
: "${BACKUP_DATABASE_URL:?BACKUP_DATABASE_URL is not set}"
: "${BACKUP_BUCKET:?BACKUP_BUCKET is not set}"
# The number of tables the dump is expected to contain. An env var rather than a
# constant because the schema grows; see the sanity check below for why it
# matters more than it looks.
MIN_TABLES="${MIN_TABLES:-20}"
# Only for testing against a local S3 (MinIO in CI). Unset in production, where
# the real endpoint is the right one.
BACKUP_S3_ENDPOINT="${BACKUP_S3_ENDPOINT:-}"

TS=$(date -u +%Y-%m-%dT%H%MZ)
FILE="acadia-$TS.dump"
OUT="/tmp/$FILE"

# ─── Dump ───────────────────────────────────────────────────────────────────
# --format=custom so pg_restore can be selective on the way back in; --no-owner
# and --no-privileges because the restore target is a fresh Neon branch whose
# role names do not match production's.
#
# sslmode=require must stay in the URL. Neon refuses plaintext, and losing it
# turns this into a connection error rather than an insecure backup -- but say
# it out loud, because a URL edited by hand is how it would go missing.
pg_dump "$BACKUP_DATABASE_URL" \
    --format=custom \
    --no-owner \
    --no-privileges \
    --compress=9 \
    -f "$OUT"

# ─── Sanity checks ──────────────────────────────────────────────────────────
# Both of these exist because pg_dump can succeed and still produce something
# useless. A dump taken against an empty database, or one where the role could
# see no tables, exits 0 and writes a valid archive containing nothing. That is
# the failure that looks like success for months and is discovered during a
# restore, which is the worst possible moment.

BYTES=$(wc -c < "$OUT" | tr -d ' ')
if [ "$BYTES" -lt 10240 ]; then
    echo "BACKUP FAILED: dump is only ${BYTES} bytes, which is too small to be real" >&2
    exit 1
fi

# `|| true` is load-bearing: grep -c exits 1 when it counts zero, and under
# `set -e` that would kill the script here with no message at all -- the one
# case this check exists to report.
TABLES=$(pg_restore --list "$OUT" | grep -c "TABLE DATA" || true)
if [ "$TABLES" -lt "$MIN_TABLES" ]; then
    echo "BACKUP FAILED: dump holds data for only ${TABLES} tables, expected at least ${MIN_TABLES}" >&2
    echo "BACKUP FAILED: a dump this thin usually means the role could not see the schema" >&2
    exit 1
fi

# ─── Upload ─────────────────────────────────────────────────────────────────
# --sse AES256 is explicit even though the bucket encrypts by default: the
# default can be changed in the console without anything here noticing, and an
# unencrypted object would be silently accepted.
#
# --only-show-errors keeps the progress bar out of the cron log without hiding
# a failure.
if [ -n "$BACKUP_S3_ENDPOINT" ]; then
    set -- --endpoint-url "$BACKUP_S3_ENDPOINT"
else
    set --
fi

aws s3 cp "$OUT" "s3://$BACKUP_BUCKET/daily/$FILE" \
    --sse AES256 --only-show-errors "$@"

# On Sundays the same file also goes to weekly/, which the bucket's lifecycle
# keeps for a year rather than 35 days. Uploaded twice rather than copied,
# because a copy needs read access the uploader does not have.
if [ "$(date -u +%u)" = "7" ]; then
    aws s3 cp "$OUT" "s3://$BACKUP_BUCKET/weekly/$FILE" \
        --sse AES256 --only-show-errors "$@"
fi

# ─── The one line that gets read ────────────────────────────────────────────
# Exactly this shape, because it is what a human greps for in the Render log to
# answer "did last night work?".
echo "BACKUP OK $FILE $BYTES $TABLES"
