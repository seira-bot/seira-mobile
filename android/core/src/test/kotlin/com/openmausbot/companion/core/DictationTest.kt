package com.openmausbot.companion.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Expectations from `ios/Tests/CompanionCoreTests/DictationTests.swift` —
 * frozen base, partial replacement, and locale candidates. Not derived from
 * the Kotlin under test.
 *
 * Provenance cases below pin the pure helpers only. The integrated composer
 * path (holder + leave-to-roster + send/clear) lives in app
 * `ChatDraftHolderTest` against `ChatComposerDraft`.
 */
class DictationTest {
    @Test
    fun emptyComposerTakesTheTranscript() {
        assertEquals("hello", Dictation.draft(base = "", transcript = "hello"))
    }

    @Test
    fun emptyTranscriptLeavesTheBase() {
        // The first callback has not arrived yet. Wiping the field in that
        // window would look like the mic deleted what you had typed.
        assertEquals("please look", Dictation.draft(base = "please look", transcript = ""))
        assertEquals("please look", Dictation.draft(base = "please look", transcript = "   "))
    }

    @Test
    fun spokenTextAppendsAfterTypedText() {
        assertEquals(
            "please look at the logs",
            Dictation.draft(base = "please", transcript = "look at the logs"),
        )
    }

    @Test
    fun whitespaceAroundEitherSideIsTrimmed() {
        assertEquals(
            "please look",
            Dictation.draft(base = "  please  ", transcript = "  look  "),
        )
    }

    /**
     * The contract ChatView has to keep: the base is the text at the
     * moment listening started, not the live draft. Re-joining against
     * that frozen base is how a later partial replaces an earlier one
     * instead of concatenating onto it.
     */
    @Test
    fun aLaterPartialReplacesAnEarlierOne() {
        val base = "please"
        assertEquals("please look", Dictation.draft(base = base, transcript = "look"))
        assertEquals(
            "please look at the logs",
            Dictation.draft(base = base, transcript = "look at the logs"),
        )
    }

    @Test
    fun bothEmptyStaysEmpty() {
        assertEquals("", Dictation.draft(base = "", transcript = ""))
        assertEquals("", Dictation.draft(base = "  ", transcript = "\n"))
    }

    // --- Transcript updates across segments --------------------------------

    @Test
    fun emptyTranscriptTakesTheNewPartial() {
        assertEquals("hello", Dictation.updateTranscript("", "hello"))
    }

    @Test
    fun emptyPartialLeavesTheTranscript() {
        assertEquals("hello", Dictation.updateTranscript("hello", ""))
        assertEquals("hello", Dictation.updateTranscript("hello", "   "))
    }

    /** Same segment: each longer partial replaces the last in place. */
    @Test
    fun aLongerPartialReplacesTheCurrentOne() {
        assertEquals(
            "first sentence",
            Dictation.updateTranscript("first sent", "first sentence"),
        )
        assertEquals(
            "first sentence.",
            Dictation.updateTranscript("first sentence", "first sentence."),
        )
    }

    /** A fresh segment after a pause does not start with the old one. */
    @Test
    fun aFreshSegmentIsAppendedAfterThePreviousText() {
        assertEquals(
            "first sentence. second sentence.",
            Dictation.updateTranscript("first sentence.", "second sentence."),
        )
    }

    /** If the recognizer revises a word in the same segment, most leading words match. */
    @Test
    fun aWordLevelRevisionReplacesInPlace() {
        assertEquals(
            "I walked to a store",
            Dictation.updateTranscript("I walked to the store", "I walked to a store"),
        )
    }

    /** A brief backtrack (new partial shorter but still a prefix) keeps the longer text. */
    @Test
    fun aBacktrackKeepsTheLongerTranscript() {
        assertEquals(
            "first sentence",
            Dictation.updateTranscript("first sentence", "first"),
        )
    }

    /** A new sentence that shares the first word is still treated as a new segment. */
    @Test
    fun aSharedFirstWordIsNotEnoughToMergeAcrossSegments() {
        assertEquals(
            "first sentence first word",
            Dictation.updateTranscript("first sentence", "first word"),
        )
    }

