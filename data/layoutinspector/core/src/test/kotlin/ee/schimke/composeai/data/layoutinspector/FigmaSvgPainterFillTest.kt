package ee.schimke.composeai.data.layoutinspector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The opaque-by-name raster fallback vs. a painter fill the token model already flattened.
 *
 * `Image(painter = BrushPainter(Brush.linearGradient(…)))` — Glimmer's card header, and the
 * `Modifier.paint` shape in general — is an `Image` by name, and with composition source info
 * resolved its layout node reads `ImageKt`, which the `"Image"` raster component matches as a
 * substring. Yet the whole thing it draws is a linear gradient the export can emit as a real
 * `<linearGradient>`, so rastering it bakes in pixels that had an exact vector form.
 *
 * The other direction has to keep working: a painter that never flattens (a bitmap, a component's
 * private painter, a re-tinting `colorFilter`) or a brush that never resolved to a gradient still
 * rasters, because dropping the fallback there drops the fill entirely.
 */
class FigmaSvgPainterFillTest {

  private val linearGradientPainter =
    "BrushPainter(brush=LinearGradient(colors=[Color(0.235, 0.549, 0.871, 1.0, sRGB IEC61966-2.1)," +
      " Color(0.0, 0.0, 0.0, 1.0, sRGB IEC61966-2.1)], stops=[0.0, 1.0]," +
      " start=Offset(0.0, 0.0), end=Offset(1000.0, 1000.0), tileMode=Clamp))"

  private fun imageNode(
    painter: String?,
    tokens: ComposeSemanticsTokens?,
    colorFilter: String? = null,
    paintModifier: Boolean = true,
  ) =
    LayoutInspectorNode(
      // The name the layer reads once source info resolves — `ImageKt`, not `Image`, which is why
      // the substring match fires at all.
      nodeId = "header-1",
      component = "ImageKt",
      bounds = LayoutInspectorBounds(0, 0, 396, 120),
      size = LayoutInspectorSize(396, 120),
      modifiers =
        if (!paintModifier) emptyList()
        else
          listOf(
            LayoutInspectorModifier(
              name = "paint",
              properties =
                buildMap {
                  painter?.let { put("painter", it) }
                  put("colorFilter", colorFilter ?: "null")
                },
              bounds = LayoutInspectorBounds(0, 0, 396, 120),
            )
          ),
      tokens = tokens,
    )

  private fun model(node: LayoutInspectorNode) =
    FigmaSvgModel.from(
      layout = LayoutInspectorPayload(node),
      rasterComponents = FigmaSvgModel.DEFAULT_RASTER_COMPONENTS,
    )

  private val gradientTokens =
    ComposeSemanticsTokens(
      backgroundGradient =
        LayoutInspectorGradient(
          colors = listOf("#FF3C8CDE", "#FF000000"),
          stops = listOf(0f, 1f),
          startX = 0f,
          startY = 0f,
          endX = 1f,
          endY = 1f,
        )
    )

  @Test
  fun aFlattenedGradientPainterFillEmitsTheGradientNotARaster() {
    val m = model(imageNode(linearGradientPainter, gradientTokens))

    assertNull("the resolved gradient is not an opaque <image> leaf", m.root.raster)
    assertTrue("no raster crops are scheduled for it", m.rasterTargets.isEmpty())

    val svg = FigmaLayeredSvg.render(m)
    assertFalse("no <image> for a vectorisable painter fill:\n$svg", svg.contains("<image"))
    assertTrue("emits the gradient def:\n$svg", svg.contains("<linearGradient"))
    assertTrue("and fills the shape with it:\n$svg", svg.contains("""fill="url(#gf-"""))
    assertTrue("carrying the brush's stops:\n$svg", svg.contains("#3C8CDE"))
  }

  @Test
  fun aFlattenedColorPainterFillEmitsAFlatFill() {
    val m =
      model(
        imageNode(
          "ColorPainter(color=Color(0.188, 0.188, 0.188, 1.0, sRGB IEC61966-2.1))",
          ComposeSemanticsTokens(backgroundColor = "#FF303030"),
        )
      )

    assertNull(m.root.raster)
    val svg = FigmaLayeredSvg.render(m)
    assertFalse("no <image> for a flat painter fill:\n$svg", svg.contains("<image"))
    assertTrue("the flat fill is emitted:\n$svg", svg.contains("""fill="#303030""""))
  }

  @Test
  fun aBitmapPainterImageStillRasters() {
    // A `BitmapPainter` is what the fallback exists for: nothing about it has a vector form, and a
    // `background` token resolved alongside it describes only what sits *behind* the bitmap.
    val m =
      model(
        imageNode(
          "BitmapPainter(image=android.graphics.Bitmap@1f2e3d)",
          ComposeSemanticsTokens(backgroundColor = "#FFFF0000"),
        )
      )

    assertNotNull("a bitmap-backed Image still rasters", m.root.raster)
    assertTrue(m.rasterTargets.isNotEmpty())
  }

  @Test
  fun anUnresolvedBrushPainterStillRasters() {
    // A radial / sweep / shader brush leaves both fill tokens empty. The painter string is still a
    // `BrushPainter`, so only the *resolved token* distinguishes it from the linear case — and
    // letting it through would emit a shape with no fill at all where the frame had pixels.
    val m = model(imageNode("BrushPainter(brush=RadialGradient(colors=[…]))", tokens = null))

    assertNotNull("an unreadable brush still rasters", m.root.raster)
    assertTrue(m.rasterTargets.isNotEmpty())
  }

  @Test
  fun aRetintingColorFilterOnAFlatPainterStillRasters() {
    // The token resolver deliberately leaves the fill unresolved under a re-tinting filter; the
    // colour it would have flattened to is not the colour drawn.
    val m =
      model(
        imageNode(
          "ColorPainter(color=Color(0.188, 0.188, 0.188, 1.0, sRGB IEC61966-2.1))",
          ComposeSemanticsTokens(backgroundColor = "#FF303030"),
          colorFilter = "ColorFilter(color=Color(1.0, 1.0, 1.0, 1.0), mode=SrcIn)",
        )
      )

    assertNotNull("a re-tinted painter still rasters", m.root.raster)
  }

  @Test
  fun aTokenFillWithNoPainterAtAllStillRasters() {
    // `Image(bitmap, Modifier.background(Red))` reaching the emitter with its paint entry stripped
    // (Coil's content painter, a release build with inspector metadata compiled out): a resolved
    // token is not evidence that the *opaque* part of the node has a vector form.
    val m =
      model(
        imageNode(
          painter = null,
          tokens = ComposeSemanticsTokens(backgroundColor = "#FFFF0000"),
          paintModifier = false,
        )
      )

    assertNotNull("no painter to flatten ⇒ still rasters", m.root.raster)
  }
}
