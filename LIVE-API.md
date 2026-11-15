# City Drive — live view API

A tiny, token-protected API next to the game, so an operator (or the agent) can
watch a running session, take a snapshot and poke it.

```
node server.js          # http://0.0.0.0:3000  (PORT to override)
```

The game page and the API are served by the same process. The token is created on
first run in `.live-token` (mode 600) or taken from `CITY_TOKEN`.

Operator endpoints (send the token as `x-city-token: <token>` or `?token=<token>`):

| Method | Path        | What it does |
| ------ | ----------- | ------------ |
| GET    | `/api/live` | every session: position, speed, fps, hd, night, mode (car/walk), engineOn, paint, online flag, last snapshot |
| POST   | `/api/cmd`  | queue a command for one session (`{"id": "...", "cmd": {...}}`) or all of them |
| GET    | `/api/shot` | latest JPEG/PNG snapshot uploaded by the game |

The page also exposes `window.__cd.state` and `window.__cd.shot()` for direct poking from devtools.

Game-side endpoints (same origin, no token): `POST /api/hb`, `GET /api/cmd?id=`,
`POST /api/shot`.

Audio is engine + road noise generated in the page (WebAudio), toggled with the 🔊 button or `M` for the country map.

Commands:

| Command | Effect |
| ------- | ------ |
| `{"t":"shot"}` | render one frame and upload it |
| `{"t":"tp","x":0,"z":-1500,"h":3.14}` | teleport the car, optionally facing `h` radians |
| `{"t":"night","v":1}` | jump to night (0 = day) |
| `{"t":"mode","v":"walk"}` | get the driver out (`"car"` to get back in) |
| `{"t":"keys","gas":true,"left":false}` | press/release the driving controls directly (steer, throttle, brake) |
| `{"t":"release"}` | let go of everything |
| `{"t":"hd","on":false}` | toggle HD/SD |
| `{"t":"pause"}` / `{"t":"resume"}` | stop / start the physics step |

```bash
T=$(cat .live-token)
curl -s -H "x-city-token: $T" localhost:3000/api/live
curl -s -H "x-city-token: $T" -H 'content-type: application/json' \
     -d '{"cmd":{"t":"shot"}}' localhost:3000/api/cmd
curl -s -H "x-city-token: $T" -o live.jpg localhost:3000/api/shot
```

The token is a shared demo secret, not real auth: it is baked into the page so the
page can register itself, and it keeps the operator endpoints from being open to
anyone who finds the URL. Anyone who can open the game can read it.