    // --- Recognizer rewrites of words it already spoke (seira-mobile#112) --

    /** Feed partials one by one, like the speech session does. */
    private fun transcriptAfter(vararg partials: String): String =
        partials.fold("") { current, partial -> Dictation.updateTranscript(current, partial) }

    @Test
    fun aPunctuationChangeInTheFirstWordIsNotANewSegment() {
        assertEquals("Iya.", transcriptAfter("Iya", "Iya."))
        assertEquals(
            "Halo apa kabar hari ini",
            transcriptAfter("Halo", "Halo, apa kabar", "Halo apa kabar hari ini"),
        )
    }

    @Test
    fun aCaseChangeInTheFirstWordIsNotANewSegment() {
        assertEquals(
            "Hello there my friend",
            transcriptAfter("hello there", "Hello there my friend"),
        )
    }

    @Test
    fun aRewriteThatDropsTheFirstWordsDoesNotRepeatThem() {
        // Partials from seira-mobile#112: the recognizer first heard "Iya"
        // twice, then dropped the first one.
        assertEquals(
            "Iya dari tadi kenapa berulang",
            transcriptAfter(
                "Iya",
                "Iya.",
                "Iya. Iya dari tadi",
                "Iya. Iya dari tadi kenapa?",
                "Iya dari tadi kenapa berulang",
            ),
        )
    }

    @Test
    fun aBacktrackWithDifferentPunctuationKeepsTheLongerTranscript() {
        assertEquals(
            "Halo, apa kabar",
            Dictation.updateTranscript("Halo, apa kabar", "Halo apa"),
        )
    }

    @Test
    fun aNewSegmentThatSharesTwoWordsWithTheTailIsStillAppended() {
        assertEquals(
            "kita makan nasi goreng nasi goreng enak",
            Dictation.updateTranscript("kita makan nasi goreng", "nasi goreng enak"),
        )
    }

    @Test
    fun aNewSegmentAfterPunctuatedTextIsStillAppended() {
        assertEquals(
            "Halo, apa kabar? Saya baik.",
            transcriptAfter("Halo, apa kabar?", "Saya baik."),
        )
    }

    @Test
    fun preferredLanguageComesFirst() {
        val locales = Dictation.localeCandidates(
            preferredLanguages = listOf("fr-FR", "de-DE"),
            current = Locale.forLanguageTag("en-US"),
        )
        assertEquals("fr_fr", Dictation.canonicalIdentifier(locales[0]))
        assertTrue(locales.map(Dictation::canonicalIdentifier).contains("en_us"))
    }

    @Test
    fun englishIsNotDuplicatedWhenItIsAlreadyPreferred() {
        val locales = Dictation.localeCandidates(
            preferredLanguages = listOf("en-US"),
            current = Locale.forLanguageTag("en-US"),
        )
        assertEquals(listOf("en_us"), locales.map(Dictation::canonicalIdentifier))
    }

    @Test
    fun hyphenAndUnderscoreAreTheSameCandidate() {
        val locales = Dictation.localeCandidates(
            preferredLanguages = listOf("en-US"),
            // Swift passes Locale(identifier: "en_US") as current.
            current = Dictation.localeFromTag("en_US"),
        )
        assertEquals(listOf("en_us"), locales.map(Dictation::canonicalIdentifier))
    }

    @Test
    fun englishIsTheLastResortWhenNothingElseIsOffered() {
        val locales = Dictation.localeCandidates(
            preferredLanguages = emptyList(),
            current = Locale.forLanguageTag("ja-JP"),
        )
        assertEquals("ja_jp", Dictation.canonicalIdentifier(locales[0]))
        assertEquals("en_us", locales.last().let(Dictation::canonicalIdentifier))
    }

    @Test
    fun preferredLanguageProbingIsBounded() {
        val locales = Dictation.localeCandidates(
            preferredLanguages = listOf("fr-FR", "de-DE", "it-IT", "es-ES", "pt-BR"),
            current = Locale.forLanguageTag("ja-JP"),
        )
        val keys = locales.map(Dictation::canonicalIdentifier)
        assertEquals(listOf("fr_fr", "de_de", "it_it"), keys.take(3))
        assertFalse(keys.contains("es_es"))
        assertFalse(keys.contains("pt_br"))
        assertEquals(listOf("ja_jp", "en_us"), keys.takeLast(2))
    }

