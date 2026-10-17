package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.Serializable

/**
 * One published starting-document set and the complete served catalog pin it belongs to. Template
 * ids are the policy's file names without `.json`, independent of the source documents' ids.
 *
 * Hosts authenticate reads and capture the pin and documents from one publication. Clients validate
 * the complete pin before caching; the documents retain their published pins until creation assigns
 * a fresh design id, revision zero and the served catalog pin. An empty set permits a generic
 * blank.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogTemplateDocumentsV1
internal constructor(
  public val catalogPin: CatalogReferenceV1,
  public val templates: Map<String, DesignDocumentV1>,
) {
  public class Builder(catalogPin: CatalogReferenceV1) {
    public var catalogPin: CatalogReferenceV1 = catalogPin
    public var templates: Map<String, DesignDocumentV1> = emptyMap()

    public fun build(): CatalogTemplateDocumentsV1 =
      CatalogTemplateDocumentsV1(catalogPin = catalogPin, templates = templates)
  }

  public fun newBuilder(): Builder = Builder(catalogPin).also { it.templates = templates }
}
