#!/usr/bin/env bash
# Keeps jankbot's YouTube playback working. Run hourly by jankbot-update.timer:
#   1. if there's a new youtube-source release, smoke test it and deploy it if it works
#   2. once a day (or when that release failed), smoke test the deployed version. if it's broken,
#      smoke test the latest youtube-source main snapshot and deploy that if it works
#   3. version changes are merged as PRs so master always matches what's deployed.
#      if nothing works, a "youtube-broken" issue is opened, and closed once something works again
# Deploys also pick up the latest yt-cipher image (which the smoke test runs against).
#
# Usage: deploy/update.sh [--dry-run] [--no-github] [--force-check]
#   --dry-run      build and smoke test, but don't push, open PRs/issues or deploy
#   --no-github    deploy, but only log PRs/issues (for testing, or before GH_TOKEN is set up)
#   --force-check  re-test the deployed version now instead of waiting for the daily check
#
# Requires: git, docker (compose plugin), gh, curl. GH_TOKEN must be set for PRs/issues.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${JANKBOT_STATE_DIR:-$HOME/.local/state/jankbot-updater}"
CIPHER_IMAGE="ghcr.io/kikkia/yt-cipher:master"
RELEASES_METADATA="https://maven.lavalink.dev/releases/dev/lavalink/youtube/v2/maven-metadata.xml"
SNAPSHOTS_METADATA="https://maven.lavalink.dev/snapshots/dev/lavalink/youtube/v2/maven-metadata.xml"
SOURCE_REPO="lavalink-devs/youtube-source"
DAILY=$((24 * 3600))
# a version that failed is retried after this long (youtube may stop blocking it)
RETRY_FAILED_AFTER=$((12 * 3600))

DRY_RUN=0
NO_GITHUB=0
FORCE_CHECK=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1; NO_GITHUB=1 ;;
    --no-github) NO_GITHUB=1 ;;
    --force-check) FORCE_CHECK=1 ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

log() { echo "[$(date '+%F %T')] $*"; }
die() { log "ERROR: $*"; exit 1; }
now() { date +%s; }
state_get() { cat "$STATE_DIR/$1" 2>/dev/null || true; }
state_set() { [ "$DRY_RUN" = 1 ] || echo "$2" > "$STATE_DIR/$1"; }

mkdir -p "$STATE_DIR"
exec 9>"$STATE_DIR/lock"
flock -n 9 || { log "another update is running, exiting"; exit 0; }

cd "$REPO_DIR"
trap cleanup EXIT
[ -f .env ] || die ".env missing, copy .env.example and set CIPHER_TOKEN"
set -a; . ./.env; set +a
[ -n "${CIPHER_TOKEN:-}" ] || die "CIPHER_TOKEN not set in .env"
[ "$NO_GITHUB" = 1 ] || [ -n "${GH_TOKEN:-}" ] || die "GH_TOKEN not set"

# --- youtube-source versions ---
pom_version() { sed -n 's:.*<youtube.source.version>\(.*\)</youtube.source.version>.*:\1:p' pom.xml; }
set_pom_version() { sed -i "s:<youtube.source.version>.*</youtube.source.version>:<youtube.source.version>$1</youtube.source.version>:" pom.xml; }
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
  [ -n "$failed_at" ] && [ $(( $(now) - failed_at )) -lt "$RETRY_FAILED_AFTER" ]
}

# keeps disk use flat: each run builds/pulls images and grows the build cache
cleanup() {
  [ "$DRY_RUN" = 1 ] && return
  # untag test builds of other versions, the running version stays tagged jankbot:current
  docker images jankbot --format '{{.Repository}}:{{.Tag}}' | grep -v ':current$' | xargs -r docker rmi >/dev/null 2>&1 || true
  # removes those and old yt-cipher images (images used by containers are kept)
  docker image prune -f >/dev/null 2>&1 || true
  docker builder prune -f --max-used-space 2gb >/dev/null 2>&1 || true
}

