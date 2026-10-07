# Deploying jankbot

jankbot runs as a Docker Compose stack (bot + [yt-cipher](https://github.com/kikkia/yt-cipher)) inside a Proxmox LXC container.
`deploy/update.sh` runs hourly from a systemd timer and keeps YouTube playback working:

- tests every new youtube-source release, and falls back to the latest youtube-source `main` snapshot when the deployed version and the release are both broken
- only deploys versions that pass a real playback smoke test (`janktastic.jankbot.SmokeTest`), and waits for music to stop first
- records every version change as an auto-merged PR, so `master` always matches what is deployed
- redeploys when you push to `master`
- opens a `youtube-broken` GitHub issue when nothing works, and closes it once playback recovers

## 1. Create the container (on the Proxmox host)

```sh
pveam update
pveam available --section system | grep debian-13      # note the exact template name
pveam download local debian-13-standard_13.1-2_amd64.tar.zst

pct create 120 local:vztmpl/debian-13-standard_13.1-2_amd64.tar.zst \
  --hostname jankbot \
  --unprivileged 1 --features nesting=1,keyctl=1 \
  --cores 2 --memory 2048 --swap 512 \
  --rootfs local-lvm:16 \
  --net0 name=eth0,bridge=vmbr0,ip=dhcp \
  --onboot 1 --password
pct start 120
pct enter 120
```

`nesting=1,keyctl=1` are required for Docker inside an unprivileged container. Change the ID (`120`), storage and bridge to match your setup.

## 2. Install Docker, gh and git (inside the container, as root)

```sh
apt update && apt full-upgrade -y
apt install -y ca-certificates curl git openssl

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg -o /etc/apt/keyrings/githubcli.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli.gpg] https://cli.github.com/packages stable main" \
  > /etc/apt/sources.list.d/github-cli.list
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin gh

docker run --rm hello-world     # confirms Docker works inside the LXC
```

## 3. Create the jankbot user (inside the container, as root)

```sh
adduser --gecos "" jankbot      # set a password, used once for ssh-copy-id
usermod -aG docker jankbot
hostname -I                     # note the IP
```

From your dev machine:

```sh
ssh-copy-id jankbot@<container-ip>
ssh jankbot@<container-ip>      # should log in without a password
```

Optionally disable SSH password logins afterwards (as root in the container):

```sh
echo 'PasswordAuthentication no' > /etc/ssh/sshd_config.d/no-passwords.conf
systemctl reload ssh
```

## 4. GitHub token

Create a fine-grained token at https://github.com/settings/personal-access-tokens/new:

- **Repository access:** only `Janktastic/jankbot`
- **Permissions:** Contents: read & write, Pull requests: read & write, Issues: read & write
- **Expiration:** the updater stops opening PRs/issues when it expires, so pick a long one and set a reminder

## 5. Configure the bot (as jankbot)

```sh
git clone https://github.com/Janktastic/jankbot.git ~/jankbot
cd ~/jankbot

cp .env.example .env
sed -i "s/^CIPHER_TOKEN=.*/CIPHER_TOKEN=$(openssl rand -hex 24)/" .env

cp exampleConfig.json config.json
nano config.json                # discordBotToken, googleApiKey, commandPrefix

mkdir -p ~/.config
echo 'GH_TOKEN=github_pat_...' > ~/.config/jankbot-updater.env
chmod 600 .env config.json ~/.config/jankbot-updater.env
```

### Optional: YouTube login for age restricted videos

Age restricted videos only play with a signed-in YouTube account. **Use a throwaway Google account**: YouTube may ban accounts used by bots. First check that the account can play an age restricted video in a browser (YouTube may ask it to verify its age).

Run this after the first deploy (step 6), since it uses the built image:

```sh
docker run --rm -it jankbot:current janktastic.jankbot.YoutubeLogin
```

Open the URL it prints, enter the code and sign in with the throwaway account. Put the printed `youtubeOauthRefreshToken` in `config.json`, then `docker compose up -d --force-recreate jankbot`. The login is one-time: the token doesn't expire unless Google revokes it. If it stops working, the bot logs `YouTube login failed` and keeps playing everything except age restricted videos.

In the [Discord developer portal](https://discord.com/developers/applications), enable **Message Content Intent** for the bot (Bot → Privileged Gateway Intents). Without it, commands are silently ignored.

## 6. First run (as jankbot)

```sh
cd ~/jankbot
set -a; . ~/.config/jankbot-updater.env; set +a
./deploy/update.sh --dry-run    # builds and smoke tests, changes nothing
./deploy/update.sh              # builds, tests and starts the bot
docker compose ps               # jankbot should become "healthy" within ~2 minutes
```

## 7. Enable hourly updates (as root)

```sh
cp /home/jankbot/jankbot/deploy/jankbot-update.{service,timer} /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now jankbot-update.timer
systemctl list-timers jankbot-update.timer
```

## Day to day

```sh
journalctl -u jankbot-update -f                        # updater runs
cd ~/jankbot && docker compose logs -f jankbot         # bot logs
systemctl start jankbot-update                         # run the updater now (as root)
./deploy/update.sh --force-check                       # re-test the deployed version now
```

If only some YouTube clients are failing, set `JANKBOT_YOUTUBE_CLIENTS` in `.env` (comma separated class names from `dev.lavalink.youtube.clients`, see `AudioSetup.java` for the default) then run `docker compose up -d`. The updater's smoke test uses the same setting.
