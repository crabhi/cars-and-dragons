# Cars & Dragons – český glosář.
# Jediný zdroj české terminologie: používá ho prompt vypravěče, obrazovka v autě i TTS.
# Formát: klíč = hodnota. Tvary pro počty: jeden|2–4|5+ (např. život|životy|životů).
# THAC0 se nikdy nevyslovuje; útok buď "zasáhl", nebo "minul".

# --- základní pojmy ---
term.hp = život|životy|životů
term.hp_label = Životy
term.ac = třída zbroje
term.ac_label = TZ
term.saving_throw = záchranný hod
term.initiative = iniciativa
term.level = úroveň
term.experience = zkušenosti
term.round = kolo
term.hit = zasáhl
term.miss = minul
term.party = Družina
term.enemies = Nepřátelé
term.turn = Na tahu
term.damage = zranění|zranění|zranění
term.spell_slot = kouzlo|kouzla|kouzel
term.xp_points = bod zkušeností|body zkušeností|bodů zkušeností

# --- stav ---
health.ok = v pořádku
health.unconscious = v bezvědomí
health.dead = mrtvý
condition.asleep = spí
condition.charmed = okouzlen
condition.blessed = požehnán
condition.shielded = magický štít
condition.protected_from_evil = ochrana před zlem
condition.blinded = oslepen
condition.paralyzed = ochromen
condition.commanded = pod rozkazem
status.fled = utekl

# --- rasy a povolání ---
race.human = člověk
race.dwarf = trpaslík
race.elf = elf
race.halfling = půlčík
class.fighter = bojovník
class.cleric = kněz
class.mage = mág
class.thief = zloděj

# --- vlastnosti ---
ability.str = síla
ability.dex = obratnost
ability.con = odolnost
ability.int = inteligence
ability.wis = moudrost
ability.cha = charisma

# --- dovednosti ---
proficiency.tracking = stopování
proficiency.healing = léčitelství
proficiency.mountaineering = horolezectví
proficiency.swimming = plavání
proficiency.hiding = skrývání
proficiency.moving_silently = tichý pohyb
proficiency.opening_locks = otevírání zámků
proficiency.spellcraft = znalost kouzel
proficiency.religion = náboženství
proficiency.blind_fighting = boj naslepo
proficiency.endurance = vytrvalost
proficiency.ancient_history = dávná historie
proficiency.direction_sense = orientace
proficiency.listening = naslouchání

# --- kouzla ---
spell.sleep = Spánek
spell.magic_missile = Magická střela
spell.shield = Štít
spell.charm_person = Okouzlení osoby
spell.light = Světlo
spell.detect_magic = Odhalení magie
spell.protection_from_evil = Ochrana před zlem
spell.cure_light_wounds = Léčení lehkých zranění
spell.bless = Požehnání
spell.command = Rozkaz

# --- předměty a zbraně ---
item.healing_potion = léčivý lektvar|léčivé lektvary|léčivých lektvarů
item.torch = pochodeň|pochodně|pochodní
item.rope = lano|lana|lan
weapon.long_sword = dlouhý meč
weapon.short_sword = krátký meč
weapon.battle_axe = válečná sekera
weapon.spear = kopí
weapon.mace = palcát
weapon.warhammer = válečné kladivo
weapon.quarterstaff = hůl
weapon.dagger = dýka
weapon.sling = prak
weapon.short_bow = krátký luk
weapon.light_crossbow = lehká kuše
weapon.unarmed = pěst

# --- netvoři (jeden|2–4|5+) ---
monster.kobold = kobold|koboldi|koboldů
monster.goblin = goblin|goblini|goblinů
monster.giant_rat = obří krysa|obří krysy|obřích krys
monster.giant_centipede = obří stonožka|obří stonožky|obřích stonožek
monster.bandit = lupič|lupiči|lupičů
monster.orc = skřet|skřeti|skřetů
monster.skeleton = kostlivec|kostlivci|kostlivců
monster.hobgoblin = hobgoblin|hobgoblini|hobgoblinů
monster.stirge = stirga|stirgy|stirg
monster.fire_beetle = ohnivý brouk|ohniví brouci|ohnivých brouků
monster.large_spider = obří pavouk|obří pavouci|obřích pavouků
monster.zombie = zombie|zombie|zombie
monster.gnoll = gnol|gnolové|gnolů
monster.ghoul = ghúl|ghúlové|ghúlů
monster.troglodyte = troglodyt|troglodyti|troglodytů
monster.lizard_man = ještěrčí muž|ještěrčí muži|ještěrčích mužů
monster.wolf = vlk|vlci|vlků
monster.bugbear = medvědí skřet|medvědí skřeti|medvědích skřetů
monster.black_bear = černý medvěd|černí medvědi|černých medvědů
monster.wild_boar = divočák|divočáci|divočáků
monster.ogre = zlobr|zlobři|zlobrů

# --- odmítnuté akce ---
reject.not_your_turn = Teď není tvůj tah.
reject.cannot_act = Tvoje postava teď nemůže jednat.
reject.no_enemies = Žádný nepřítel tu není.
reject.unknown_target = Nevím, na koho míříš. Řekni to prosím jinak.
reject.invalid_target = Na tento cíl to nejde.
reject.unknown_spell = Takové kouzlo neznám.
reject.spell_not_known = To kouzlo tvoje postava neumí.
reject.spell_not_memorized = To kouzlo už nemáš připravené. Odpočinek ho obnoví.
reject.no_item = Ten předmět už nemáš.
reject.unknown_item = Takový předmět neznám.
reject.not_in_combat = To jde jen v boji.
reject.in_combat = Uprostřed boje to nejde.
reject.already_rested = V této scéně už jste odpočívali.
reject.no_ranged_weapon = Nemáš žádnou zbraň na dálku.
reject.unknown_ability = Nevím, jakou vlastnost chceš použít.

# --- systémové hlášky ---
msg.welcome = Vítejte u hry Auta a draci. Jsem váš vypravěč.
msg.ask_players = Kolik vás bude hrát? Řekněte číslo od jedné do čtyř.
msg.players_default = Nerozuměl jsem, hrají tedy dva hráči.
msg.party_intro = Vaše družina:
msg.ask_premise = Ať jeden z vás řekne krátký námět dobrodružství. Třeba: skřeti ukradli mlýnský kámen.
msg.premise_default = Skřeti ukradli mlýnský kámen a vesnice bez mouky hladoví.
msg.preparing = Chystám dobrodružství, chvilku strpení.
msg.resume = Pokračujeme v uložené hře.
msg.didnt_hear = Nic jsem neslyšel. Zkus to prosím znovu.
msg.skip_turn = Nikdo neodpověděl, postava tento tah vyčkává.
msg.try_again = Zkus to prosím jinak.
msg.offline = Ztratil jsem spojení se světem příběhů. Zkusím to znovu.
msg.offline_fallback = Spojení stále nefunguje. Budu vyprávět zjednodušeně, dokud se nevrátí.
msg.cost_warning = Upozornění: blíží se limit útraty za tuto hru.
msg.cost_exceeded = Limit útraty byl vyčerpán. Dál budu vyprávět zjednodušeně.
msg.saved_quit = Hra je uložená. Na shledanou na příští cestě.
msg.victory = Dobrodružství je u konce. Vyhráli jste!
msg.defeat = Družina padla. Tento příběh skončil.
msg.new_game = Začínáme novou hru.
msg.status = Stav družiny:
msg.paused = Hra je pozastavená.
msg.mic_permission = Povolte prosím v telefonu přístup k mikrofonu.
msg.your_turn = Na tahu je:
msg.what_do = Co uděláš?
