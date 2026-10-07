#!/usr/bin/env bash
# Keeps jankbot's YouTube playback working with no manual maintenance.
#
# Each run (hourly, via jankbot-update.timer):
#   1. syncs the repo to origin/master
#   2. pulls the latest yt-cipher image
#   3. picks candidate youtube-source versions:
#        - the latest release, if it differs from what's deployed
#        - the deployed version again, if master/yt-cipher changed or the daily check is due
#        - the latest youtube-source main snapshot, if the above fail
#   4. builds each candidate and smoke-tests real YouTube playback against it (first pass wins)
#   5. version change -> PR with the bump + smoke test output, auto-merged, so master == deployed
#   6. waits until nothing is playing, deploys, rolls back + opens an issue if the bot goes unhealthy
#   7. nothing passes -> keeps the current deployment and opens a "youtube-broken" issue
#
# Usage: deploy/update.sh [--dry-run] [--force-check]
#   --dry-run      build and smoke test, but don't push, open PRs/issues or deploy
#   --force-check  re-test the deployed version even if the daily check isn't due
#
# Requires: git, docker (compose plugin), gh, curl. GH_TOKEN must be set for PRs/issues.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${JANKBOT_STATE_DIR:-$HOME/.local/state/jankbot-updater}"
CIPHER_UPSTREAM="ghcr.io/kikkia/yt-cipher:master"
RELEASES_METADATA="https://maven.lavalink.dev/releases/dev/lavalink/youtube/v2/maven-metadata.xml"
SNAPSHOTS_METADATA="https://maven.lavalink.dev/snapshots/dev/lavalink/youtube/v2/maven-metadata.xml"
SOURCE_REPO="lavalink-devs/youtube-source"
HEALTH_CHECK_INTERVAL=$((24 * 3600))
# a candidate that failed is not retried for this long (youtube may start/stop blocking things)
FAILED_RETRY_INTERVAL=$((12 * 3600))
# how long to wait for music to stop before restarting anyway
MAX_IDLE_WAIT=$((2 * 3600))
HEALTHY_TIMEOUT=240
SMOKE_NETWORK="jankbot-smoketest"
SMOKE_CIPHER="jankbot-smoketest-cipher"

DRY_RUN=0
FORCE_CHECK=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --force-check) FORCE_CHECK=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

