# HattrickOrganizer (HO!)

Desktop client for the online football manager [Hattrick](https://www.hattrick.org).
User documentation: <https://ho.readthedocs.io/>. Developer notes: [AGENTS.md](AGENTS.md).

```bash
./gradlew run      # run HO from source
./gradlew test     # unit tests
```

## Headless MCP server

HO can run without a window and serve only its MCP tools, so an AI agent can manage the team
from a Linux server. It is the normal HO jar started with a different main class
(`core.HeadlessMain`). Two ways to ship it: a **Docker image** (recommended, brings its own Java)
or a **tarball** for a server with Java 17+ installed.

### Docker

The database lives in a plain folder on the server (`data/`, bind-mounted to `/data`), so it is
easy to back up, and you can move your existing desktop database there and keep all its history.

#### 1. Build the image

On a machine with the repo, JDK 17 and Docker:

```bash
./gradlew docker                                  # image for this machine's CPU
./gradlew docker -PdockerPlatform=linux/amd64     # for an x86 server, e.g. built on Apple silicon
```

This builds `ho-headless:10.0`, also tagged `latest`, on Eclipse Temurin 17 JRE.

#### 2. Get image and compose file onto the server

**Through the registry** `docker.rayba.de`: one multi-arch image (amd64 + arm64), so the server
pulls the right one whatever machine built it.

```bash
docker login docker.rayba.de                      # once, on the build machine and on the server
./gradlew dockerPush                              # builds and pushes docker.rayba.de/ho-headless:10.0 and :latest

ssh server mkdir -p ho/data
scp utils/buildResources/docker-compose.yml server:ho/
ssh server "echo HO_IMAGE=docker.rayba.de/ho-headless:latest >> ho/.env"
```

`-PdockerRegistry=host[:port]` pushes somewhere else.

**Or without a registry**, copy the locally built image:

```bash
docker save ho-headless:10.0 ho-headless:latest | gzip | ssh server 'gunzip | docker load'
ssh server mkdir -p ho/data
scp utils/buildResources/docker-compose.yml server:ho/
```

On the server the layout is:

```
ho/
├── docker-compose.yml
├── .env            # optional, see below
└── data/           # HO's data folder: users.json, db/, logs/
```

The container runs as the owner of `data/`. Compose assumes uid/gid 1000; if `id -u` / `id -g`
print something else for the user owning `ho/data`, put it in `ho/.env`:

```bash
printf 'HO_UID=%s\nHO_GID=%s\n' "$(id -u)" "$(id -g)" > ho/.env
```

#### 3a. Move your live desktop database (keeps the history)

Quit desktop HO first, so the database is closed. On the Mac, pack the data folder (installed HO
keeps it in `~/Library/Application Support/HO`; on Linux it is `~/.ho`, on Windows `%AppData%\HO`):

```bash
tar -czf ho-data.tgz -C ~/"Library/Application Support/HO" users.json db img
scp ho-data.tgz server:ho/
ssh server 'cd ho && tar -xzf ho-data.tgz -C data && rm ho-data.tgz'
```

The database is upgraded automatically on first start if the desktop HO was an older version.
The access token is stored in the database, so no `authorize` is needed.

From then on the server is the database's home. Desktop HO still has the old copy, which stops
being updated: don't keep using both, or they drift apart.

#### 3b. Or start fresh

Without a database to move, connect to Hattrick once:

```bash
cd ho && docker compose run --rm ho authorize
```

Open the printed `https://chpp.hattrick.org/oauth/authorize.aspx?...` link on any device, log in
and grant access, then paste the code Hattrick shows. HO stores the token in `data/`, downloads
your team and exits. Several teams: `HO_JAVA_OPTS=-Dho.teamId=<teamId> docker compose run --rm ho authorize`.

#### 4. Run

```bash
cd ho
docker compose up -d          # start, and again after every reboot (restart: unless-stopped)
docker compose logs -f        # follow the log (-t adds timestamps); Docker keeps 3 x 10 MB of it,
                              # the full log with timestamps is in data/logs/
docker compose down           # stop - closes the database cleanly
```

The compose file publishes the port as `127.0.0.1:8765`, the server's loopback only. Keep it that
way: the server has no authentication. To update: `./gradlew dockerPush` on the build machine,
then `docker compose pull && docker compose up -d` on the server (or load the new image and
`docker compose up -d`). The data stays in `data/`. To back up, copy `data/` while the container is stopped.
HO also zips the database into `data/db/` on every start.

Then connect the agent through an SSH tunnel, as described in
[Use it from an agent](#use-it-from-an-agent).

**Without compose**, the same container is:

```bash
docker run -d --name ho --restart unless-stopped --user "$(id -u):$(id -g)" \
  -v "$PWD/data":/data -p 127.0.0.1:8765:8765 ho-headless:latest
```

### Tarball (without Docker)

#### Build

On a machine with the repo checked out (JDK 17+):

```bash
./gradlew headless
```

This writes `build/headless/HO-headless-10.0.tar.gz`, which holds HO.jar, its libraries,
`prediction/`, `truststore.jks` and the start script `ho-headless.sh`.

#### Install on the server

The server needs a Java 17+ runtime; no display or desktop packages are needed.

```bash
sudo apt install openjdk-17-jre-headless        # Debian/Ubuntu; any JRE 17+ works

scp build/headless/HO-headless-10.0.tar.gz server:
ssh server
tar -xzf HO-headless-10.0.tar.gz                 # unpacks into ./HO
```

HO keeps its database (`db/`), `logs/` and `users.json` inside that `HO` folder.

#### Connect to Hattrick (once)

```bash
./HO/ho-headless.sh authorize
```

1. The command prints a `https://chpp.hattrick.org/oauth/authorize.aspx?...` link.
2. Open it in a browser on any device, log in to Hattrick and grant HO access.
3. Hattrick shows a code. Paste it at the `Code shown by Hattrick:` prompt.
4. HO stores the access token in its database and downloads your team, then exits.

If your account has several teams it takes the primary one; pick another with
`JAVA_OPTS=-Dho.teamId=<teamId> ./HO/ho-headless.sh authorize`.

**Moving an existing database instead:** copy `db/` and `users.json` from your desktop HO's data
folder into `HO/` and skip `authorize`. The token is stored in the database, so it comes along.
Where desktop HO keeps its data depends on how you installed it (see AGENTS.md, "Where user data
lives").

#### Run

```bash
./HO/ho-headless.sh                  # MCP on http://127.0.0.1:8765/mcp
./HO/ho-headless.sh --port 9000      # another port
./HO/ho-headless.sh DEBUG            # more logging, in HO/logs/
```

Stop it with Ctrl-C or `kill`. It closes the database cleanly either way. To keep it running
after you log out, run it with systemd, for example `/etc/systemd/system/ho.service`:

```ini
[Unit]
Description=HO headless MCP server
After=network-online.target

[Service]
User=ho
ExecStart=/home/ho/HO/ho-headless.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now ho
```

### Use it from an agent

The server listens on `127.0.0.1` only and has **no authentication**. Do not expose it to the
network. Reach it through an SSH tunnel from the machine the agent runs on:

```bash
ssh -N -L 8765:localhost:8765 server
claude mcp add --transport http ho http://localhost:8765/mcp     # Claude Code
```

Keep `download_team` in the agent's routine: it refreshes the local database from Hattrick, the
same as HO's Download button.

### Things to know

- **One HO per database.** The database is single-process: never open the same `db/` from
  desktop HO and the server at the same time. To look at the data in the GUI, stop the server
  and copy `db/` over.
- **Token expired or revoked?** Tools that talk to Hattrick then fail with an authorization
  error (HTTP 401 in `HO/logs/`). Stop the server and run `authorize` again.
- The Download dialog's match archive and previous-season fixtures are not available headless;
  `download_team` says so in its answer.