# --- build + smoke test ---
image() { echo "jankbot:$(echo "$1" | cut -c1-12)"; }

SMOKE_OUTPUT=""
# builds the bot with the given youtube-source version and checks youtube playback works with it
smoke_test() {
  local version="$1"
  log "building and smoke testing youtube-source $version"
  if ! docker build -q --build-arg "YT_SOURCE_VERSION=$version" -t "$(image "$version")" . >/dev/null; then
    SMOKE_OUTPUT="docker build failed"
    log "build failed"
    return 1
  fi
  docker network inspect jankbot-smoketest >/dev/null 2>&1 || docker network create jankbot-smoketest >/dev/null
  docker rm -f jankbot-smoketest-cipher >/dev/null 2>&1 || true
  docker run -d --rm --name jankbot-smoketest-cipher --network jankbot-smoketest --network-alias yt-cipher \
    -e OVERRIDE_PLAYER_VARIANT=IAS -e "API_TOKEN=$CIPHER_TOKEN" "$CIPHER_IMAGE" >/dev/null
  sleep 5
  # config.json is mounted so the smoke test uses the bot's settings (eg. the youtube login)
  SMOKE_OUTPUT="$(docker run --rm --network jankbot-smoketest -v "$REPO_DIR/config.json:/config/config.json:ro" \
    -e JANKBOT_REMOTE_CIPHER_URL=http://yt-cipher:8001 -e "JANKBOT_REMOTE_CIPHER_PASSWORD=$CIPHER_TOKEN" \
    -e "JANKBOT_YOUTUBE_CLIENTS=${JANKBOT_YOUTUBE_CLIENTS:-}" \
    "$(image "$version")" janktastic.jankbot.SmokeTest 2>&1 | grep -E '^SMOKETEST|^Client \[')" || true
  docker rm -f jankbot-smoketest-cipher >/dev/null 2>&1 || true
  echo "$SMOKE_OUTPUT"
  grep -q '^SMOKETEST PASSED' <<<"$SMOKE_OUTPUT"
}

deploy() {
  if [ "$DRY_RUN" = 1 ]; then log "[dry-run] would deploy youtube-source $1"; return; fi
  log "deploying youtube-source $1"
  docker tag "$(image "$1")" jankbot:current
  docker compose up -d
}

# --- github ---
open_issue() {
  local title="$1" body="$2" existing
  if [ "$NO_GITHUB" = 1 ]; then log "[no-github] would open issue: $title"; return; fi
  gh label create youtube-broken --color d73a4a --force >/dev/null 2>&1 || true
  existing="$(gh issue list --label youtube-broken --state open --json number --jq '.[0].number')"
  if [ -n "$existing" ]; then
    gh issue comment "$existing" --body "$body" >/dev/null
  else
    gh issue create --title "$title" --label youtube-broken --body "$body" >/dev/null
  fi
  log "opened/updated issue: $title"
}

close_issues() {
  local number
  [ "$NO_GITHUB" = 1 ] && return
  for number in $(gh issue list --label youtube-broken --state open --json number --jq '.[].number'); do
    gh issue close "$number" --comment "$1" >/dev/null
    log "closed issue #$number"
  done
}

# commits the pom.xml version bump on a branch, opens a PR and merges it
merge_version_bump() {
  local branch="$1" title="$2" body="$3"
  if [ "$NO_GITHUB" = 1 ]; then log "[no-github] would open + merge PR: $title"; return; fi
  git checkout -q -B "$branch"
  git -c user.name="jankbot-updater" -c user.email="jankbot-updater@users.noreply.github.com" commit -q -am "$title"
  git -c credential.helper='!gh auth git-credential' push -q -f origin "$branch"
  gh pr create --base master --head "$branch" --title "$title" --body "$body" >/dev/null
  gh pr merge "$branch" --squash --delete-branch >/dev/null
  git checkout -q master
  git fetch -q origin
  git reset -q --hard origin/master
  log "merged PR: $title"
}

