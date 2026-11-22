# Deployment protocol

`ee.schimke.composeai:deployment-protocol` (package `ee.schimke.composeai.deployment.protocol`,
module [`api/deployment-protocol`](../../api/deployment-protocol)) is what every container deployed
beside the preview server agrees on with the tools that deploy it and with its siblings.

## Why it exists

compose-preview-server is being unbundled. The UI builder
([yschimke/compose-ui-builder](https://github.com/yschimke/compose-ui-builder)) and the hosted MCP
([yschimke/compose-preview-mcp](https://github.com/yschimke/compose-preview-mcp)) will ship their
own containers on their own release trains, deployed beside the server. Today the shapes they would
have to agree on — how a deploy tool knows a replica is live and which build it is, what a release
says it needs, how a sibling learns who a browser is — exist only as server-internal code. Each
repository is a different release train, so the agreement goes to layer 0: here.

Shape only, as everything in this repository. Probing, rolling out, verifying a session and
choosing which component release to deploy stay in each implementation.

**Every shape was derived from the server's current wire**, so a server that has not changed a line
already conforms. The goldens in `DeploymentProtocolTest` are the bodies compose-preview-server
3.192.0 sends, and `DeploymentJsonSchemaTest` validates them against the published schemas.

## What is published

| Kotlin type | JSON Schema (`src/main/resources/schemas/`) | what it is |
| --- | --- | --- |
| `DeployableEndpointsV1` | — | the `/healthz`, `/readyz` and `/version` paths and bodies |
| `DeployableVersionV1` | `deployable-version-v1.schema.json` | the `/version` body |
| `ReleaseManifestV1` (+ `ApiRangeV1`, `ReleaseAssetV1`, `ReleaseImageV1`) | `release-manifest-v1.schema.json` | what a component release says about itself |
| `SessionWhoamiV1` | `session-whoami-v1.schema.json` | the auth host's session-introspection reply |
| `DeploymentComponentsV1`, `DeploymentApisV1` | — | well-known component and API names (open vocabularies) |

Every type is on the Builder pattern ([AGENTS.md](../../AGENTS.md)): a new field is a new Builder
`var`, never a constructor parameter. The schemas are **generated from the serializers** by a
test-only copy of ui-builder-protocol's generator and fail the build when stale
(`UPDATE_SCHEMAS=1 ./gradlew :deployment-protocol:test` regenerates them). Unlike ui-builder's,
their objects are **open**: every body here is decoded with `ignoreUnknownKeys = true`, and a
component adds its own fields beside the contract's.

## 1. Deployable endpoints

Every deployable container serves three ungated, secret-free endpoints:

| route | status | body | meaning |
| --- | --- | --- | --- |
| `GET /healthz` | `200` | `ok` | liveness: the listener is up. **Never the rollout gate.** |
| `GET /readyz` | `200` / `503` | `ready` / `warming` | readiness: the component can serve its real traffic. |
| `GET /version` | `200` | `DeployableVersionV1` JSON | identity: which build answered. |

The bodies of `/healthz` and `/readyz` are informational; the status code is the signal.

**`/readyz` latches.** Once a replica reports ready it stays ready for its lifetime; a component
that later degrades says so somewhere else (the preview server's `/status.json`), never by
un-readying, because the rollout gate must not flap a replica out of a load balancer it was
promoted into. **It may also start warming on the first poll**: the preview server starts its
readiness prober lazily, on the first `/readyz` request (`handleReadyz` →
`ensureReadinessProbe`), and renders every design system before latching. A deploy tool
therefore polls `/readyz`; it does not wait for it passively.

### The `/version` body

```json
{"schema":"compose-preview-serve/version/v1","version":"3.192.0","serveSchema":"compose-preview-serve/v3","public":true}
```

That is the preview server's reply today (`VersionResponse` in `ServeHttpServer.kt`, encoded with
`encodeDefaults = true`), and it decodes as a `DeployableVersionV1`:

| field | | |
| --- | --- | --- |
| `schema` | required | `compose-preview/deployable-version/v1` (`SCHEMA`), or the server's historical `compose-preview-serve/version/v1` (`PREVIEW_SERVER_SCHEMA`). Both name this shape; `SCHEMAS` holds both. |
| `version` | required | the released version, as the release tag names it, without a `v` |
| `component` | optional | `preview-server`, `ui-builder`, `catalog-mcp`, … Absent from a server predating this contract, which is always the preview server. |
| `apis` | optional | `Map<String, Int>`: the API levels the running build speaks, e.g. `"ui-builder-editor": 2` |

The server's own `serveSchema` and `public` are server-specific and are ignored by the contract
(unknown keys). The server keeps its schema id; it does not have to move to `SCHEMA` to conform.

**Keep `version` the only string-valued `"version"` key in the body.** The server's rollout loop
reads it with a regular expression over the compacted body rather than a JSON parser, and takes the
last match. Integer API levels in `apis` cannot match it; a nested object with its own
`"version":"…"` would. New deploy tooling parses the JSON.

### How a deploy tool decides convergence

A rollout of component *C* to version *V* has converged when, polling the public address:

1. `GET /version` decodes, its `schema` is in `DeployableVersionV1.SCHEMAS`, its `component` is *C*
   (absent counts as `preview-server`), and its `version` is *V*;
2. `GET /readyz` returns `200`; and
3. both stay true on **consecutive** polls. During a swap two containers can answer one address,
   so a single poll can see the new `/version` and the old replica's `/readyz` (or the reverse).
   Reading them once, from different replicas, is the split the server's own loop guards against.

And, for a release that declares them, every `requires` entry of its `ReleaseManifestV1` is met by
the `apis` of the sibling components already deployed — checked **before** the rollout starts, not
after.

The preview server's `preview-host-image.yml` rollout loop is stricter, and that is
server-specific: it also reads `GET /status.json`, requires that body's `version` to equal *V* as
well (so the two endpoints cannot disagree across a swap), and requires at least one catalog, every
catalog `loaded` or `stale`, and none `failed`. `/status.json` is not part of this contract, and
other components are not required to serve it.

## 2. Release manifest

`ReleaseManifestV1`, published as `compose-preview-release.json` (`ReleaseManifestV1.FILE`) beside
a release's assets and, for a component that ships an archive, at the archive root:

```json
{
  "schema": "compose-preview/release-manifest/v1",
  "component": "ui-builder",
  "version": "3.48.0",
  "provides": { "ui-builder-editor": 2, "ui-builder-root-offline": 1 },
  "requires": { "session-whoami": { "min": 1, "max": 1 } },
  "assets": [ { "name": "compose-preview-ui-builder-web-3.48.0.zip", "sha256": "<64 hex>", "mediaType": "application/zip" } ],
  "image": { "repository": "ghcr.io/yschimke/compose-ui-builder", "tag": "3.48.0", "digest": "sha256:<64 hex>" }
}
```

| field | | |
| --- | --- | --- |
| `schema`, `component`, `version` | required | |
| `provides` | optional | API levels this release speaks; the build-time twin of `/version`'s `apis` |
| `requires` | optional | `Map<String, ApiRangeV1>`, `min` and `max` **inclusive**: met when a sibling provides that API at a level inside the range |
| `assets` | optional | `{name, sha256, mediaType?}`; `sha256` is lower-case hex |
| `image` | optional | `{repository, tag, digest?}`; a deploy tool pins `digest` when present |

### It does not replace `ui-builder-web.json`

The editor archive's `ui-builder-web.json` (`ServeUiBuilderEditor.EditorManifest`,
`compose-ui-builder-web/v1`) stays exactly as it is. The server refuses an editor whose manifest
schema is anything else, so the release manifest is a **second, additive file**, never a new
version of the first. Its fields map:

| `ui-builder-web.json` | `ReleaseManifestV1` |
| --- | --- |
| `schema: compose-ui-builder-web/v1` | `schema: compose-preview/release-manifest/v1`, `component: ui-builder` |
| `version` | `version` |
| `serverApi` | `provides["ui-builder-editor"]` (`DeploymentApisV1.UI_BUILDER_EDITOR`) |
| `rootOffline` (default `0`) | `provides["ui-builder-root-offline"]` (`DeploymentApisV1.UI_BUILDER_ROOT_OFFLINE`) |

The server's `SUPPORTED_SERVER_API` (today `{2}`) becomes, in the server's own release manifest,
`requires["ui-builder-editor"] = {min: 2, max: 2}` if it ever ships one; nothing changes until it
does. The archive's own `sha256` pin in `catalogs.json` is the editor's `assets` entry.

## 3. Session introspection

The preview server already exposes `GET /auth/github/whoami` (`ServeGithubAuth.handleWhoami`):

| status | body | when |
| --- | --- | --- |
| `200` | `{"login":"octocat","guest":false}` | a live signed-in session |
| `401` | `Not signed in.` (text, not part of the contract) | no cookie, a forged or expired one, or more than one session cookie |

always with `Cache-Control: no-store`, and with no CORS headers so another origin's page cannot read
it. It exists so a service sharing the host can reuse the sign-in instead of running its own OAuth
app (the HA card design server already does).

`SessionWhoamiV1` is that reply. A sibling container — the UI builder in its own container —
authenticates a browser like this:

1. The browser's request reaches the sibling through the auth host's public origin (a reverse
   proxy routing by path to the sibling's container), so it carries the session cookie. The cookie
   is host-only (`__Host-`-prefixed when the server is so configured), so a sibling on a different
   host never receives it; that case is the server's cross-host handoff
   (`/auth/github/handoff`), which this contract does not cover.
2. The sibling sends `GET /auth/github/whoami` to the **auth host over the internal network**,
   forwarding the request's `Cookie` header unchanged. It does not need to know the cookie's name,
   and it never sees or holds the cookie secret.
3. `200` → the request speaks for `login`; `guest` says whether it was signed in outside the
   allowlist (absent reads as a member). `401` → anonymous. **Anything else, or no answer → fail
   closed**: the auth host could not say.
4. The sibling does not cache the answer beyond the request it is answering, matching `no-store`.
   `expiresAtEpochSeconds`, when present, may bound the sibling's own short-lived state; it never
   extends a session.

The forward must go to the internal address, never back out through the public origin, and the
sibling must strip any inbound header it would otherwise trust as an identity: the identity is what
whoami said, nothing else.

**Future fields go through the Builder**, as optional fields that a host predating them omits:
`scopes` (an open vocabulary the auth host defines; empty means *unreported*, not *nothing*) and
`expiresAtEpochSeconds`. Both are `@EncodeDefault(NEVER)`, so a server that adopts the type and
sets neither sends exactly today's bytes.

### The bearer-grant variant: out of scope here

`ServeAgentGrants.WHOAMI_PATH` (`GET /agent-access/whoami`) is a different contract: it introspects
an **agent grant** presented as a bearer, not a browser session. Its reply (`WhoamiResponse`:
`active`, `scopes`, `capabilities`, `expiresInSeconds`, `actorId`, `onBehalfOfActorId`,
`designIds`, `reason`, …) answers `200` with `active: false` for a dead grant rather than `401`,
and carries the agent-grant vocabulary `agent-grant-protocol` already owns. A sibling could forward
an `Authorization: Bearer` header to it exactly as it forwards a cookie above, but the reply type
belongs beside `AgentGrantScope` and `AgentGrantCapability` in `agent-grant-protocol`, not here. It
is a follow-up there.

## 4. Follow-up contracts, not yet typed

Named here so later pull requests fill them in. None has a type, schema or API level yet; the
`DeploymentApisV1` names reserved for them are what `provides`/`requires` will cite.

### 4a. Catalog read API (`catalog-read`)

What the UI builder and the hosted catalog MCP read from the preview server: the catalog list (ids,
load state, revision), a catalog's revisions, and its component records. Today these are spread
across `/status.json`'s `catalogList`, `/api/components`, `/api/previews`,
`/api/ui-builder/v1/component-library[/{system}/{componentId}[/file]]`,
`/api/ui-builder/v1/catalogs/{system}/component-record`, `/templates`, `/guidelines` and
`/api/ui-builder/v1/catalog-capabilities/{file}`. The record shapes are largely
`component-catalog-protocol`'s already; what is missing is the read surface's routes, paging and
revision addressing.

### 4b. Editor ↔ host route set (`ui-builder-editor`)

The `/api/ui-builder/*` routes the editor calls on its host — designs, comments, guidelines,
references, assets, agents, exports, thumbnails. Its level is already on the wire as
`ui-builder-web.json`'s `serverApi` (2 today).

**Partly typed**: [UI_BUILDER_HTTP_ROUTES.md](UI_BUILDER_HTTP_ROUTES.md) writes down the routes
ha-designs calls — the envelope route, a design's `GET`/`PUT`, the two Remote Compose exports, the
identity route, the admin design listing and the editor link — with their credentials, statuses
and error shapes, and how the `ui-builder-editor` level versions them. The types
(`UiBuilderHttpRoutesV1`, `UiBuilderHttpCredentialsV1`, `UiBuilderIdentityV1`,
`UiBuilderAdminDesignsV1`, …) are in `ui-builder-protocol` rather than here, because the Wasm editor
is a client and this module is JVM only. The editor's other routes are listed there by name, not
yet typed.

### 4c. Render / export request (`render-export`)

What a sibling sends the preview server to render or export a design it does not host:
`POST /api/ui-builder/v1/documents/export.{png,svg,compose}` and
`GET /api/ui-builder/v1/designs/{designId}/export.*` today. To be typed when the UI builder's
container stops sharing a process with the renderer.
