# Mod-Spezifikation: Lucky OneBlock (`luckyoneblock`)

> **An den ausführenden Entwickler-Agenten:**
> Dieses Dokument ist deine verbindliche Spezifikation für diese Minecraft-Mod.
> Halte dich strikt an die Loader-Vorgaben, die Zielversion (Minecraft 1.21.1 Fabric) und die Anti-Halluzinations-Regeln.
> Führe nach jedem Schritt den entsprechenden Verifikations-Befehl aus (`./gradlew build` oder `./gradlew runClient`).

---

## 1. Übersicht & Scope
- **Mod Name:** Lucky OneBlock
- **Mod ID:** `luckyoneblock`
- **Zielversion:** Minecraft 1.21.1
- **Loader:** Fabric
- **Umgebung:** `*` (Dedicated Server & Client, volle Multiplayer-Kompatibilität)
- **Basis-Package:** `de.exit.luckyoneblock`
- **In-Scope (v1.0):**
  - **Dedizierter Welttyp / Preset ("Lucky OneBlock"):** Erzeugt bei Welterstellung eine leere Void-Welt (keine natürlichen Vanilla-Blöcke außer dem Startblock).
  - **Start-Spawn:** Der Spieler spawnt direkt bei `(0, 65, 0)` auf dem Lucky Block bei `(0, 64, 0)`.
  - **Unendlicher Lucky Block (Instant Auto-Respawn):** Sobald der Block bei `(0, 64, 0)` abgebaut wird, spawnt sein Lucky-Drop/Event aus `simple_lucky_block` und im exakt selben Server-Tick wird der Block sofort wieder als `simple_lucky_block:lucky_block` platziert.
  - **Kein Herunterfallen (Fallschutz):** Durch den sofortigen Tick-Ersatz fällt der Spieler beim kontinuierlichen Abbauen physikalisch nicht in die Leere.
  - **Explosions- & Zerstörungsschutz:** Sollten TNT, Creeper oder andere Explosionen (z. B. durch einen unglücklichen Lucky-Drop) den Spot treffen, wird der Lucky Block bei `(0, 64, 0)` niemals permanent zerstört, sondern unverzüglich wiederhergestellt.
  - **Fallback-Mechanismus:** Falls `simple_lucky_block` nicht geladen ist oder dessen Block noch nicht registriert ist, nutzt die Mod einen robusten Fallback (z. B. `Blocks.GOLD_BLOCK` oder `Blocks.SPONGE`) und loggt eine saubere Warnung, ohne zu crashen.
- **Out-of-Scope (v1.0):**
  - Keine Pflicht-Befehle im Chat nötig (rein passiver, nahtloser Spielmodus).
  - Keine erzwungenen Starter-Items (Spieler startet mit leerer Hand, da Lucky Blocks mit der Hand abbaubar sind).
  - Keine externen GUI-Config-Bibliotheken in v1.0.

---

## 2. Tech-Stack & Umgebungs-Leitplanken
- **Java-Version:** Java 21 (`C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot`)
- **Build-Tool:** Gradle mit Fabric Loom (`gradlew`)
- **Mod-Dependencies:**
  - Minecraft `1.21.1`
  - Fabric Loader `0.16.10`
  - Fabric API `0.109.0+1.21.1`
  - Simple Lucky Block (`simple_lucky_block:lucky_block`) als Zielblock
  - Mappings: `net.fabricmc:yarn:1.21.1+build.3:v2`
- **Group:** `de.exit`
- **Entrypoints:**
  - Main: `de.exit.luckyoneblock.LuckyOneBlockMod` (`ModInitializer`)
  - ServerWorldEvents / PlayerBlockBreakEvents zur kontinuierlichen Überwachung und Wiederherstellung des OneBlock-Spots.