# switches to a version that passed the smoke test
update_to() {
  local version="$1" title="$2" body="$3"
  set_pom_version "$version"
  merge_version_bump "auto/youtube-source-$(echo "$version" | cut -c1-12)" "$title" \
    "$(printf '%s\n\nSmoke test:\n```\n%s\n```\n' "$body" "$SMOKE_OUTPUT")"
  [ "$DRY_RUN" = 1 ] && git checkout -q -- pom.xml
  deploy "$version"
  state_set last-check "$(now)"
  close_issues "Fixed by youtube-source $version."
}

# ================= main =================

git fetch -q origin
git checkout -q master
git reset -q --hard origin/master
docker pull -q "$CIPHER_IMAGE" >/dev/null

CURRENT="$(pom_version)"
RELEASE="$(latest_release)"
[ -n "$CURRENT" ] || die "could not read youtube.source.version from pom.xml"
[ -n "$RELEASE" ] || die "could not read the latest youtube-source release"
log "deployed: youtube-source $CURRENT, latest release: $RELEASE"

# 1. new release (also moves a snapshot deployment back to releases)
RELEASE_FAILED=0
if [ "$RELEASE" != "$CURRENT" ] && ! recently_failed "$RELEASE"; then
  if smoke_test "$RELEASE"; then
    update_to "$RELEASE" "Update youtube-source to $RELEASE" "Automated update from \`$CURRENT\` to release \`$RELEASE\`."
    exit 0
  fi
  log "release $RELEASE failed the smoke test"
  state_set "failed-$RELEASE" "$(now)"
  RELEASE_FAILED=1
fi

# 2. daily check of the deployed version
LAST_CHECK="$(state_get last-check)"
if [ "$FORCE_CHECK" = 0 ] && [ "$RELEASE_FAILED" = 0 ] && docker image inspect jankbot:current >/dev/null 2>&1 \
    && [ -n "$LAST_CHECK" ] && [ $(( $(now) - LAST_CHECK )) -lt "$DAILY" ]; then
  log "nothing to do"
  exit 0
fi
state_set last-check "$(now)"
if smoke_test "$CURRENT"; then
  # first run: nothing deployed yet
  docker image inspect jankbot:current >/dev/null 2>&1 || deploy "$CURRENT"
  close_issues "YouTube playback is working again with youtube-source $CURRENT."
  log "youtube-source $CURRENT is working"
  exit 0
fi
CURRENT_FAILURE="$SMOKE_OUTPUT"
log "deployed youtube-source $CURRENT is broken, trying the latest snapshot"

# 3. latest youtube-source main snapshot
SNAPSHOT="$(latest_snapshot)"
if [ -n "$SNAPSHOT" ] && [ "$SNAPSHOT" != "$CURRENT" ] && ! recently_failed "$SNAPSHOT"; then
  if smoke_test "$SNAPSHOT"; then
    update_to "$SNAPSHOT" "Update youtube-source to snapshot ${SNAPSHOT:0:10}" \
      "Automated update from \`$CURRENT\` to unreleased snapshot [\`${SNAPSHOT:0:10}\`](https://github.com/$SOURCE_REPO/commit/${SNAPSHOT%-SNAPSHOT}), because the deployed version and release \`$RELEASE\` fail the smoke test. The next release that passes replaces it."
    exit 0
  fi
  state_set "failed-$SNAPSHOT" "$(now)"
fi

# 4. nothing works, keep what's running
open_issue "YouTube playback is broken" "$(printf 'No youtube-source version passed the smoke test, `%s` was left running.\n\nTried: deployed `%s`, release `%s`, snapshot `%s`.\n\nDeployed version smoke test:\n```\n%s\n```\n\nIf only some clients fail, try changing `JANKBOT_YOUTUBE_CLIENTS` in `.env`.\n' \
  "$CURRENT" "$CURRENT" "$RELEASE" "${SNAPSHOT:-none}" "$CURRENT_FAILURE")"
exit 1