# logs go to stderr so functions can return values on stdout
log() { echo "[$(date '+%F %T')] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

mkdir -p "$STATE_DIR"
exec 9>"$STATE_DIR/lock"
flock -n 9 || { log "another update is running, exiting"; exit 0; }

cd "$REPO_DIR"
[ -f .env ] || die ".env missing, copy .env.example and set CIPHER_TOKEN"
set -a; . ./.env; set +a
[ -n "${CIPHER_TOKEN:-}" ] || die "CIPHER_TOKEN not set in .env"
if [ "$DRY_RUN" = 0 ]; then
  [ -n "${GH_TOKEN:-}" ] || die "GH_TOKEN not set"
fi

# --- state helpers (one value per file) ---
state_get() { cat "$STATE_DIR/$1" 2>/dev/null || true; }
state_set() { [ "$DRY_RUN" = 1 ] || echo "$2" > "$STATE_DIR/$1"; }
now() { date +%s; }

# --- version helpers ---
pom_version() { sed -n 's:.*<youtube.source.version>\(.*\)</youtube.source.version>.*:\1:p' pom.xml; }
latest_release() { curl -fsS "$RELEASES_METADATA" | sed -n 's:.*<release>\(.*\)</release>.*:\1:p'; }
# newest main commit that has a published snapshot (CI publishes a few minutes after the push)
latest_snapshot() {
  local published sha
  published="$(curl -fsS "$SNAPSHOTS_METADATA")"
  # commit shas come first in each entry, so document order is newest first
  for sha in $(curl -fsS "https://api.github.com/repos/$SOURCE_REPO/commits?sha=main&per_page=10" \
      | grep -oE '"sha": ?"[0-9a-f]{40}"' | grep -oE '[0-9a-f]{40}'); do
    if grep -q "<version>$sha-SNAPSHOT</version>" <<<"$published"; then
      echo "$sha-SNAPSHOT"
      return
    fi
  done
}
recently_failed() {
  local failed_at
  failed_at="$(state_get "failed-$1")"
  [ -n "$failed_at" ] && [ $(( $(now) - failed_at )) -lt "$FAILED_RETRY_INTERVAL" ]
}

# --- build / test ---
image_tag() { echo "$(git rev-parse --short=10 HEAD)-$(echo "$1" | cut -c1-12)"; }

build_image() {
  local version="$1" tag
  tag="$(image_tag "$version")"
  if ! docker image inspect "jankbot:$tag" >/dev/null 2>&1; then
    log "building jankbot:$tag (youtube-source $version)"
    DOCKER_BUILDKIT=1 docker build -q --build-arg "YT_SOURCE_VERSION=$version" -t "jankbot:$tag" . >/dev/null
  fi
  echo "$tag"
}

SMOKE_OUTPUT=""
# smoke_test <jankbot image> <cipher image>
smoke_test() {
  local bot_image="$1" cipher_image="$2" rc=0
  log "smoke testing $bot_image with $cipher_image"
  docker network inspect "$SMOKE_NETWORK" >/dev/null 2>&1 || docker network create "$SMOKE_NETWORK" >/dev/null
  docker rm -f "$SMOKE_CIPHER" >/dev/null 2>&1 || true
  docker run -d --rm --name "$SMOKE_CIPHER" --network "$SMOKE_NETWORK" --network-alias yt-cipher \
    -e OVERRIDE_PLAYER_VARIANT=IAS -e "API_TOKEN=$CIPHER_TOKEN" "$cipher_image" >/dev/null
  sleep 5
  # config.json is mounted so the smoke test uses the bot's settings (eg. the youtube login)
  SMOKE_OUTPUT="$(docker run --rm --network "$SMOKE_NETWORK" -v "$REPO_DIR/config.json:/config/config.json:ro" \
    -e JANKBOT_REMOTE_CIPHER_URL=http://yt-cipher:8001 -e "JANKBOT_REMOTE_CIPHER_PASSWORD=$CIPHER_TOKEN" \
    -e "JANKBOT_YOUTUBE_CLIENTS=${JANKBOT_YOUTUBE_CLIENTS:-}" \
    "$bot_image" janktastic.jankbot.SmokeTest 2>&1 | grep -E '^SMOKETEST|^Client \[' )" || rc=$?
  docker rm -f "$SMOKE_CIPHER" >/dev/null 2>&1 || true
  echo "$SMOKE_OUTPUT" >&2
  grep -q '^SMOKETEST PASSED' <<<"$SMOKE_OUTPUT"
}

# tests a youtube-source version with the new cipher image, then the deployed one if they differ.
# sets PASSED_CIPHER to the cipher image that worked
PASSED_CIPHER=""
BUILD_FAILED=0
try_version() {
  local version="$1" tag cipher
  BUILD_FAILED=0
  tag="$(build_image "$version")" || { log "build failed for $version"; BUILD_FAILED=1; return 1; }
  for cipher in $CIPHER_CANDIDATES; do
    if smoke_test "jankbot:$tag" "$cipher"; then
      PASSED_CIPHER="$cipher"
      return 0
    fi
  done
  return 1
}

# --- github helpers ---
gh_ensure_label() { gh label create "$1" --color "$2" --force >/dev/null 2>&1 || true; }

open_issue() {
  local title="$1" body="$2" existing
  if [ "$DRY_RUN" = 1 ]; then log "[dry-run] would open issue: $title"; return; fi
  gh_ensure_label youtube-broken d73a4a
  existing="$(gh issue list --label youtube-broken --state open --json number --jq '.[0].number')"
  if [ -n "$existing" ]; then
    gh issue comment "$existing" --body "$body" >/dev/null
    log "updated issue #$existing"
  else
    gh issue create --title "$title" --label youtube-broken --body "$body" >/dev/null
    log "opened issue: $title"
  fi
}

close_issues() {
  local body="$1" number
  [ "$DRY_RUN" = 1 ] && return
  for number in $(gh issue list --label youtube-broken --state open --json number --jq '.[].number'); do
    gh issue close "$number" --comment "$body" >/dev/null
    log "closed issue #$number"
  done
}

# commits pom.xml on a branch, opens a PR and merges it, then resyncs master
merge_change() {
  local branch="$1" title="$2" body="$3" label="$4"
  if [ "$DRY_RUN" = 1 ]; then log "[dry-run] would open + merge PR: $title"; git checkout -q -- pom.xml; return; fi
  gh_ensure_label "$label" 0e8a16
  git checkout -q -B "$branch"
  git -c user.name="jankbot-updater" -c user.email="jankbot-updater@users.noreply.github.com" commit -q -am "$title"
  git -c credential.helper='!gh auth git-credential' push -q -f origin "$branch"
  gh pr create --base master --head "$branch" --title "$title" --label "$label" --body "$body" >/dev/null
  gh pr merge "$branch" --squash --delete-branch >/dev/null
  git checkout -q master
  git fetch -q origin
  git reset -q --hard origin/master
  log "merged PR: $title"
}

# --- deploy helpers ---
bot_status() { docker compose exec -T jankbot cat /tmp/jankbot-status 2>/dev/null || true; }

wait_for_idle() {
  local waited=0 playing
  while [ "$waited" -lt "$MAX_IDLE_WAIT" ]; do
    playing="$(bot_status | sed -n 's/^playing=//p')"
    [ -z "$playing" ] || [ "$playing" = 0 ] && return
    [ "$waited" = 0 ] && log "music is playing in $playing server(s), waiting for it to stop"
    sleep 60
    waited=$((waited + 60))
  done
  log "still playing after $((MAX_IDLE_WAIT / 60)) minutes, restarting anyway"
}

wait_for_healthy() {
  local waited=0 health
  while [ "$waited" -lt "$HEALTHY_TIMEOUT" ]; do
    health="$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q jankbot)" 2>/dev/null || true)"
    [ "$health" = healthy ] && return 0
    sleep 10
    waited=$((waited + 10))
  done
  return 1
}

