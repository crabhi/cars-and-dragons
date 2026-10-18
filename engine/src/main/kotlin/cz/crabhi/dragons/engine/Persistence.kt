package cz.crabhi.dragons.engine

import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Saves the current game as JSON. Writes go to a temp file first so a crash never corrupts the save. */
class SaveStore(
    private val dir: File,
) {
    private val file get() = File(dir, "current.json")

    fun save(state: GameState) {
        dir.mkdirs()
        val tmp = File(dir, "current.json.tmp")
        tmp.writeText(GameJson.encodeToString(GameState.serializer(), state), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun load(): GameState? {
        if (!file.exists()) return null
        return try {
            GameJson.decodeFromString(GameState.serializer(), file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            file.renameTo(File(dir, "broken-${System.currentTimeMillis()}.json"))
            null
        }
    }

    fun clear() {
        file.delete()
    }
}

/** Local transcripts for debugging and recaps. Never uploaded. Files older than [retention] are deleted. */
class TranscriptStore(
    private val dir: File,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val retention: Duration = Duration.ofDays(30),
) {
    fun append(
        gameId: String,
        speaker: String,
        text: String,
    ) {
        dir.mkdirs()
        val today = LocalDate.now(clock)
        val time = LocalTime.now(clock).withNano(0)
        File(dir, "$today-$gameId.txt").appendText("[$time] $speaker: $text\n", Charsets.UTF_8)
    }

    fun purgeOld(): Int {
        val cutoff = clock.millis() - retention.toMillis()
        val old = dir.listFiles { f -> f.isFile && f.lastModified() < cutoff }.orEmpty()
        old.forEach { it.delete() }
        return old.size
    }

    fun files(): List<File> = dir.listFiles().orEmpty().sortedBy { it.name }
}
