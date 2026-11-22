# Catalog read API

What a client outside the preview server reads catalogs and their previews from, over HTTP, typed in
`ee.schimke.composeai:component-catalog-protocol` (`CatalogReadV1.kt`, package
`ee.schimke.composeai.catalog.protocol`). This is the `catalog-read` API of
[DEPLOYMENT_PROTOCOL.md §4a](DEPLOYMENT_PROTOCOL.md#4a-catalog-read-api-catalog-read).

## Why it exists

The hosted catalog MCP (`/mcp`: compose-preview-server's `ServeCatalogMcp.kt` and
`ServeLibraryMcp.kt`) is moving to [yschimke/compose-preview-mcp](https://github.com/yschimke/compose-preview-mcp)
as its own container. Today it reads catalogs through the server's in-process
`ServeSessionRegistry` (`knownSessionIds`, `peekHost`, `peekState`, `lease`). Out of process it has
to fetch the same data from the server over HTTP, and the two release trains have to agree on those
routes, so the agreement goes to layer 0.

**Level 1** typed the routes the server already served that carry what the MCP reads, and listed the
rest as four gaps. Every level-1 shape was derived from the server's code (compose-preview-server
3.192.0: `ServeHttpServer.kt`, `ServeCatalogMcp.kt`, `ServeMachineAuthorization.kt`), so the server
conformed without a change. **Level 2** types the routes compose-preview-server#2092 added to close
those gaps (`ServeCatalogRead.kt`): per-preview capabilities, the catalog's history and one
preview's timeline, a preview's guidelines result, and an `ETag` plus a `revision` field on
`/{system}/api/previews`. `CatalogReadTest` (level 1) and `CatalogReadLevel2Test` hold the goldens:
the server's bodies as its own serializer writes them, pinned against test-only copies of its
private DTOs, then decoded as the contract types (the level-2 bodies strictly, since they are typed
whole).

The render request (a made-to-order render with overrides, an SVG, semantics, a data product) is a
separate contract and is **not** typed here; see [the last section](#render-request-not-yet-typed).

## Where the types live, and why

In **`component-catalog-protocol`**, not `deployment-protocol`:

- **The bodies are catalog shapes.** The module already holds the catalog's wire files
  (`components.json`, `ui-builder.json`); the read API is how a client reads that catalog from a
  server instead of a delivery branch. [UI_BUILDER_HTTP_ROUTES.md](UI_BUILDER_HTTP_ROUTES.md) puts
  one component's routes beside that component's bodies for the same reason.
- **It has two clients, and one is Wasm.** DEPLOYMENT_PROTOCOL §4a names the UI builder as a reader
  of this API beside the MCP. `component-catalog-protocol` has JVM and Wasm variants;
  `deployment-protocol` is JVM only.
- **No new dependency.** The module still depends on `kotlinx-serialization-json` alone. Three
  shapes belong to modules it must not take on: a knob is a
  `PreviewOverrideDeclaration` (`data-preview-overrides-core`, JVM only) and a watch face is
  compose-ai-tools' `WatchFaceComplicationsPayload` (layer 1b). The first is projected to
  `CatalogReadKnobV1`, the part a reader needs; the second is an opaque `JsonObject`. The goldens
  hold the knob JSON `PreviewOverrideDeclaration`'s serializer writes, as a literal: even a
  test-only `project(…)` dependency would make `maven-publish-plan.sh` republish this module
  whenever that one changes.
- **The guidelines record is opaque too.** `CatalogReadGuidelinesV1.record` is a
  `GuidelineRecordV1` from `design-guidelines-protocol`, which is in this repository, at layer 0,
  JVM and Wasm, and depends on nothing but `kotlinx-serialization-json`: taking it would be
  technically clean. It is still a `JsonObject`, for what it would do to consumers and releases.
  Every client of this module would resolve a second coordinate and its ABI, including compose-ai-tools'
  Gradle plugin, which reads only the catalog wire files and has no use for guidelines; and
  `maven-publish-plan.sh`'s rule 2 republishes a module whenever a dependency of it publishes, so
  every guidelines change would cut a new `component-catalog-protocol` the catalog files did not
  need. The record carries its own `schema` (`CatalogReadGuidelinesV1.RECORD_SCHEMA`), and a reader
  that wants it typed — the MCP, which already links `design-guidelines-protocol` — decodes it with
  `GuidelineRecordV1.serializer()` in one call. The golden holds the record's JSON as
  `GuidelineRecordV1`'s serializer writes it under the server's `Json`, as a literal, for the same
  reason the knob JSON is one.

| Kotlin | what it is |
| --- | --- |
| `CatalogReadRoutesV1` | paths, query parameters, response headers, and the API name and level |
| `CatalogReadCredentialsV1` | the credential headers, query parameter and cookies the server reads |
| `CatalogReadStatusV1`, `CatalogReadCatalogV1` | the catalog part of `GET /status.json` (server-private `StatusResponse`, `CatalogDto`) |
| `CatalogReadPreviewsV1`, `CatalogReadPreviewV1`, `CatalogReadDegradationV1`, `CatalogReadKnobV1`, `CatalogReadWatchFaceRendererV1` | the body of `GET /{system}/api/previews` (server-private `PreviewsResponse`, `PreviewDto`, `DegradationDto`; render-host's `WatchFaceRendererInfo`) |
| `CatalogReadCapabilitiesV1`, `CatalogReadPreviewCapabilitiesV1` | level 2: `GET /{system}/api/capabilities` (server-private `CatalogCapabilitiesResponse`, `PreviewCapabilitiesDto`) |
| `CatalogReadHistoryV1` | level 2: `GET /{system}/api/history` (`CatalogHistoryResponse`) |
| `CatalogReadPreviewHistoryV1`, `CatalogReadHistoryVersionV1` | level 2: `GET /{system}/api/history/{previewId}` (`PreviewHistoryResponse`, `HistoryVersionDto`) |
| `CatalogReadGuidelinesV1` | level 2: `GET /{system}/api/guidelines/{previewId}` (`PreviewGuidelinesResponse`) |

Every type is on the Builder pattern ([AGENTS.md](../../AGENTS.md)). Every field the server always
writes (its `Json` sets `encodeDefaults = true`) is `@EncodeDefault`, so a second host or a test
double built with these types writes the server's bytes whatever its encoder's settings. A field
added later is a Builder `var` at the end of the internal constructor, `@EncodeDefault` when the
server always writes it (as `CatalogReadPreviewsV1.revision`: a level-2 server writes
`"revision": null` for a catalog with none) and `@EncodeDefault(NEVER)` when it leaves it out.

**Absent and `null` read the same.** The server writes an absent optional as `null`; every optional
field here defaults to `null` (or `false`, or empty), so a body from a writer that leaves them out
decodes to the same value. `CatalogReadLevel2Test` checks every level-2 golden both ways.

**Decode every body with `ignoreUnknownKeys = true`.** The types are the part of each body a reader
may rely on. `/status.json` is the operator status and also carries daemons, configuration,
caches and grant counts, which are not this contract. `/api/previews` is typed whole; its `views`
counters are engagement since the server started, never identity.

## What the MCP reads, and where it comes from

Every read `ServeCatalogMcp` makes of the registry or a host, mapped to a route:

| the MCP reads (in process) | used by | served by |
| --- | --- | --- |
| catalog ids (`knownSessionIds`) and the still-loading ones (`catalogLoads`, `pending`) | every tool; `complete: false` listings | `GET /status.json` `catalogList[].id`, `loadState` |
| a catalog's label and preview count **without resuming it** (`peekHost` / `peekState`) | `status`, `list_projects`, `catalog_library`, `resources/list`, `list-all-documentation` | `GET /status.json` `title`, `previews` (a different label source; see [irregularities](#irregularities-the-server-has-today)) |
| a catalog's label after a lease (`host.label`) | `list_previews` | `GET /{system}/api/previews` `module` |
| each preview's id, label, modes | every listing | `GET /{system}/api/previews` `previews[]` |
| each preview's declared knobs (`seedKey`, `type`) | `list_previews` `knobs`, override validation | `previews[].overrides` → `CatalogReadKnobV1.seedKey` |
| whether a preview is a watch face, its slot and user-configuration counts | `list_previews`, override validation | `previews[].watchFace` (`slots`, `userConfigurations`) |
| the watch-face renderer backends and their availability | `list_previews`, `wfRenderer` resolution | `previews[].watchFaceRenderers` |
| `svgAvailable`, `scrollAvailable`, `state`, `theme`, `dataProductKinds` per preview | `list_previews`, `list_data_products`, `observe=svg` / `scroll-*` refusals | level 2: `GET /{system}/api/capabilities` |
| whether a preview has an a11y overlay, design annotations, published typography, a guidelines result | `list_data_products` | level 2: `GET /{system}/api/capabilities` (`productKinds` is `list_data_products`' derived set) |
| the published render (`bakedRender`) | `resources/read`, `render_preview` without live scope, `catalog_library` | `GET /{system}/render/{previewId}.png` |
| the catalog's delivery-branch provenance (`repo`, `branch`, served `commit`) | `history_list` | level 2: `GET /{system}/api/history` (also `repo`, `branch` in `/status.json`) |
| a preview's indexed timeline (`indexedTimeline`: path, observations, unstable, flapCount, versions) | `history_list`, `history_diff`, `history_read` | level 2: `GET /{system}/api/history/{previewId}` |
| one historical render's bytes (`pinnedIndexedRender`) | `history_read` | `GET /{system}/render/{previewId}.png?at=<commit>`, with a path difference (see irregularities) |
| a preview's published guidelines result (`guidelineResultFor`, a `GuidelineRecordV1`) | `get_preview_data kind=guidelines/result` | level 2: `GET /{system}/api/guidelines/{previewId}` |
| the device catalog (`DeviceDimensions`) | `list_devices` | no route needed: `daemon-devices` is a layer-0 library the MCP links |
| the host's public origin and its aliases | `status`, `initialize`, signed image URLs | MCP deployment configuration, not a route |
| live renders, SVG, semantics, data products, matrices, semantics diffs | the render tools | the render request ([below](#render-request-not-yet-typed)) |
| the agent-grant broker (`request_access`, `poll_access`) and the live-scope check | access tools; every live tool | the server's `/agent-access/…` routes: agent-grant-protocol's contract, not this one |
| UI-builder tools (`ui_builder_*`) | — | the `ui-builder-editor` routes ([UI_BUILDER_HTTP_ROUTES.md](UI_BUILDER_HTTP_ROUTES.md)) |

The MCP's **signed resource links** (`compose-preview://catalog/<catalog>/<previewId>?…&exp=&sig=`,
and the `<origin>/mcp/render.png?uri=…&exp=…&sig=…` image URL) need no server route: they are
signed with a per-process key the MCP holds, and `/mcp/render.png` sits under `/mcp`, which moves
with the MCP's container. The MCP answers such a URL by fetching the pixels through the routes here
(the published lane) or through the render request (a link minted after a live render).

The MCP's component-record and catalog-guidelines reads all happen inside the UI-builder tools,
which reach the builder's own routes; the catalog tools do not read them.

## Route table

`{system}` is the catalog id and `{previewId}` the preview id, each one percent-encoded path
segment.

| method | path | needs | `2xx` |
| --- | --- | --- | --- |
| `GET` | `/status.json` | read credential | `200` `CatalogReadStatusV1` |
| `GET` | `/{system}/api/previews` (or `/api/previews?session={system}`) | read credential | `200` `CatalogReadPreviewsV1` |
| `GET` | `/{system}/render/{previewId}.png` (or `/render/{previewId}.png?session={system}`) | read credential | `200` `image/png` |
| `GET` | `/{system}/render/{previewId}.png?at=<commit>` | read credential | `200` `image/png`, as published at that commit |
| `GET` | `/{system}/api/capabilities` (level 2) | read credential | `200` `CatalogReadCapabilitiesV1` |
| `GET` | `/{system}/api/history` (level 2) | read credential | `200` `CatalogReadHistoryV1` |
| `GET` | `/{system}/api/history/{previewId}` (level 2) | read credential | `200` `CatalogReadPreviewHistoryV1` |
| `GET` | `/{system}/api/guidelines/{previewId}` (level 2) | read credential | `200` `CatalogReadGuidelinesV1` |
| `GET` | `/version` | none | `DeployableVersionV1` ([DEPLOYMENT_PROTOCOL.md](DEPLOYMENT_PROTOCOL.md#the-version-body)) |

Each level-2 route also answers as `/api/…?session={system}` (`CatalogReadRoutesV1.ROOT_…_PATH`),
like `/api/previews`. Every `200` of `/api/previews` and the four level-2 routes carries an `ETag`
(see [caching](#caching-validators-and-paging)).

### `GET /status.json`

Every configured catalog, listed or not, in configuration order, with its load state. Built without
resuming any catalog (`peekHost`), so:

- a catalog idle since it was last resident reports its last-known title, trust and count with
  `metaStale: true`;
- a catalog never resident since the server started has `previews: null`: unknown, not zero;
- `loadState` is `loaded`, `stale` (a usable copy, latest refresh failed, `loadError` says why),
  `failed` (no usable copy; not served) or `pending` (configured, not loaded yet: normal for a minute
  after a restart). A reader treats `pending` as "retry", never as "no such catalog", which is
  exactly the MCP's `complete: false` / `loading` answer today.

On a host serving one catalog as a top-level site, `catalogList` holds only that catalog.

### `GET /{system}/api/previews`

One catalog's previews, unpaged, in the catalog's order. It **leases** the catalog, so an idle one is
resumed (as the MCP's `list_previews` does today). A catalog still loading after a restart answers
`503`, `Retry-After: 3`, `Cache-Control: no-store`, text `catalog loading`; one the server does not
have answers `404`, text `not found`.

`/api/previews` without a `{system}` segment reads `?session=`, else the host's default catalog; on
a top-level site host the site's catalog wins over `?session=`.

### `GET /{system}/render/{previewId}.png`

With no render parameters, the preview's published render: the bytes the MCP's `resources/read`
and its live-scope-less `render_preview` return. `X-Compose-Preview-Generation` names the lane
that answered (`baked` for published bytes). `404` text `no such preview` for an unknown preview.
Any override parameter, `?scroll=`, `?rcPlayer=` or a non-PNG suffix turns this into a live render,
which needs `live` scope and belongs to the render request, not to this API.

`?at=<commit>` pins the render to one delivery-branch publish: 7 to 40 lower-case hex, never a
ref. Unanswerable is `404` (never today's bytes); combined with a render parameter, `400`.

`HEAD` is answered where the `GET` would replay published bytes (unfurlers probe with it); a `HEAD`
that would commission a render is refused.

### `GET /{system}/api/capabilities` (level 2)

What each preview can answer, in the catalog's order, unpaged, built from the calls the MCP's
`list_previews` and `list_data_products` make. `dataProductKinds` is the preview's declared list;
`productKinds` adds the derived kinds (`watchface/complications`, `a11y/hierarchy`,
`guidelines/result`, `compose/annotations`, constants on `CatalogReadPreviewCapabilitiesV1`); both
are sorted. `revision` is the served delivery commit, or `null`. It leases the catalog, with
`/api/previews`' `503` and `404`.

### `GET /{system}/api/history` (level 2)

Where the catalog's history lives. `mode` is `published` (the delivery branch: `repo`, `branch` and
the served `commit` are set), `local` (the server's own checkout, project mode) or `none` (with a
`reason`), in that precedence. `renderUrlTemplate` substitutes `{commit}` and `{path}`;
`publishesManifest` says the served tree carried a `history.json`, so the per-preview route can
answer with a timeline. `manifestUrl` names that file at the branch head; its format is
compose-ai-tools', not this contract.

### `GET /{system}/api/history/{previewId}` (level 2)

One preview's timeline, in all three modes, with the catalog's provenance fields repeated. With a
timeline: `path`, `observations`, `unstable`, `flapCount`, and `versions` newest first, each with
`commit`, `date`, `blob`, `commits`, `sourceSha`, `sinceCommit`, `occurrences` and the `renderUrl`
serving those exact bytes. Two versions with the same `blob` have the same bytes: that is
`history_diff`'s `changed`. Without one, `versions` is `null` and `reason` says why, never an empty
list that would read as "never changed"; the one exception is `local` mode with fewer than two
distinct renders, which answers `[]` with a `reason`, as `history_list` does. An unknown preview is
`404`, text `no such preview`.

### `GET /{system}/api/guidelines/{previewId}` (level 2)

The preview's published guidelines result: `found: true` with the `GuidelineRecordV1` as `record`
(opaque here; [above](#where-the-types-live-and-why)), or `found: false` with `record: null`.
`kind` is always `guidelines/result`. `revision` is the served delivery commit, not the record's own
integer `revision`. An unknown preview is `404`, text `no such preview`.

## Credentials

Every route here but `/version` shares one gate (`rejectBadToken` → `isAuthorizedCall`):

| credential | presented as |
| --- | --- |
| nothing, on a `--public` host | — |
| operator token | `X-Compose-Preview-Token: <token>` or `?token=<token>`, or the `cp_browse` cookie a browser got by exchanging `?token=` |
| agent grant with `preview` scope | `X-Compose-Preview-Token: <grant>`, `Authorization: Bearer <grant>`, `?token=<grant>`, or the `cp_agent_grant` cookie |

- **Every refusal is `404`, text `not found`**, never `401`: a gated host does not say what it
  serves. A reader cannot tell a wrong credential from an unknown catalog on these routes.
- The operator token is never a bearer: `Authorization: Bearer` is looked up only as an agent grant.
- A GitHub session alone does not pass this gate.
- `/version` is ungated.

**What the MCP's container presents.** On a public host, nothing. On a gated host, one of two
designs, and the choice is the MCP's: a service credential of its own (the operator token, held as
a secret, in `X-Compose-Preview-Token`), with the MCP enforcing grant scope itself as it does in
process today; or the calling agent's grant, forwarded as `Authorization: Bearer`, so the server
judges every read. The second keeps the server the only place a grant is checked, but needs the
grant introspection DEPLOYMENT_PROTOCOL §3 leaves to agent-grant-protocol.

## Caching, validators and paging

- `/status.json`: `Cache-Control: no-store`. No `ETag`.
- `/api/previews`: **no `Cache-Control`**. At level 1, no `ETag` either: a reader must not assume
  freshness across requests; the body changes when a catalog refreshes in place.
- **Level 2: `ETag` and `If-None-Match`.** Every `200` of `/api/previews`, `/api/capabilities`,
  `/api/history`, `/api/history/{previewId}` and `/api/guidelines/{previewId}` carries a weak
  `ETag` (`W/"…"`). A request whose `If-None-Match` names it (weak comparison; `*` matches too) is
  answered `304 Not Modified`, no body, the same `ETag`. A refusal (`404 not found`) carries no
  `ETag`, so a validator never leaks what a gated host serves.
  - The tag is **opaque**. The server builds it as the first 12 characters of the served commit,
    when there is one, then a SHA-256 prefix of the body, but a reader only stores it and sends it
    back; it never parses a revision out of it. Read `revision` (or `commit`) for that.
  - It follows the body, not only the commit: a body can change at the same commit (a renderer comes
    up, a degradation clears), and its tag changes with it.
  - On `/api/previews` the digest leaves out the `views` engagement counters, which move on every
    page open and are not catalog identity; that is why the tag is weak. A `304` there does not mean
    the counters are unchanged.
  - The four level-2 routes send `Cache-Control: private, no-cache`
    (`CatalogReadRoutesV1.READ_CACHE_CONTROL`): keep a copy, revalidate every time, never in a shared
    cache, since a gated catalog's data must not sit in one. `/api/previews` keeps sending none.
- The published PNG, with no render parameters: `no-store` on a gated host; on a public host
  `public, max-age=300, stale-while-revalidate=3600`, or `public, max-age=31536000, immutable` when
  the request carries the catalog's current `?gen=`. A pinned `?at=` render is `immutable` on a
  public host and `no-store` on a gated one.
- JSON bodies of 1 KiB or more are gzip-encoded for a client that accepts it.
- **Nothing is paged.** `/api/previews` returns the whole catalog (a large one is megabytes). The MCP
  pages its own answer (`offset`, `limit` 100 by default, at most 500, and a `query` over id and
  label) after reading it; that stays the MCP's behaviour, over a cached copy if it likes.
- **Nothing is addressed by revision** except the PNG's `?at=`. At level 1 there is no catalog
  revision in either JSON body; at level 2 `/api/previews`' `revision` (and the level-2 bodies'
  `revision` or `commit`) names the delivery commit served, and the `ETag` is how a reader asks "has
  this catalog changed" without re-downloading it. Neither selects a revision: every route answers
  for the commit the server serves now.

## The level-1 gaps, now typed

Level 1 listed four reads the MCP made in process that no route served. compose-preview-server#2092
added a route or field for each, and level 2 types them:

| gap | what the MCP read | typed at level 2 as |
| --- | --- | --- |
| 1. per-preview capabilities | `dataProductKinds`, `hasSvgExportFor`, `hasScrollExportFor`, `state`, `theme`, and the derived `a11y/hierarchy`, `compose/annotations`, `guidelines/result` kinds | `GET /{system}/api/capabilities`: its own route rather than the new `PreviewDto` fields level 1 suggested |
| 2. history | the served delivery commit (`provenance.commit`) and a preview's indexed timeline, with blobs and dates (`/api/render-runs` carries neither) | `GET /{system}/api/history` and `GET /{system}/api/history/{previewId}` |
| 3. a preview's guidelines result | `guidelineResultFor`, a `GuidelineRecordV1` | `GET /{system}/api/guidelines/{previewId}` |
| 4. a catalog revision to cache against | — (needed once the MCP reads `/api/previews` over the network) | the `ETag` / `304` on all five JSON reads, and `CatalogReadPreviewsV1.revision` |

## Irregularities the server has today

Recorded so a reader reproduces them rather than guessing. Changing any is a decision about this
API's level, not a bug fix:

- **Level 2 is not announced.** The server that adds the level-2 routes still reports
  `serveSchema: "compose-preview-serve/v3"` and no `apis` on `/version`, so a reader cannot tell
  level 1 from level 2 there. An `ETag` on a `200` of `/api/previews` is the level-2 signal; a
  level-2 route on a level-1 server answers as an unknown path does, which a reader cannot tell
  from a refusal.
- **Two names for the served commit.** `/api/previews`, `/api/capabilities` and `/api/guidelines`
  call it `revision`; `/api/history` and `/api/history/{previewId}` call it `commit`, as the MCP's
  `history_list` does. Same value.
- **Two `revision`s in the guidelines body.** The body's is the served commit (a string); its
  `record`'s is the record's own integer.
- **A local-mode `renderUrl` is a path on the server.** In `local` mode each version's `renderUrl`
  is `/history/render/<blob>.png`, to resolve against the answering server's origin, under the same
  read credential, and served only in project mode. That route is not typed here; a `published`
  `renderUrl` is an absolute `raw.githubusercontent.com` URL.

- **Two names for a catalog.** `/status.json`'s `title` is the bundle's title, else the host's
  label; the MCP's `label` (and `/api/previews`' `module`) is the host's label. A catalog whose
  bundle names a title is called differently by the two today.
- **Two catalog sets.** The MCP lists every registered session (`knownSessionIds`).
  `/status.json` lists configured catalogs only when the host tracks any, so a session registered
  outside the catalog configuration (an uploaded bundle on an `--accept-bundles` host) is in the
  MCP's list and missing from `/status.json`. Conversely `/status.json` lists `failed` catalogs,
  which have no session and which the MCP does not list.
- **Two pinned-render paths.** `?at=` resolves the preview's path with today's layout
  (`pinnedRender`); the MCP's `history_read` resolves it from the history manifest
  (`pinnedIndexedRender`), falling back to today's layout only when the manifest has no entry. A
  preview whose path moved is readable through the MCP and `404` through `?at=`.
- **Two story-id spaces.** The MCP's Storybook-compatible ids are `<catalog>::<previewId>`;
  `/{system}/index.json`'s are Storybook-style slugs. They do not interconvert.
- **`?token=` shadows the header.** For the operator token, `rejectBadToken` reads `?token=` and
  only falls back to `X-Compose-Preview-Token` when the query has none, so a stale `?token=` fails a
  request whose header is right. `ServeMachineAuthorization` (the `/mcp` gate) accepts either.
- **Published-only differs.** The MCP's published lane answers from bytes already on disk
  (`bakedRender`) and refuses otherwise; `/render/{previewId}.png` falls through to the host's render
  path, which fetches the published bytes for a cold catalog. Over HTTP the MCP gets an answer where
  in process it gets a refusal.
- **The `/mcp` gate refuses the browser grant cookie** (`allowBrowserGrantCookie = false`); these
  routes accept it.

## How the API is versioned

The routes are the `catalog-read` API (`CatalogReadRoutesV1.API_NAME`, the same string as
`DeploymentApisV1.CATALOG_READ`), now at level **2** (`CatalogReadRoutesV1.API_LEVEL`):

| level | constant | what it is |
| --- | --- | --- |
| 1 | `API_LEVEL_1` | `/status.json`, `/{system}/api/previews`, the published render and `?at=`: what compose-preview-server 3.192.0 serves |
| 2 | `API_LEVEL_2` | level 1 plus `/api/capabilities`, `/api/history`, `/api/history/{previewId}`, `/api/guidelines/{previewId}`, the `ETag` / `304` on those and `/api/previews`, and `/api/previews`' `revision`: what the server serves from the release that merges compose-preview-server#2092 |

**Why closing the gaps is a level, though nothing is removed.** Level 2 is only additions, so a
level-1 reader reads a level-2 server unchanged, and 1 keeps meaning exactly the level-1 routes. It
is a level anyway because a reader has to know the new routes are there before it calls them: on a
level-1 server they answer as an unknown path, which on these routes is indistinguishable from a
refusal, so "try it and see" does not work. An MCP that requires them says `min: 2`; one that degrades without them
keeps `min: 1`.

- The server does not report `apis` on `/version` yet. Until it does, a reader takes
  `serveSchema: "compose-preview-serve/v3"` (`CatalogReadRoutesV1.SERVE_SCHEMA`) as level 1 at
  least, and an `ETag` on `/api/previews` as level 2. When the server adopts `DeployableVersionV1`,
  it reports `apis["catalog-read"] = 2` through `DeployableVersionV1.Builder.apis`; no field is
  added for it.
- The MCP's release manifest says `requires["catalog-read"] = {min: 2, max: 2}` once it reads the
  level-2 routes, `{min: 1, max: 2}` while it can fall back; a deploy tool checks it against the
  server's `apis` before a rollout
  ([DEPLOYMENT_PROTOCOL.md](DEPLOYMENT_PROTOCOL.md#how-a-deploy-tool-decides-convergence)).
- **What bumps the level**: removing or renaming a route or a typed field, changing a status a reader
  branches on (`503` for loading, `404` for unknown), or changing what a route requires. **What does
  not, by itself**: a new route, a new optional field through the Builder; level 2 is such a set,
  made a level because a reader needs to know it is there (above).

## Render request: not yet typed

What the MCP's render tools send and get back, from `ServeCatalogMcp`'s handlers. The future
contract is to be named `catalog-render`; it is distinct from DEPLOYMENT_PROTOCOL §4c's
`render-export`, which renders a UI-builder design rather than a catalog preview. Every input below
needs `live` grant scope except a bare published PNG, which is this API's.

**Inputs**, per render:

- the target: `catalog` and `previewId`, or a `compose-preview://catalog/<catalog>/<previewId>` URI
  whose `?overrides=` is base64url JSON;
- `observe`: `png`, `svg` (the `compose/figma-svg` export as SVG text), `scroll-png` / `scroll-svg`
  (the full-page capture), `semantics` (the default: hash, size and dimensions plus the
  `compose/annotations` payload) or `hash`;
- `overrides`: a map of string, number or boolean, keyed by the server's override parameters and
  `knob.<seedKey>` for declared knobs, refused when a knob is undeclared or its value does not fit
  its type;
- watch-face arguments: `complications` (array), `watchFace` (object), `ambient`, `ambientBurnIn`,
  `ambientLowBit`, `clockEpochMillis` or `previewTime`, and `wfRenderer` (`dwf-rc`, `dwf-apk`);
- `render_matrix`: base `overrides`, plus `axes` (a cross-product) or `scenarios` (watch-face
  cells), at most 24 cells, `observe` `png` or `hash`;
- `diff_semantics`: two targets with their own overrides; reads each side's `tags` index;
- `get_preview_data`: a `kind` (`a11y/*`, `compose/annotations` and its aliases,
  `watchface/complications`), with the same overrides and watch-face arguments;
- `preview-stories`: up to 16 targets in one call.

**Outputs**: PNG bytes, or SVG text; the `generation` that produced them (`baked` when no renderer
ran); `requestedOverrides`, `overridesApplied` and `overridesIgnoredReason` when the answer could not
apply them; `sha256`, `sizeBytes`, `widthPx`, `heightPx`; the `compose/annotations` JSON for
semantics and its `tags` index for the diff; the data product's JSON. Failures the MCP tells apart:
the render queue or daemon busy (retry), no such preview, no SVG or scroll export for this preview,
a watch-face renderer unknown or unavailable (with its reason), and a render failure with the
daemon's reason.

The server's `GET`/`POST /{system}/render/{previewId}.{png,svg,a11y,annotations,complications}`
already renders most of this from query (or body) parameters rather than a JSON override map, and
answers dropped overrides with `X-Compose-Preview-Dropped-Overrides`, `409` or `503`; typing that
mapping is the `catalog-render` contract's first job.
