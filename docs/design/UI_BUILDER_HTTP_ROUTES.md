# UI builder HTTP routes

The HTTP routes of the UI builder that a client outside the editor calls, typed in
`ee.schimke.composeai:ui-builder-protocol` (`HttpRoutesV1.kt`, package
`ee.schimke.composeai.uibuilder.protocol`). This is the editor↔host route set of
[DEPLOYMENT_PROTOCOL.md §4b](DEPLOYMENT_PROTOCOL.md#4b-editor--host-route-set-ui-builder-editor),
for the routes its second client calls.

## Why it exists

compose-preview-server serves the UI builder's routes today. They are moving into the UI builder's
own host, `ui-builder-server` in [yschimke/compose-ui-builder](https://github.com/yschimke/compose-ui-builder),
which proxies them to the server until it serves them itself. Two clients in other repositories
call them: the Wasm editor (compose-ui-builder) and **ha-designs**
(yschimke/homeassistant-remotecompose `design-server`, `UiBuilder.kt`). Three release trains have
to agree on the routes before they move, so the agreement goes to layer 0.

This first cut covers **the routes ha-designs calls**: it is the smallest closed set, and the one
client that is not the editor. The editor's other routes are listed at the end, by name only.

**Every shape was derived from the server's current code** (compose-preview-server 3.192.0:
`ServeUiBuilderRoutes.kt`, `ServeUiBuilderExportRoutes.kt`, `ServeHttpServer.kt`,
`ServeMachineAuthorization.kt`), so the server conforms without a change.
`UiBuilderHttpRoutesTest` holds the goldens: the server's bodies encoded with the server's own
`Json` instances, and ha-designs' bodies built as its `buildJsonObject` calls build them, each
decoded strictly.

## Where the types live, and why

In **`ui-builder-protocol`**, not `deployment-protocol`:

- **The Wasm editor is a client.** `ui-builder-protocol` is Kotlin Multiplatform with a Wasm
  variant; `deployment-protocol` is JVM only, so the editor could not compile against it.
- **They are one component's API, not what every deployable shares.** `deployment-protocol` holds
  what every container beside the preview server agrees on: `/healthz`, `/version`, the release
  manifest and session whoami. A route of one component's API belongs with that component's
  bodies.
- **The bodies are already there.** `DesignDocumentV1`, `HttpRequestEnvelopeV1`,
  `HttpResponseEnvelopeV1`, `ExportArtifactV1` and the access types are `ui-builder-protocol`'s.
  The new types sit beside them, and `ui-builder-protocol` gains no dependency.

What is new:

| Kotlin | what it is |
| --- | --- |
| `UiBuilderHttpRoutesV1` | paths, query parameters, the revision header, the body limit, and the API name and level |
| `UiBuilderHttpCredentialsV1` | the credential headers and query parameter the server reads, and the actor ids it resolves them to |
| `UiBuilderIdentityV1` | the `200` body of `GET /api/ui-builder/v1/identity` (server-private `UiBuilderIdentityV1` until now) |
| `UiBuilderIdentityRefusalV1` | its `401` body (server-private `UiBuilderIdentityRefusalV1`) |
| `UiBuilderAdminDesignsV1`, `UiBuilderAdminDesignV1`, `UiBuilderTrashedDesignV1` | the body of `GET /admin/ui-builder/designs` (server-private `AdminUiBuilderDesignsResponse`, `AdminUiBuilderDesignDto`, `TrashedDesignV1`) |

All five body types are on the Builder pattern ([AGENTS.md](../../AGENTS.md)). The identity types'
optional fields are `@EncodeDefault(NEVER)` and their `schemaVersion` `@EncodeDefault`, so a host
writes the server's bytes whatever its encoder's settings. The admin types keep the server's
`encodeDefaults = true` / explicit-null bytes (`"unusableReason":null`), which ha-designs reads.

## Credentials

Every `/api/ui-builder/v1` route asks one question: does the request hold the **capability** the
route needs (read, write or export)? The server answers it from the first of these that the
request carries (`ServeMachineAuthorization.authorizeCapability`):

| credential | presented as | actor |
| --- | --- | --- |
| operator token | `X-Compose-Preview-Token: <token>`, or `?token=<token>`, or the `cp_browse` cookie a browser got by exchanging `?token=` | `operator` |
| GitHub session | the session cookie (`cp_gh_auth`, `__Host-cp_gh_auth` on an HTTPS host) | `github:<login>` |
| agent grant | `Authorization: Bearer <grant>`, `X-Compose-Preview-Token: <grant>`, `?token=<grant>`, or the `cp_agent_grant` cookie | the grant's holder, on behalf of its approver |
| nothing, on a `--public` host | — | `anonymous:visitor` (read only) |

- **The operator token is never a bearer.** `Authorization: Bearer` is looked up only as an agent
  grant, so an operator token sent that way is a `401`. ha-designs' `dashboardCatalog` records
  this the hard way.
- An operator token has every capability. A GitHub session has read; write and export need
  repository access, membership of a host that names its members, or
  `--github-auth-open-ui-builder`.
- A session-cookie request that changes state (`PUT`, `POST`) from a page on another origin is
  refused as forbidden.
- The admin route reads **only** `X-Compose-Preview-Admin-Token` (the admin token, or the
  read-only admin token for `GET`s), never a query parameter, and is not gated by the capabilities
  above. Configured UI-builder administrators (a GitHub login or an actor id) also pass.

Cookie names are listed so a reader can recognise them; a client never sets one. A sibling that
authenticates a browser forwards its `Cookie` header unchanged, as
[DEPLOYMENT_PROTOCOL.md §3](DEPLOYMENT_PROTOCOL.md#3-session-introspection) says.

## Route table

Every `/api/ui-builder/v1` response carries `Cache-Control: no-store`. `{designId}` is one
percent-encoded path segment. A request body larger than 8 MiB (`MAX_REQUEST_BYTES`) is `413`.

| method | path | capability | request | `2xx` |
| --- | --- | --- | --- | --- |
| `POST` | `/api/ui-builder/v1/requests` | per request (below) | `HttpRequestEnvelopeV1` | `200` `HttpResponseEnvelopeV1` |
| `GET` | `/api/ui-builder/v1/designs/{designId}[?revision=N]` | read | — | `200` `DesignDocumentV1` |
| `PUT` | `/api/ui-builder/v1/designs/{designId}` + `If-None-Match: *` | write | `DesignDocumentV1` | `201`, `Location: /ui-builder/{designId}`, text `created {designId}` |
| `GET` | `/api/ui-builder/v1/designs/{designId}/export.rc[?revision=N][&download=1]` | export | — | `200` bytes |
| `POST` | `/api/ui-builder/v1/documents/export.rc[?artifact=true][&download=1]` | export | `DesignDocumentV1` | `200` bytes, or `ExportArtifactV1` with `artifact=true` |
| `GET` | `/api/ui-builder/v1/identity` | read | — | `200` `UiBuilderIdentityV1` |
| `GET` | `/admin/ui-builder/designs` | admin header | — | `200` `UiBuilderAdminDesignsV1` |
| `GET` | `/ui-builder/{designId}` | none (page shell) | — | `200` the editor |

### `POST /api/ui-builder/v1/requests`

The envelope route: one `UiBuilderRequestV1` in, one `UiBuilderResponseV1` out, under
`schemaVersion: 1` and the caller's `requestId`. The envelope's `actorId` must be the actor the
credential resolves to (`operator` for the operator token).

| request `type` | capability |
| --- | --- |
| `listCatalogs`, `listDesigns`, `openDesign`, `getDesignAccess`, `getSnapshot`, `getDelta`, `previewCatalogUpgrade` | read |
| `exportDesign` | export |
| `createDesign`, `applyOperation`, `updateDesignAccess`, `updatePresence` | write |

ha-designs sends two: `listCatalogs`, answered `type: "catalogs"`, to resolve a catalog pin from
`catalogs[].benchmark.{catalogSystemId, catalogRevision, nativeRuntimeId}`; and
`getDesignAccess`, answered `type: "designAccess"`, reading `access.ownerActorId` and every
`actorGrants[]` whose `role` is `editor`. It does not poll: it makes one request per lookup.

The server decodes the envelope **strictly** (`ignoreUnknownKeys = false`). A field a client adds
before the server knows it is a `400`, so new fields travel server → client first.

Status follows the error code: `badRequest` `400`, `unauthorized` `401`, `forbidden` `403`,
`notFound` `404`, `accessRevisionMismatch` / `catalogUnavailable` / `migrationRequired` /
`snapshotRequired` `409`, `internal` `500`. An export refused for capacity is `internal`,
`retryable: true`, status `503` and `Retry-After: 1`.

### `GET` / `PUT /api/ui-builder/v1/designs/{designId}`

`GET` answers the document as `getSnapshot` would (`snapshot.state.document`), at the latest
committed revision or `?revision=N`. A design the caller may not read is `404`, never `403`, so
existence does not leak. A malformed `revision` is `400`.

`PUT` creates and never replaces:

| status | when |
| --- | --- |
| `201` | created; `Location: /ui-builder/{designId}` |
| `400` | blank id, a body that is not a strict `DesignDocumentV1`, a body `id` other than the path's, or the service refused the document |
| `403` | the host does not create designs in that document's catalog |
| `409` | the document already has a canonical home elsewhere (`home` names another server or a repository) |
| `412` | the design exists, or a concurrent create won the race |
| `428` | no `If-None-Match: *` |

The body is decoded strictly, so a top-level key outside `DesignDocumentV1` — ha-designs' library
`template` marker, for one — is a `400`; ha-designs strips it first. On a host with a public origin
the stored document gains `home: {"kind":"server", "url": <origin>, "designId": <id>}`, so a `GET`
afterwards returns every field the `PUT` sent, plus `home` and the defaults the server encodes.

### The Remote Compose exports

Present only on a host built with the Remote Compose exporter
(`RemoteDocumentExportSupport.formats`); elsewhere the paths are `404`. Both run the same service
export as `exportDesign`:

- `200`: the artifact's decoded bytes, `Content-Type: application/octet-stream`,
  `Content-Disposition: inline` (`attachment` with `?download=1`) with
  `filename="{designId}.rc"` (`document.rc` for a supplied document), `ETag: "<sha256 hex>"`, and
  `X-UI-Builder-Revision: N` when the artifact's `REVISION_PINNED_*` diagnostic names a revision.
  The `GET` without `?revision=` renders the current revision on every request.
- `?artifact=true` on the supplied-document route answers the `ExportArtifactV1` as JSON instead,
  diagnostics included, even when they hold an error.
- `422`: the artifact carries error diagnostics (`REMOTE_COMPILE_FAILED`, …), one
  `code: message` per line.
- the service's refusals map as on the envelope route, `503` with `Retry-After: 1` for capacity.

### `GET /api/ui-builder/v1/identity`

| status | body |
| --- | --- |
| `200` | `UiBuilderIdentityV1` |
| `401` | `UiBuilderIdentityRefusalV1`, `WWW-Authenticate: Bearer` |
| `403` | text `UI-builder read access required` |

```json
{"schemaVersion":1,"designVisibilitySupported":true,"actorId":"github:visitor","signedIn":true,"canWrite":false,"writeDeniedReason":"…"}
```

`signedIn` is true for a session or the operator token, false for the anonymous reader of a public
host. `canWrite` asks the write route's question; `writeDeniedReason` is present only when it is
false. `signInUrl` (`/auth/github/start?return=<path>`) is present on a host with GitHub sign-in
when no session is signed in; the return path is the request's same-host `Referer`, else
`/ui-builder`. ha-designs treats `200` with `signedIn: true` as signed in and offers `signInUrl`
otherwise.

### `GET /admin/ui-builder/designs`

Every design on the host, oldest first, whoever owns it; then a row for each design whose stored
files will not read (title its id, counts zero, `documentAvailable: false`); then the trash.
`schema` is `compose-preview-serve/admin-ui-builder-designs/v1`. **Every refusal is `404` with text
`not found`** — no admin token configured, a wrong one, or none — like every admin route.
ha-designs lists with the read-only admin token and filters on `catalogSystemId`, a null
`unusableReason`, `ownerActorId` and `collaborators`.

### `GET /ui-builder/{designId}`

The editor's page shell, public like the Wasm assets; the editor's own data calls carry their
credential. ha-designs links a signed-in person to `<browserUrl>/ui-builder/<id>`, and an
operator link adds `?token=<operator token>`, which the host exchanges for the `cp_browse` cookie
and strips. On the builder host in root mode, a browser navigation to `/ui-builder/{designId}`
is redirected to `/{designId}`; the `/ui-builder/` link keeps working.

## Error shapes

Three, by route, all derived from the current server:

1. **The envelope route** answers every refusal it makes after reading the body as an
   `HttpResponseEnvelopeV1` whose `response` is `ErrorResponseV1` (`type: "error"`, a
   `ServiceErrorV1` with `code`, `message`, `retryable`, and the conflict cursors when relevant).
   An undecodable envelope answers with `requestId: "invalid"`.
2. **The identity route's `401`** is `UiBuilderIdentityRefusalV1`.
3. **Everything else** — the resource routes, the exports, the `413` of the envelope route, every
   `403` but the envelope route's, and the admin route — is `text/plain`, a sentence for a person.
   A client branches on the status, never on the text.

## Irregularities the server has today

Recorded so a second host reproduces them rather than guessing, and so a client does not lean on
them. Changing any of them is a decision about the route set's level (below), not a bug fix:

- The envelope route's actor mismatch answers code `unauthorized` with status `403`: the one place
  the code and the status disagree.
- The envelope route's `413` is text, not an envelope, although every other refusal there is one.
- `PUT …/designs/{designId}` onto a design that already lives on this server computes a `409`
  refusal ("apply changes to the original instead") and sends it as `412`, the status of every
  other existing-design refusal.
- `PUT`'s `Location` is `/ui-builder/` plus the raw design id, not a percent-encoded segment.
- `WWW-Authenticate: Bearer` accompanies every `401`, though only an agent grant is accepted as a
  bearer; the operator token goes in `X-Compose-Preview-Token` or `?token=`.
- The admin route answers `404` for every refusal, so a client cannot tell a wrong token from a
  host with no admin surface.

## How the route set is versioned

The route set is the `ui-builder-editor` API (`UiBuilderHttpRoutesV1.API_NAME`, the same string as
`DeploymentApisV1.UI_BUILDER_EDITOR`), at level **2** (`UiBuilderHttpRoutesV1.API_LEVEL`):

- the editor archive says which level it was built against: `ui-builder-web.json`'s `serverApi`,
  and `provides["ui-builder-editor"]` in its release manifest;
- the host says which levels it serves: the preview server's `SUPPORTED_SERVER_API` (`{2}`), and in
  a release manifest `requires["ui-builder-editor"] = {min: 2, max: 2}`. `ui-builder-server`
  reports what it serves as `apis["ui-builder-editor"]` on `/version`;
- a host refuses an editor whose level it does not serve; a deploy tool checks the pair before a
  rollout ([DEPLOYMENT_PROTOCOL.md](DEPLOYMENT_PROTOCOL.md#how-a-deploy-tool-decides-convergence)).

**What bumps the level**: removing or renaming a route, changing what a route requires (a new
precondition, a stricter capability), changing a body incompatibly, or changing the status a
client branches on. **What does not**: a new route, a new optional body field (through the
Builder), a new request variant a client opts into. The paths keep their `/v1` segment across
levels; that segment versions the body shapes (`schemaVersion: 1`), not the route set.

## Not yet typed

The editor's other routes, names only. Each is a later pull request.

- `GET /api/ui-builder/v1/designs/{designId}/updates` (WebSocket)
- `PUT /api/ui-builder/v1/designs/{designId}/title`
- `GET /api/ui-builder/v1/designs/{designId}/visibility`
- `GET /api/ui-builder/v1/designs/{designId}/agents`
- `GET`/`PUT /api/ui-builder/v1/designs/{designId}/assets/{assetKey}` (ha-designs calls these too)
- `/api/ui-builder/v1/designs/{designId}/export.{svg,png,compose,json}` and
  `/api/ui-builder/v1/documents/export.{png,json}`
- `/api/ui-builder/v1/designs/{designId}/thumbnail.png`,
  `/api/ui-builder/v1/designs/{designId}/revisions/{revision}/thumbnail.png`
- `/api/ui-builder/v1/designs/{designId}/native-preview`,
  `/api/ui-builder/v1/designs/{designId}/remote-content/{nodeId}/capture`
- `/api/ui-builder/v1/designs/{designId}/comments` and its `…/updates`, `…/watch`,
  `…/acknowledgement`, `…/{threadId}`, `…/{threadId}/resolution`,
  `…/{threadId}/acknowledgement`, `…/{threadId}/{commentId}/reactions`
- `/api/ui-builder/v1/designs/{designId}/guidelines`, `…/guidelines/access`,
  `…/guidelines/check`, `…/guidelines/prompt`, and
  `/api/ui-builder/v1/catalogs/{catalogId}/guidelines`
- `/api/ui-builder/v1/designs/{designId}/reference`, `…/reference/settings`
- `/api/ui-builder/v1/designs/{designId}/links`, `/api/ui-builder/v1/links`
- `/api/ui-builder/v1/designs/{designId}/review`, `…/decisions`, `…/implementation`,
  `/api/ui-builder/v1/implementations`
- `/api/ui-builder/v1/designs/{designId}/catalog-recovery`, `…/component-drift`
- `/api/ui-builder/v1/designs/{designId}/dwf-preview`, `…/watchface-validation`,
  `…/watch-face-publication`, `…/watch-installs`
- `/api/ui-builder/v1/device-presets`
- `/api/ui-builder/v1/home-folders`, `/api/ui-builder/v1/home-folders/{designId}`
- `/api/ui-builder/v1/projects`
- `/api/ui-builder/v1/catalogs/{system}/templates`, `/api/ui-builder/v1/catalogs/{system}/component-record`,
  `/api/ui-builder/v1/component-library[/{system}/{componentId}[/file]]`,
  `/api/ui-builder/v1/catalog-capabilities/{file}` (the catalog read API, §4a)
- the browser form routes under `/ui-builder/{designId}/`: `access` (ha-designs posts to it),
  `history`, `history/{revision}/restore`, `history/{revision}/fork`, `delete`, `restore`,
  `rename`, `folder`, `watch-face`; and `/ui-builder/designs`, `/ui-builder/designs/copy`,
  `/ui-builder/designs/import`
- the rest of `/admin/ui-builder`: `designs/{designId}/document` (`GET`, `PUT`),
  `designs/{designId}` (`DELETE`), `actors/{actorId}` (`DELETE`), `library`
