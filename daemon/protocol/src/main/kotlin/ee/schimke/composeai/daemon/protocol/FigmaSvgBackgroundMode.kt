package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.Serializable

/**
 * The Figma SVG background mode carried on the wire, moved here from `data-layoutinspector-core`.
 */
/**
 * How the `compose/figma-svg` export treats the background the render painted behind the preview.
 *
 * The export's product is **editable layers**, and a baked-in fill is the one thing an importing
 * designer can't easily undo — an opaque shape spanning the canvas that has to be found and deleted
 * before the import works anywhere but the surface it was baked for. Hard to remove, easy to add
 * back: so [NONE] is the default and a background is *requested*, per preview, by whoever knows it
 * is wanted.
 */
@kotlinx.serialization.Serializable
public enum class FigmaSvgBackgroundMode {
  /**
   * Export background-free (the default). The tree's own fills still draw — a screen that paints
   * its surface colour keeps painting it; only the *injected* bottom layer is dropped.
   */
  NONE,
  /**
   * Paint the background in the **device-mask** shape: a black `<circle>` for a round Wear face,
   * the vertical stadium for a tall Wear scroll export, and — with no mask — the plain frame rect.
   * The corners outside the mask stay transparent, so the export reads as a watch sitting on the
   * importing canvas rather than a square tile. This is the shape the export used to inject
   * unconditionally, and what a Wear device or tall-scroll preview generally wants.
   */
  DEVICE,
  /**
   * Paint the background in the **content's own** shape — the outermost layer that declares one, so
   * an `OutlinedButton` gets a filled pill exactly under its outline and a circular icon button
   * gets a disc. No device mask involved; a component preview that just needs something to read
   * against wants this, not a full tile.
   *
   * Falls back to the plain frame rect when the tree declares no shape at all.
   */
  CONTENT_SHAPE,
  /**
   * Paint the background as a plain rect across the whole export, ignoring the device mask. The
   * mask keeps clipping the *content*, but the fill runs to the corners — the "stage" look, for an
   * export that has to sit on a solid card rather than on the importing canvas.
   */
  FULL_BLEED;

  public companion object {
    /**
     * Parses a mode from a wire/property string, case- and separator-insensitive (`full-bleed`,
     * `full_bleed`, `fullBleed`). Also accepts the pre-modes booleans: `true` is the device-mask
     * shape the export used to inject unconditionally, `false` is [NONE]. Null when unset or
     * unrecognised, so a typo falls back to the caller's default rather than failing a render.
     */
    public fun parse(raw: String?): FigmaSvgBackgroundMode? =
      when (raw?.trim()?.lowercase()?.replace("-", "")?.replace("_", "")) {
        null,
        "" -> null
        "false",
        "none" -> NONE
        "true",
        "device",
        "clipped",
        "clip" -> DEVICE
        "contentshape",
        "content",
        "shape" -> CONTENT_SHAPE
        "fullbleed",
        "bleed" -> FULL_BLEED
        else -> null
      }
  }
}
