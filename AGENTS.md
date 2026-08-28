# AGENTS.md

Published wire contracts, extracted from
[yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools). Read `README.md`
first — it says what is here, what is deliberately not, and why.

## The rules that matter here

**Every module is a published contract.** `explicitApi()` is on, and `checkKotlinAbi` is wired
into `check` for every module. An implicitly-public declaration is an API decision nobody made,
and an unrecorded ABI change is a break for a consumer in another repository that will not find
out until it bumps. If `checkKotlinAbi` fails, either the change was not intended to be public
or the dump needs updating *deliberately* — `./gradlew updateLegacyAbi`.

**Shape, never behaviour.** If a type reads a file, opens a socket or computes a result, it
does not belong here; it belongs in `:daemon:core` upstream. This is the whole basis of the
split, and the reason `render-session-api` is not here (README).

**Do not add a dependency without asking what it does to the consumers' floor.** Every
`ee.schimke.composeai` module on a POM here is a module a client must resolve. The four
`data-*-core` modules and `common-io` are here only because the wire contracts re-export them
as `api`; that is the bar.

**Run the formatter before committing.** `./gradlew ktfmtFormatAll` (or
`:<module>:ktfmtFormatMain :<module>:ktfmtFormatTest`). `check` runs `ktfmtCheck` and it is a
hard gate.

**Conventional commits** for commit subjects and PR titles (`fix:`, `feat:`, `docs:`, …) —
release-please reads them.

**Never attribute a commit to an AI agent.** Same rule as upstream: no `Co-authored-by:`
trailer naming an agent, and no agent identity as author or committer.

## Cross-repo checks

`DeviceDimensionsCatalogDriftTest` compares a catalog duplicated in the upstream Gradle plugin.
It **skips** without a checkout. If you are changing `DeviceDimensions`, run it for real:

```sh
COMPOSE_AI_TOOLS_ROOT=../compose-ai-tools ./gradlew :daemon-devices:test
```

`docs/daemon/protocol-fixtures/` are the cross-language wire goldens, parsed here and by
compose-preview-vscode's TypeScript suite. Adding a protocol message means adding a fixture;
`MessagesTest.fixtureInventoryMatchesExpected` fails if you forget.
