package io.xaio.ota.model

import com.google.gson.annotations.SerializedName

data class ReleaseRecord(
    val tag: String,
    val version: String,
    @SerializedName("version_code") val versionCode: Int,
    val channel: String,
    @SerializedName("dfu_package_format") val dfuPackageFormat: String? = null,
    @SerializedName("security_epoch") val securityEpoch: Int,
    @SerializedName("forced_update") val forcedUpdate: Boolean,
    @SerializedName("hw_allow") val hwAllow: List<String>,
    @SerializedName("min_bootloader") val minBootloader: String,
    @SerializedName("stack_req") val stackReq: String,
    val url: String,
    val sha256: String,
    @SerializedName("sig_url") val sigUrl: String,
    @SerializedName("release_notes_url") val releaseNotesUrl: String,
    @SerializedName("release_notes_summary") val releaseNotesSummary: String = "",
    @SerializedName("release_notes_markdown") val releaseNotesMarkdown: String = "",
    @SerializedName("published_at") val publishedAt: String,
)

data class LatestCatalog(
    val stable: ReleaseRecord? = null,
    val beta: ReleaseRecord? = null,
    val dev: ReleaseRecord? = null,
)

data class ReleaseHistory(
    val stable: List<ReleaseRecord> = emptyList(),
    val beta: List<ReleaseRecord> = emptyList(),
    val dev: List<ReleaseRecord> = emptyList(),
)

data class ReleaseCatalogSnapshot(
    val latest: LatestCatalog,
    val history: ReleaseHistory,
) {
    private fun isSupported(record: ReleaseRecord?): Boolean {
        return record?.dfuPackageFormat == "legacy-crc"
    }

    fun latestForChannel(channel: String): ReleaseRecord? = when (channel) {
        "stable" -> latest.stable
        "beta" -> latest.beta
        "dev" -> latest.dev
        else -> latest.stable
    }?.takeIf(::isSupported)

    fun historyForChannel(channel: String): List<ReleaseRecord> = when (channel) {
        "stable" -> history.stable
        "beta" -> history.beta
        "dev" -> history.dev
        else -> history.stable
    }.filter(::isSupported)
}