    // --- Provenance: typed snapshot vs volatile draft --------------------

    @Test
    fun typedTextSurvivesRecreationThroughTheSnapshot() {
        // Rotation / process death drops the in-memory holder; only the
        // saveable typed snapshot may return. Pure typed text must come back.
        var provenance = DraftProvenance()
        provenance = Dictation.afterTypedEdit(provenance, "please look at the logs")
        assertEquals("please look at the logs", provenance.typedSnapshot)
        assertFalse(provenance.contaminated)

        // Holder gone — seed from snapshot only.
        val restored = Dictation.seedVolatileDraft(
            held = null,
            typedSnapshot = Dictation.restoredDraft(provenance),
        )
        assertEquals("please look at the logs", restored.text)
        assertFalse(restored.contaminated)
    }

    @Test
    fun dictatedTextDoesNotSurviveRecreation() {
        var provenance = DraftProvenance()
        provenance = Dictation.afterTypedEdit(provenance, "please")
        val live = Dictation.draft(base = "please", transcript = "look at the logs")
        provenance = Dictation.afterDictation(provenance)

        assertEquals("please look at the logs", live)
        assertEquals("please", provenance.typedSnapshot)
        assertTrue(provenance.contaminated)

        // Activity recreation: holder discarded, spoken part must not return.
        val restored = Dictation.seedVolatileDraft(
            held = null,
            typedSnapshot = Dictation.restoredDraft(provenance),
        )
        assertEquals("please", restored.text)
        assertFalse(restored.contaminated)
        assertFalse(restored.text.contains("look at the logs"))
    }

    @Test
    fun editAfterDictationDoesNotSmuggleSpeechIntoTheSnapshot() {
        var provenance = DraftProvenance()
        provenance = Dictation.afterTypedEdit(provenance, "please")
        var live = Dictation.draft(base = "please", transcript = "look")
        provenance = Dictation.afterDictation(provenance)

        // User edits the contaminated draft — the whole string still contains
        // speech and must not be copied into the saveable snapshot.
        live = "$live at the logs!"
        provenance = Dictation.afterTypedEdit(provenance, live)

        assertEquals("please look at the logs!", live)
        assertEquals("please", provenance.typedSnapshot)
        assertTrue(provenance.contaminated)

        val restored = Dictation.seedVolatileDraft(
            held = null,
            typedSnapshot = Dictation.restoredDraft(provenance),
        )
        assertEquals("please", restored.text)
        assertFalse(restored.text.contains("look"))
    }

    @Test
    fun sendOrClearResetsProvenance() {
        var provenance = Dictation.afterDictation(
            Dictation.afterTypedEdit(DraftProvenance(), "please"),
        )
        assertTrue(provenance.contaminated)
        assertEquals("please", provenance.typedSnapshot)

        provenance = Dictation.afterClear()
        assertEquals(DraftProvenance(), provenance)

        // After reset, typing is clean again and updates the snapshot.
        provenance = Dictation.afterTypedEdit(provenance, "new draft")
        assertEquals("new draft", provenance.typedSnapshot)
        assertFalse(provenance.contaminated)
    }

    @Test
    fun emptyingTheFieldResetsProvenance() {
        var provenance = Dictation.afterDictation(
            Dictation.afterTypedEdit(DraftProvenance(), "please"),
        )
        assertTrue(provenance.contaminated)
        assertEquals("please", provenance.typedSnapshot)

        provenance = Dictation.afterTypedEdit(provenance, "")
        assertEquals(DraftProvenance(), provenance)
    }

    @Test
    fun seedPrefersHeldVolatileOverTypedSnapshot() {
        val held = VolatileDraft(
            text = "please look at the logs",
            contaminated = true,
        )
        val seeded = Dictation.seedVolatileDraft(
            held = held,
            typedSnapshot = "please",
        )
        assertEquals("please look at the logs", seeded.text)
        assertTrue(seeded.contaminated)
    }
}