# deploy <jankbot image> <cipher image>; rolls back and returns 1 if the bot doesn't become healthy
deploy() {
  local bot_image="$1" cipher_image="$2"
  if [ "$DRY_RUN" = 1 ]; then log "[dry-run] would deploy $bot_image + $cipher_image"; return 0; fi
  if docker image inspect jankbot:current >/dev/null 2>&1; then
    wait_for_idle
    docker tag jankbot:current jankbot:previous
    docker tag jankbot-yt-cipher:current jankbot-yt-cipher:previous
  fi
  docker tag "$bot_image" jankbot:current
  docker tag "$cipher_image" jankbot-yt-cipher:current
  log "deploying $bot_image + $cipher_image"
  docker compose up -d --remove-orphans
  if wait_for_healthy; then
    log "deploy healthy"
    return 0
  fi
  log "deploy unhealthy, rolling back"
  docker compose logs --tail 50 jankbot || true
  if docker image inspect jankbot:previous >/dev/null 2>&1; then
    docker tag jankbot:previous jankbot:current
    docker tag jankbot-yt-cipher:previous jankbot-yt-cipher:current
    docker compose up -d
  fi
  return 1
}

# ================= main =================

git fetch -q origin
git checkout -q master
git reset -q --hard origin/master
HEAD_SHA="$(git rev-parse HEAD)"
CURRENT_VERSION="$(pom_version)"
[ -n "$CURRENT_VERSION" ] || die "could not read youtube.source.version from pom.xml"

