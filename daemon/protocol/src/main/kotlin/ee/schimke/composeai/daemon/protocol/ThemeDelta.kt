package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The theme-diff wire shapes, moved here from `data-theme-core` for the same reason as
 * [SemanticsDelta]: the protocol needs the delta, not the differ.
 */
public object ThemeDiffProduct {
  public const val SCHEMA: String = "compose-theme-diff/v1"
}

@Serializable
public data class ThemeTokenChange(
  val token: String,
  val from: String? = null,
  val to: String? = null,
)

@Serializable
public data class ThemeTypographyChange(
  val token: String,
  val from: TypographyToken? = null,
  val to: TypographyToken? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class ThemeDelta(
  // `@EncodeDefault` so the versioned schema discriminator rides every wire surface even under
  // `encodeDefaults = false` (the daemon's `history/diff mode=data` result), matching the
  // `SemanticsDelta` contract.
  @EncodeDefault val schema: String = ThemeDiffProduct.SCHEMA,
  val colorScheme: List<ThemeTokenChange> = emptyList(),
  val shapes: List<ThemeTokenChange> = emptyList(),
  val typography: List<ThemeTypographyChange> = emptyList(),
) {
  val isEmpty: Boolean
    get() = colorScheme.isEmpty() && shapes.isEmpty() && typography.isEmpty()
}

@Serializable
public data class TypographyToken(
  val fontFamily: String? = null,
  val fontSize: Float? = null,
  val fontSizeUnit: String? = null,
  val fontWeight: String? = null,
  val fontStyle: String? = null,
  val lineHeight: Float? = null,
  val lineHeightUnit: String? = null,
  val letterSpacing: Float? = null,
  val letterSpacingUnit: String? = null,
)
