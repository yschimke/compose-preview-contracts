package ee.schimke.composeai.discovery

/**
 * Wire version of `components.json`.
 *
 * A published data product needs one from the start: the file persists in bundles and is read by
 * the preview browser, the UI builder and MCP clients, so an old bundle and a detached consumer
 * must be able to tell an additive record from an incompatible future one — the lesson
 * `docs/API_STABILITY.md` records for `previews.json`.
 *
 * Evolution rules: an added field with a default bumps nothing; a removed field, or one whose
 * meaning changes, bumps this. A reader that does not know a major version refuses the file rather
 * than guessing at it.
 */
public const val COMPONENT_RECORD_SCHEMA_VERSION: Int = 2

/**
 * The first schema that records **which opt-in mechanism** each marker in [ComponentCode] needs.
 *
 * Before it, `requiredOptIns` was a flat list and a consumer could only assume `kotlin.OptIn`,
 * which is wrong for a marker declared with `androidx.annotation.RequiresOptIn` — the assumption
 * that made this a meaning change rather than an addition, and so a version bump under the rule
 * above. A generator reading an older record cannot classify its markers and should say so instead
 * of guessing.
 */
public const val COMPONENT_RECORD_OPT_IN_MECHANISM_SCHEMA: Int = 2

/** The `schema` value a `ui-builder.policy.json` this generator understands must carry. */
public const val UI_BUILDER_POLICY_SCHEMA: String = "compose-ui-builder-policy/v1"

/** The `schema` a generated `ui-builder.json` carries. */
public const val UI_BUILDER_CATALOG_SCHEMA: String = "compose-ui-builder-catalog/v1"

/** The record file a generated builder catalog is paired with, on the branch and in `build/`. */
public const val UI_BUILDER_RECORD_FILE: String = "components.json"