docker pull -q "$CIPHER_UPSTREAM" >/dev/null
NEW_CIPHER_ID="$(docker image inspect -f '{{.Id}}' "$CIPHER_UPSTREAM")"
docker tag "$CIPHER_UPSTREAM" "jankbot-yt-cipher:${NEW_CIPHER_ID#sha256:}"
NEW_CIPHER="jankbot-yt-cipher:${NEW_CIPHER_ID#sha256:}"
CURRENT_CIPHER_ID="$(docker image inspect -f '{{.Id}}' jankbot-yt-cipher:current 2>/dev/null || true)"
CIPHER_CANDIDATES="$NEW_CIPHER"
if [ -n "$CURRENT_CIPHER_ID" ] && [ "$CURRENT_CIPHER_ID" != "$NEW_CIPHER_ID" ]; then
  CIPHER_CANDIDATES="$NEW_CIPHER jankbot-yt-cipher:current"
fi

log "deployed youtube-source: $CURRENT_VERSION, master: ${HEAD_SHA:0:10}"

RELEASE="$(latest_release)"
[ -n "$RELEASE" ] || die "could not read latest youtube-source release"

# 1. a new release (preferred over snapshots, so a snapshot deployment moves back to releases)
if [ "$RELEASE" != "$CURRENT_VERSION" ] && ! recently_failed "$RELEASE"; then
  log "new youtube-source release $RELEASE"
  if try_version "$RELEASE"; then
    TAG="$(image_tag "$RELEASE")"
    sed -i "s:<youtube.source.version>.*</youtube.source.version>:<youtube.source.version>$RELEASE</youtube.source.version>:" pom.xml
    merge_change "auto/youtube-source-$RELEASE" "Update youtube-source to $RELEASE" \
      "$(printf 'Automated update from `%s` to release `%s`.\n\nSmoke test:\n```\n%s\n```\n' "$CURRENT_VERSION" "$RELEASE" "$SMOKE_OUTPUT")" auto-update
    # the merge commit changes HEAD, retag the tested image to the new commit's tag
    docker tag "jankbot:$TAG" "jankbot:$(image_tag "$RELEASE")"
    if deploy "jankbot:$(image_tag "$RELEASE")" "$PASSED_CIPHER"; then
      state_set deployed "$(git rev-parse HEAD)"
      state_set last-check "$(now)"
      close_issues "Fixed by youtube-source $RELEASE."
      exit 0
    fi
    open_issue "Deploy of youtube-source $RELEASE failed health check" \
      "$(printf 'The smoke test passed but the bot did not become healthy, rolled back.\n\nRevert the version bump if this keeps happening.\n')"
    exit 1
  fi
  log "release $RELEASE failed the smoke test"
  state_set "failed-$RELEASE" "$(now)"
  RELEASE_FAILED=1
fi

# 2. re-test the deployed version when something changed or the daily check is due
LAST_CHECK="$(state_get last-check)"
NEEDS_CHECK=0
[ "$FORCE_CHECK" = 1 ] && NEEDS_CHECK=1
[ "$(state_get deployed)" != "$HEAD_SHA" ] && NEEDS_CHECK=1
[ "$CURRENT_CIPHER_ID" != "$NEW_CIPHER_ID" ] && NEEDS_CHECK=1
[ -z "$LAST_CHECK" ] || [ $(( $(now) - LAST_CHECK )) -ge "$HEALTH_CHECK_INTERVAL" ] && NEEDS_CHECK=1
[ "${RELEASE_FAILED:-0}" = 1 ] && NEEDS_CHECK=1

if [ "$NEEDS_CHECK" = 0 ]; then
  log "nothing to do"
  exit 0
fi