### Anti-Halluzinations- & Modding-Sicherheitsregeln (Minecraft 1.21.1)
- **Kein Client-Code auf dem Server:** Verwende für die Block-Regeneration und den Welt-Spawn niemals `MinecraftClient`, `Screen` oder Render-Klassen. Alle Logiken für Blockwechsel und Spieler-Teleport/Spawn laufen rein serverseitig über `ServerWorld` und `ServerPlayerEntity`.
- **Data Components statt rohem NBT:** Verwende in 1.21.1 `DataComponentTypes` anstelle veralteter `ItemStack.getOrCreateTag()`-Aufrufe.
- **Registry-Sicherheit:**
  - Nutze `BuiltInRegistries.BLOCK` und `Identifier.of("simple_lucky_block", "lucky_block")`.
  - Prüfe immer mit `BuiltInRegistries.BLOCK.containsId(...)` auf Existenz, bevor der Block platziert wird.
- **Tick-Synchronisation & Event-Handling:**
  - Für den Abbau: Nutze Fabric API's `PlayerBlockBreakEvents.AFTER` oder `PlayerBlockBreakEvents.BEFORE`.
  - Beim Event `AFTER` wird im selben Server-Tick `world.setBlockState(oneBlockPos, luckyBlockState)` ausgeführt.
  - Zur Absicherung gegen Explosionen: Registriere einen Listener bei `ServerTickEvents.END_WORLD_TICK` (oder `ExplosionEvents`), der prüft, ob die OneBlock-Position in der Overworld Luft ist. Wenn ja, wird sofort der Lucky Block wiederhergestellt!
- **Void World Preset:**
  - Erstelle das World Preset wahlweise über Datapack-JSONs (`data/luckyoneblock/worldgen/world_preset/lucky_oneblock.json` & `flat_level_generator_preset`) mit leerer Blockliste (`"structure_overrides": []`, `"layers": []`) oder über programmatischen Generator.

---

## 3. Architektur & Dateistruktur
```text
lucky-oneblock/
├── gradle/wrapper/
├── src/
│   └── main/
│       ├── java/
│       │   └── de/exit/luckyoneblock/
│       │       ├── LuckyOneBlockMod.java             # ModInitializer & Lifecycle
│       │       ├── logic/
│       │       │   └── OneBlockManager.java          # Block-Regeneration, Instant-Respawn & Blast-Protection
│       │       └── world/
│       │           └── WorldGenHandler.java          # Spawn-Logik & Setup bei neuer Welt
│       └── resources/
│           ├── fabric.mod.json                       # Mod-Metadaten
│           ├── assets/luckyoneblock/
│           │   └── lang/
│           │       ├── en_us.json                    # Englische Texte
│           │       └── de_de.json                    # Deutsche Texte
│           └── data/luckyoneblock/worldgen/
│               ├── world_preset/
│               │   └── lucky_oneblock.json           # Welt-Preset für das "Neue Welt erstellen"-Menü
│               └── flat_level_generator_preset/
│                   └── lucky_oneblock.json           # Void-Layer-Definition
├── build.gradle
├── gradle.properties
├── settings.gradle
└── .gitignore
```

---

## 4. Komponenten & Registrierungen

### 1. OneBlockManager (`de.exit.luckyoneblock.logic.OneBlockManager`)
- **Konstante Position:** `BlockPos ONE_BLOCK_POS = new BlockPos(0, 64, 0);`
- **Zielblock-Auflösung:**
  ```java
  public static BlockState getLuckyBlockState() {
      Identifier id = Identifier.of("simple_lucky_block", "lucky_block");
      if (BuiltInRegistries.BLOCK.containsId(id)) {
          return BuiltInRegistries.BLOCK.get(id).getDefaultState();
      }
      return Blocks.GOLD_BLOCK.getDefaultState();
  }
  ```
- **Abbau-Event (`PlayerBlockBreakEvents.AFTER`):**
  - Wenn `pos.equals(ONE_BLOCK_POS)` und `world.getRegistryKey() == World.OVERWORLD`:
  - `world.setBlockState(ONE_BLOCK_POS, getLuckyBlockState(), Block.NOTIFY_ALL);`
- **Tick-Sicherung (`ServerTickEvents.END_WORLD_TICK`):**
  - In der Overworld: Wenn `world.getBlockState(ONE_BLOCK_POS).isAir()`, sofort wiederherstellen! Dadurch ist der Block auch gegen TNT-Explosionen, Enderdrachen, Wither oder sonstige Umgebungseinflüsse immun.

