package cz.crabhi.dragons.engine

/** Builds a simple adventure outline from a premise without a language model. */
object OfflineAdventures {
    private val keywordMonsters =
        listOf(
            listOf("goblin") to "goblin",
            listOf("skret") to "orc",
            listOf("kobold") to "kobold",
            listOf("vlk", "vlc") to "wolf",
            listOf("kostliv", "kostr") to "skeleton",
            listOf("zombi", "mrtvol", "nemrt") to "zombie",
            listOf("ghul") to "ghoul",
            listOf("pavouk", "pavouc") to "large_spider",
            listOf("krys") to "giant_rat",
            listOf("zlobr") to "ogre",
            listOf("lupic", "bandit", "zbojnik", "loupez") to "bandit",
            listOf("medved") to "black_bear",
            listOf("gnol") to "gnoll",
            listOf("jester", "bazin") to "lizard_man",
            listOf("divocak", "kanec") to "wild_boar",
        )

    fun monstersFor(premise: String): List<String> {
        val tokens = CzechText.tokens(premise)
        val found = keywordMonsters.filter { (stems, _) -> CzechText.containsAnyStem(tokens, stems) }.map { it.second }
        return found.ifEmpty { listOf("goblin", "orc") }
    }

    fun create(
        premise: String,
        partySize: Int,
    ): Adventure {
        val monsters = monstersFor(premise)
        val main = monsters.first()
        val second = monsters.getOrElse(1) { if (main == "wolf") "bandit" else "wolf" }
        val boss = if (main in setOf("goblin", "orc", "kobold", "bandit")) "hobgoblin" else main
        val size = partySize.coerceIn(1, 4)
        val story = premise.trim().let { if (it.endsWith('.') || it.endsWith('!')) it else "$it." }
        return Adventure(
            title = "Dobrodružství: ${premise.trim().trimEnd('.')}",
            premise = premise,
            scenes =
                listOf(
                    Scene(
                        id = "village",
                        title = "Začátek cesty",
                        location = "Vesnice na kraji lesa",
                        description = "Vesničané vás obklopí a prosí o pomoc. $story",
                        goal = "Zjistit, kam se vydat, a vyrazit na cestu.",
                        npcs = listOf(Npc("Starosta Bohumil", "starosta vesnice")),
                    ),
                    Scene(
                        id = "road",
                        title = "Přepadení na cestě",
                        location = "Lesní cesta",
                        description = "Na úzké cestě mezi stromy se ozve šramot. Někdo na vás čekal.",
                        goal = "Přežít přepadení.",
                        encounter = Encounter(listOf(MonsterGroup(second, size))),
                    ),
                    Scene(
                        id = "trail",
                        title = "Stopy v lese",
                        location = "Hluboký les",
                        description = "Stopy vedou hlouběji do lesa k temné rokli.",
                        goal = "Najít doupě nepřátel.",
                        magicItem = true,
                    ),
                    Scene(
                        id = "lair",
                        title = "Doupě",
                        location = "Jeskyně v rokli",
                        description = "Z jeskyně čpí kouř a ozývá se vrčení. Tady je konec vaší cesty.",
                        goal = "Porazit vůdce a splnit úkol.",
                        encounter = Encounter(listOf(MonsterGroup(main, size), MonsterGroup(boss, 1)).distinctBy { it.monster }),
                    ),
                ),
        )
    }
}