CURRENT_FAILURE=""
if try_version "$CURRENT_VERSION"; then
  TAG="$(image_tag "$CURRENT_VERSION")"
  state_set last-check "$(now)"
  # redeploy if master or yt-cipher produced a different image, or nothing is deployed yet
  if [ "$(docker image inspect -f '{{.Id}}' "jankbot:$TAG")" != "$(docker image inspect -f '{{.Id}}' jankbot:current 2>/dev/null || true)" ] \
      || [ "$(docker image inspect -f '{{.Id}}' "$PASSED_CIPHER")" != "$CURRENT_CIPHER_ID" ]; then
    deploy "jankbot:$TAG" "$PASSED_CIPHER" || {
      open_issue "Deploy of master ${HEAD_SHA:0:10} failed health check" \
        "The smoke test passed but the bot did not become healthy, rolled back to the previous image."
      exit 1
    }
  fi
  state_set deployed "$HEAD_SHA"
  close_issues "YouTube playback is working again with youtube-source $CURRENT_VERSION."
  log "deployed version $CURRENT_VERSION is working"
  exit 0
fi
if [ "$BUILD_FAILED" = 1 ]; then
  open_issue "master ${HEAD_SHA:0:10} does not build" \
    "deploy/update.sh could not build master, the current deployment was left running. Run \`docker build .\` to see the error."
  exit 1
fi
CURRENT_FAILURE="$SMOKE_OUTPUT"
log "deployed version $CURRENT_VERSION failed the smoke test, trying latest snapshot"

# 3. youtube-source main snapshot
SNAPSHOT="$(latest_snapshot)"
if [ -n "$SNAPSHOT" ] && [ "$SNAPSHOT" != "$CURRENT_VERSION" ] && ! recently_failed "$SNAPSHOT"; then
  if try_version "$SNAPSHOT"; then
    TAG="$(image_tag "$SNAPSHOT")"
    sed -i "s:<youtube.source.version>.*</youtube.source.version>:<youtube.source.version>$SNAPSHOT</youtube.source.version>:" pom.xml
    merge_change "auto/youtube-source-${SNAPSHOT:0:10}" "Update youtube-source to snapshot ${SNAPSHOT:0:10}" \
      "$(printf 'Automated update from `%s` to unreleased snapshot [`%s`](https://github.com/%s/commit/%s), because the deployed version and the latest release (`%s`) fail the smoke test. Will move back to a release once one passes.\n\nDeployed version smoke test:\n```\n%s\n```\nSnapshot smoke test:\n```\n%s\n```\n' \
        "$CURRENT_VERSION" "$SNAPSHOT" "$SOURCE_REPO" "${SNAPSHOT%-SNAPSHOT}" "$RELEASE" "$CURRENT_FAILURE" "$SMOKE_OUTPUT")" snapshot
    docker tag "jankbot:$TAG" "jankbot:$(image_tag "$SNAPSHOT")"
    if deploy "jankbot:$(image_tag "$SNAPSHOT")" "$PASSED_CIPHER"; then
      state_set deployed "$(git rev-parse HEAD)"
      state_set last-check "$(now)"
      close_issues "Fixed by youtube-source snapshot ${SNAPSHOT:0:10}."
      exit 0
    fi
    open_issue "Deploy of youtube-source snapshot ${SNAPSHOT:0:10} failed health check" \
      "The smoke test passed but the bot did not become healthy, rolled back."
    exit 1
  fi
  state_set "failed-$SNAPSHOT" "$(now)"
fi

# 4. nothing works
state_set last-check "$(now)"
open_issue "YouTube playback is broken" "$(printf 'No youtube-source version passed the smoke test, the current deployment (`%s`) was left running.\n\nTried: deployed `%s`, release `%s`, snapshot `%s`.\n\nLast smoke test:\n```\n%s\n```\n\nIf only some clients fail, try changing `JANKBOT_YOUTUBE_CLIENTS` in `.env`.\n' \
  "$CURRENT_VERSION" "$CURRENT_VERSION" "$RELEASE" "${SNAPSHOT:-none}" "${SMOKE_OUTPUT:-$CURRENT_FAILURE}")"
exit 1
