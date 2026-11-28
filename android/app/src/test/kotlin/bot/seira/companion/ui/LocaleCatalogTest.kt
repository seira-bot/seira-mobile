package bot.seira.companion.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every language folder under `res/` must carry the same copy as `values`: same keys, same format
 * arguments, the plural forms the language needs, and text that aapt accepts.
 */
class LocaleCatalogTest {
    private val root = File("src/main/res").takeIf(File::isDirectory) ?: File("app/src/main/res")

    private class Entry(val plural: Boolean, val texts: Map<String, String>)

    private val formatArgument = Regex("%(?:\\d+\\$)?[-#+0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")
    private val quantities = setOf("zero", "one", "two", "few", "many", "other")

    private val requiredQuantities: Map<String, Set<String>> = mapOf(
        "ru" to setOf("one", "few", "many", "other"),
        "uk" to setOf("one", "few", "many", "other"),
        "pl" to setOf("one", "few", "many", "other"),
        "de" to setOf("one", "other"),
        "es" to setOf("one", "other"),
        "fr" to setOf("one", "other"),
        "hi" to setOf("one", "other"),
        "it" to setOf("one", "other"),
        "pt" to setOf("one", "other"),
        "tr" to setOf("one", "other"),
        "ja" to setOf("other"),
        "ko" to setOf("other"),
        "vi" to setOf("other"),
        "zh" to setOf("other"),
    )

    /** Later CLDR releases add `many` to the Romance languages. */
    private val optionalQuantities: Map<String, Set<String>> = mapOf(
        "es" to setOf("many"),
        "fr" to setOf("many"),
        "it" to setOf("many"),
        "pt" to setOf("many"),
    )

    private fun languageFolders(): List<File> = root.listFiles { file ->
        file.isDirectory && file.name.startsWith("values-") && file.listFiles { f ->
            f.name.startsWith("strings") && f.name.endsWith(".xml")
        }.orEmpty().isNotEmpty()
    }.orEmpty().sortedBy(File::getName)

    private fun languageOf(folder: String): String {
        val qualifier = folder.removePrefix("values-")
        return if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").substringBefore('+')
        else qualifier.substringBefore('-')
    }

    private fun catalog(folder: File): Map<String, Entry> {
        val entries = LinkedHashMap<String, Entry>()
        val files = folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            .orEmpty().sortedBy(File::getName)
        for (file in files) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val children = document.documentElement.childNodes
            for (index in 0 until children.length) {
                val node = children.item(index) as? Element ?: continue
                if (node.getAttribute("translatable") == "false") continue
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> entries[name] = Entry(false, mapOf("" to node.textContent))
                    "plurals" -> {
                        val items = node.getElementsByTagName("item")
                        entries[name] = Entry(
                            true,
                            (0 until items.length).map { items.item(it) as Element }
                                .associate { it.getAttribute("quantity") to it.textContent },
                        )
                    }
                }
            }
        }
        return entries
    }

    private fun arguments(text: String): List<String> =
        formatArgument.findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    private fun quoteProblem(text: String): String? {
        if (text.length >= 2 && text.first() == '"' && text.last() == '"') return null
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\') {
                index += 2
                continue
            }
            if (char == '\'' || char == '"') return "unescaped $char at $index"
            index++
        }
        if (text.startsWith("@") || text.startsWith("?")) return "starts with a bare ${text.first()}"
        return null
    }

    private fun problems(folderName: String, language: String, english: Map<String, Entry>, own: Map<String, Entry>): List<String> {
        val found = ArrayList<String>()
        for (key in english.keys - own.keys) found += "$folderName: missing $key"
        for (key in own.keys - english.keys) found += "$folderName: extra $key"
        val required = requiredQuantities[language]
        if (required == null) found += "$folderName: no plural rule known for language $language"
        for ((key, entry) in own) {
            val source = english[key] ?: continue
            if (entry.plural != source.plural) {
                found += "$folderName: $key is ${if (entry.plural) "plurals" else "string"} but English is " +
                    if (source.plural) "plurals" else "string"
                continue
            }
            val sourceOther = source.texts.getValue(if (source.plural) "other" else "")
            for ((quantity, text) in entry.texts) {
                val where = if (entry.plural) "$key[$quantity]" else key
                if (text.isBlank() && sourceOther.isNotBlank()) found += "$folderName: $where is blank"
                quoteProblem(text)?.let { found += "$folderName: $where has $it" }
                val sourceArguments = arguments(sourceOther)
                val ownArguments = arguments(text)
                if (!entry.plural || quantity == "other") {
                    if (ownArguments != sourceArguments) {
                        found += "$folderName: $where uses $ownArguments but English uses $sourceArguments"
                    }
                } else if (!sourceArguments.containsAll(ownArguments)) {
                    found += "$folderName: $where uses $ownArguments but English uses $sourceArguments"
                }
            }
            if (entry.plural && required != null) {
                val have = entry.texts.keys
                val missing = required - have
                if (missing.isNotEmpty()) found += "$folderName: $key lacks quantity $missing"
                val allowed = required + optionalQuantities[language].orEmpty()
                val unknown = have - quantities
                if (unknown.isNotEmpty()) found += "$folderName: $key has unknown quantity $unknown"
                val surplus = (have - allowed) - unknown
                if (surplus.isNotEmpty()) found += "$folderName: $key has quantity $surplus that $language does not use"
            }
        }
        return found
    }

    @Test
    fun englishPluralsUseOneAndOther() {
        val english = catalog(File(root, "values"))
        val plurals = english.filterValues { it.plural }
        assertTrue(plurals.isNotEmpty(), "no plurals in values")
        for ((key, entry) in plurals) assertEquals(setOf("one", "other"), entry.texts.keys, key)
    }

    @Test
    fun englishCopyIsSafeForAapt() {
        val found = catalog(File(root, "values")).flatMap { (key, entry) ->
            entry.texts.mapNotNull { (quantity, text) ->
                quoteProblem(text)?.let { "$key[$quantity] has $it" }
            }
        }
        assertTrue(found.isEmpty(), found.joinToString("\n"))
    }

    @Test
    fun everyLanguageFolderMatchesEnglish() {
        val english = catalog(File(root, "values"))
        assertTrue(english.size > 900, "English catalog looks empty: ${english.size}")
        val folders = languageFolders()
        val names = folders.map(File::getName)
        assertTrue("values-b+zh+Hans" in names && "values-b+zh+Hant" in names, "Chinese folders not found in $names")
        val found = folders.flatMap { folder ->
            problems(folder.name, languageOf(folder.name), english, catalog(folder))
        }
        assertTrue(found.isEmpty(), "${found.size} problems:\n" + found.take(60).joinToString("\n"))
    }

    @Test
    fun localeListComesFromTheResourceFolders() {
        val properties = File(root, "resources.properties")
        assertTrue(properties.isFile, "res/resources.properties is missing")
        assertTrue(properties.readText().lines().any { it.trim().startsWith("unqualifiedResLocale=") })
        assertTrue(!File(root, "xml/locales_config.xml").exists(), "a hand-written locale list drifts from the folders")
        val manifest = File(root.parentFile, "AndroidManifest.xml").readText()
        assertTrue("localeConfig" !in manifest, "the manifest must not name a locale list; the build writes it")
    }
}
