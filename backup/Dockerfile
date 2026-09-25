# Image for the acadia-db-backup Render cron job.
#
# postgres:18-alpine for pg_dump and pg_restore, not because this runs a server.
# 18 is the major version production Neon is on -- pg_dump must be the same major
# version or newer than the server, and an older one refuses to run rather than
# producing a subtly wrong dump, so the failure mode here is loud.
#
# If Neon is ever upgraded past 18, this tag has to move with it. The refusal
# ("server version mismatch") appears in the cron log and the run fails, so the
# job reports the problem rather than hiding it.
FROM postgres:18-alpine

# aws-cli v2 from Alpine's community repo. No pip, no virtualenv: the package is
# a static binary and this image only has to upload one file.
RUN apk add --no-cache aws-cli

COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh

# Not root. The script writes only to /tmp and needs no database or filesystem
# privileges beyond that, and the postgres user already exists in this image.
USER postgres

# No ENTRYPOINT from the base image: postgres:*-alpine would otherwise try to
# initialise a data directory and run a server, which is not what this is for.
ENTRYPOINT []
CMD ["/usr/local/bin/backup.sh"]
