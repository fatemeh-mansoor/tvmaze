#!/bin/bash
set -e
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  until pg_isready -h db -U tvmaze; do sleep 1; done
  mkdir -p "$PGDATA"
  chown postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"
  # -R writes standby.signal and primary_conninfo, so this node starts as a streaming replica
  PGPASSWORD=replicator gosu postgres pg_basebackup -h db -U replicator -D "$PGDATA" -R -X stream
fi
exec docker-entrypoint.sh postgres -c hot_standby=on
