package com.bitchat.android.nyaya.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Browsable index of the law bundled inside the app.
 *
 * The knowledge base already ships the complete text of the Acts as markdown
 * assets, but until now the only way to see any of it was to ask the AI a
 * question and hope the retriever surfaced the right passage. This exposes the
 * same assets directly, so the user can read the law themselves — which matters
 * for a legal-help app: an answer the user can verify against the statute is
 * worth more than one they have to take on trust.
 *
 * No network, no parsing beyond markdown headings, and the large Acts are read
 * one section at a time rather than loaded whole.
 */
class LegalLibrary(private val context: Context) {

    /** A bundled document: either a curated guide or the full text of an Act. */
    data class Document(
        val assetName: String,
        val title: String,
        val kind: Kind,
        /** Set when the Act is no longer in force, e.g. the 1961 Income-tax Act. */
        val statusNote: String? = null
    ) {
        /** Title with the trailing provenance clauses stripped, for list rows. */
        val shortTitle: String
            get() = title.substringBefore(" \u2014 ").trim().ifEmpty { title }
    }

    enum class Kind {
        /** Short, plain-language guidance written for this app. */
        GUIDE,

        /** The complete, unedited text of an Act as published. */
        FULL_ACT
    }

    /** One heading-delimited section of a document. */
    data class Section(val heading: String, val body: String)

    /**
     * Lists everything bundled, guides first.
     *
     * Only the first few lines of each file are read, so this stays fast even
     * though the library is about 12 MB — reading all of it to build a list would
     * stall the UI on older phones.
     */
    suspend fun list(): List<Document> = withContext(Dispatchers.IO) {
        val names = runCatching {
            context.assets.list(ASSET_DIR).orEmpty()
        }.getOrDefault(emptyArray())
        names
            .filter { it.endsWith(".md") && it != "README.md" }
            .sorted()
            .mapNotNull { name -> runCatching { describe(name) }.getOrNull() }
            // Guides before full Acts: a worried user wants the plain-language
            // page, not 4 MB of the Income-tax Act.
            .sortedWith(compareBy({ it.kind.ordinal }, { it.assetName }))
    }

    /** Splits a document into its markdown sections, for lazy rendering. */
    suspend fun sections(assetName: String): List<Section> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Section>()
        var heading = ""
        val body = StringBuilder()
        fun flush() {
            val text = body.toString().trim()
            if (heading.isNotEmpty() || text.isNotEmpty()) {
                out += Section(heading, text)
            }
            body.setLength(0)
        }
        open(assetName).use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#")) {
                    flush()
                    heading = trimmed.trimStart('#').trim()
                } else {
                    body.appendLine(line)
                }
            }
        }
        flush()
        out.filter { it.heading.isNotEmpty() || it.body.isNotEmpty() }
    }

    private fun describe(assetName: String): Document {
        var title: String? = null
        var status: String? = null
        var sawStatusHeading = false
        open(assetName).use { reader ->
            var lines = 0
            for (line in reader.lineSequence()) {
                val trimmed = line.trim()
                if (title == null && trimmed.startsWith("# ")) {
                    title = trimmed.removePrefix("# ").trim()
                } else if (trimmed.startsWith(STATUS_HEADING)) {
                    sawStatusHeading = true
                } else if (sawStatusHeading && status == null && trimmed.isNotEmpty()) {
                    status = shortStatus(trimmed)
                }
                if (++lines >= HEADER_SCAN_LINES) break
            }
        }
        return Document(
            assetName = assetName,
            title = title ?: assetName.removeSuffix(".md").replace('_', ' '),
            kind = if (assetName.contains(FULL_ACT_MARKER)) Kind.FULL_ACT else Kind.GUIDE,
            statusNote = status
        )
    }

    private fun open(assetName: String) =
        context.assets.open("$ASSET_DIR/$assetName").bufferedReader(Charsets.UTF_8)

    /**
     * Reduces the fetcher's status paragraph to its bold lead, e.g.
     * "STATUS: OLD LAW — REPEALED." The rest of the paragraph is several lines
     * long and belongs on the document itself, not in a list row badge.
     */
    private fun shortStatus(line: String): String {
        if (!line.startsWith("**")) return line.take(MAX_STATUS_CHARS)
        val end = line.indexOf("**", startIndex = 2)
        val lead = if (end > 2) line.substring(2, end) else line.removePrefix("**")
        return lead.trim().removeSuffix(".").take(MAX_STATUS_CHARS)
    }

    companion object {
        private const val ASSET_DIR = "nyaya_kb"

        /** Files fetched by tools/kb/fetch_full_kb.py carry this in their name. */
        private const val FULL_ACT_MARKER = "_full_"

        /** Written by the fetcher for Acts whose status needs flagging. */
        private const val STATUS_HEADING = "## Status of this Act"

        /**
         * How far into a file to look for the title and status banner. The
         * fetcher writes both within the first few dozen lines; scanning further
         * would mean paying to read multi-megabyte Acts just to build a list.
         */
        private const val HEADER_SCAN_LINES = 60

        /** Badge text longer than this would wrap and unbalance the list row. */
        private const val MAX_STATUS_CHARS = 48
    }
}