### 2. Spawn-Setup (`ServerWorldEvents.LOAD` / `ServerPlayerEvents.AFTER_RESPAWN`)
- Sobald die Welt generiert/geladen wird:
  - Stelle sicher, dass `ONE_BLOCK_POS` existiert.
  - Setze den World-Spawn auf `(0, 65, 0)`.
- Wenn ein Spieler die Welt betritt oder respawnt und unter ihm Void ist:
  - Sichere Positionierung auf `(0.5, 65.0, 0.5)`.

---

## 5. Gameplay-Flow & Definition of Done (DoD)

### Akzeptanzkriterien
1. **Neue Welt erstellen:**
   - Im Singleplayer-Menü "Welt erstellen" -> Welttyp / Preset "Lucky OneBlock" auswählen.
   - Die Welt startet als reine Void-Welt (nur der Lucky Block existiert bei `(0, 64, 0)`).
2. **Spawn:**
   - Spieler steht direkt auf dem Lucky Block bei `(0.5, 65.0, 0.5)`.
3. **Abbau & Sofort-Ersatz:**
   - Spieler baut den Lucky Block ab (z. B. mit bloßer Hand).
   - Der Block bricht, das Lucky-Drop/Event (Items, Mobs, Strukturen, etc.) wird ausgelöst.
   - Der Block ist im exakt selben Server-Tick wieder da.
   - Der Spieler fällt nicht nach unten durch den Block.
4. **Explosionsschutz:**
   - Ein TNT-Drop oder Creeper explodiert direkt am OneBlock.
   - Der Lucky Block wird im nächsten Tick automatisch wieder an `(0, 64, 0)` hergestellt.
5. **Multiplayer / Dedicated Server:**
   - Die Mod läuft ohne Client-Abhängigkeiten stabil auf einem Dedicated Server.

---

## 6. Phasenweise Implementierungs-Roadmap

### Phase 1: Projekt-Setup & Build-Verifikation
- [x] Basisdateien (`build.gradle`, `gradle.properties`, `settings.gradle`, `.gitignore`) anlegen.
- [x] `fabric.mod.json` mit Entrypoints und Metadaten einrichten.
- [x] Simple Lucky Block von Modrinth als Jar-in-Jar eingebunden.
- [x] **Verifikation:** `./gradlew build` läuft fehlerfrei durch.

### Phase 2: Event-Handler & OneBlockManager
- [x] `OneBlockManager` implementieren (`PlayerBlockBreakEvents.AFTER`, Ziellogik für `simple_lucky_block:lucky_block`).
- [x] Tick-Sicherung für Explosions- und Zerstörungsschutz implementiert (`ServerTickEvents.END_WORLD_TICK`).
- [x] Fallschutz & Void-Rescue implementiert (Spieler fällt niemals in die Leere).
- [x] **Verifikation:** `./gradlew build` besteht erfolgreich.

### Phase 3: Void World Preset & Spawn-Logik
- [x] Worldgen-Preset-JSONs für das "Welt erstellen"-Menü eingebunden (`lucky_oneblock`).
- [x] Welterstellungs- und Spieler-Spawn-Event abgesichert (`(0, 65, 0)`).
- [x] **Verifikation:** Build erfolgreich, Welt-Preset definiert.

### Phase 4: Integrationstest & Polish
- [ ] Test mit und ohne installierte `simple_lucky_block`-Mod.
- [ ] Test von schnellem Dauerabbau und TNT-Explosionen auf dem Spot.
- [ ] Sprachdateien (`en_us.json`, `de_de.json`) finalisieren.
- [ ] **Verifikation:** Endgültiges JAR via `./gradlew build` fehlerfrei erzeugen.

---

## 7. Wichtige Konsolen-Befehle
- Test-Client starten: `./gradlew runClient`
- Test-Server starten: `./gradlew runServer`
- Mod kompilieren: `./gradlew build`
