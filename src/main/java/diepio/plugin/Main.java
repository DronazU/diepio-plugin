package diepio.plugin;

import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.ctype.ContentType;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.Random;

public class Main extends Plugin {

    // ================ CONSTANTS ================
    private static final float HUD_INTERVAL = 0.25f;
    private static final float EXP_INTERVAL = 0.25f;
    private static final float NORMAL_SHAPE_MIN = 0.25f;
    private static final float NORMAL_SHAPE_MAX = 1.5f;
    private static final float ALPHA_DELAY_MAX = 30f;
    private static final int MAX_SHAPES = 500;
    private static final int MAX_LEVEL = 75;
    private static final int DRONE_SPAWN_TIME = 90; // seconds
    private static final int CARRIER_CACHE_TICKS = 90;
    private static final float PVP_RADIUS = 25f;
    private static final float BASE_REGEN = 5f;
    private static final int BASE_HEALTH = 250;
    private static final int BASE_BODY = 15;
    private static final int BASE_BULLET = 5;
    private static final int BASE_DRONE_HP = 100;
    private static final float BASE_DAMAGE_RESISTANCE = 0.04f; // percentage
    private static final int EXP_MULT = 35;
    private static final float BASE_BOSS_SPAWN_TIME = 15f; // mins

    // ================ FIELDS ================
    private UnitType basicUnit;
    private UnitType droneUnit;
    private float healthRegen = BASE_REGEN;
    private int maxHealth = BASE_HEALTH;
    private int bodyDamage = BASE_BODY;
    private int bulletDamage = BASE_BULLET;
    private int baseDroneHealth = BASE_DRONE_HP;
    private int globalTick = 0;
    private static Block mazeWall;
    private static boolean mazeGenerated = false;

    private final Random random = new Random();

    private final ObjectMap<String, Tank> playerTanks = new ObjectMap<>();
    private final ObjectMap<Tile, Shape> activeShapes = new ObjectMap<>();
    private final ObjectMap<Integer, Integer> droneHealthMap = new ObjectMap<>();
    private final ObjectMap<Team, Unit> carrierCache = new ObjectMap<>();
    private final ObjectMap<Team, Float> teamTimers = new ObjectMap<>();
    private final ObjectMap<Integer, Integer> bossDroneLastHitTick = new ObjectMap<>(); // <-- добавлено
    private final Vec2 tmp1 = new Vec2();
    private final Vec2 tmp2 = new Vec2();
    private final Vec2 tmp3 = new Vec2();
    private float cacheTimer = 0f;

    private final ObjectMap<String, String[]> evolutionMap = new ObjectMap<>();
    private final ObjectMap<String, String[]> displayNames = new ObjectMap<>();
    private final ObjectMap<String, Float> classDamageMult = new ObjectMap<>();
    private final ObjectMap<Integer, Integer> droneLastHitTick = new ObjectMap<>();
    private final ObjectSet<String> autonomousCarriers = ObjectSet.with("fortress", "zenith", "corvus", "omura", "reign");
    private final ObjectSet<String> carrierUnitNames = ObjectSet.with("poly", "fortress", "zenith", "mega", "corvus", "omura", "reign", "quad", "oct");
    private final ObjectMap<String, Integer> playerRankPoints = new ObjectMap<>();
    private final ObjectMap<String, String> playerNames = new ObjectMap<>();
    private final Fi rankFile = new Fi("config/ranks.properties");
    private final ObjectMap<Unit, BossData> activeBosses = new ObjectMap<>();
    private Boss[] bossTemplates;
    private float bossSpawnTimer = 0f;

    private Seq<Block> normalBlocks = new Seq<>();
    private Seq<Block> alphaBlocks = new Seq<>();

    private static final int UPGRADE_MENU = 1;
    private static final int EVOLUTION_MENU = 2;

    // ================ INNER CLASSES ================
    public static class Tank {
        public float health = 200;
        public int maxHealth = 200;
        public int points = 0;
        public int experience = 0;
        public int level = 1;
        public float bodyDamage = 0;
        public int respExp;
        public Tile targetTile = null;
        public boolean showHud = true;
        public boolean evolvedTier1 = false;
        public boolean evolvedTier2 = false;
        public boolean evolvedTier3 = false;
        public boolean evolvedTier4 = false;
        public boolean evolvedTier5 = false;
        public int rankPoints = 0;

        public static class Bonus {
            public int healthRegen = 1;
            public int maxHealth = 1;
            public int bodyDamage = 1;
            public int bulletDamage = 1;
            public int resistance = 1;
            public int droneCount = 1;
            public int droneHealth = 1;
            public int sightRange = 1;
        }
        public Bonus bonus = new Bonus();
        public Tank() {}
    }

    public static class Boss extends Tank {
        public String bossName;
        public UnitType unitType;
        public int droneSpawnTimer;

        public Boss(String name, float health, int droneCount, int droneHealth, int droneSpawnTimer, UnitType unit) {
            super();

            this.bossName = name;
            this.health = health;
            this.maxHealth = (int)health;
            this.unitType = unit;
            this.droneSpawnTimer = droneSpawnTimer;

            this.bonus.healthRegen = 8;
            this.bonus.maxHealth = 8;
            this.bonus.bodyDamage = 8;
            this.bonus.bulletDamage = 8;
            this.bonus.resistance = 8;
            this.bonus.droneCount = droneCount;
            this.bonus.droneHealth = droneHealth;
            this.bonus.sightRange = 8;
            this.experience = 50000;
        }
    }

    public static class BossData {
        public final Boss boss;
        public final Unit unit;
        public float droneTimer = 0f;
        public Seq<Unit> drones = new Seq<>();
        public BossData(Boss boss, Unit unit) {
            this.boss = boss;
            this.unit = unit;
        }
    }

    public static class Shape {
        public float health;
        public float maxHealth;
        public int expReward;
        public Shape(Block block) {
            String name = block.name;
            if (name.equals("dp-square")) {
                maxHealth = 20f;
                expReward = 15;
            } else if (name.equals("dp-triagle")) {
                maxHealth = 55f;
                expReward = 50;
            } else if (name.equals("dp-pentagon")) {
                maxHealth = 200f;
                expReward = 130;
            } else if (name.equals("dp-alpha-pentagon")) {
                maxHealth = 7500f;
                expReward = 2000;
            } else {
                maxHealth = 1f;
                expReward = 0;
            }
            health = maxHealth;
        }
    }

    public static class RankSystem {
        public static class Rank {
            public final String name;
            public final String emoji;
            public final int rankPointsRequired;

            public Rank(String name, String emoji, int rankPointsRequired) {
                this.name = name;
                this.emoji = emoji;
                this.rankPointsRequired = rankPointsRequired;
            }
        }

        public static final Rank[] RANKS = {
                new Rank("[gray]null[]", "\uF833", 0),
                new Rank("[#495057]Basi[#CED4DA]c[]", "\uF7EB", 25),
                new Rank("[#495057]Poun[#CED4DA]de[#F8F9FA]r[]", "\uF7FA", 50),
                new Rank("[#CED4DA]Di[#F8F9FA]re[#FFFFFF]ctor[]", "\uF800", 75),
                new Rank("[#F8F9FA]Ov[#22A6B3]er[#0E8C9D]seer[]", "\uF7FD", 100),
                new Rank("[#22A6B3]Ov[#0E8C9D]er[#0652DD]lord[]", "\uF7FC", 200),
                new Rank("[#0E8C9D]Ne[#0652DD]cr[#6C5CE7]omancer[]", "\uF7FF", 300),
                new Rank("[#0652DD]Cr[#6C5CE7]ui[#A29BFE]ser[]", "\uF7F9", 400),
                new Rank("[#6C5CE7]Ba[#A29BFE]tt[#FD79A8]leship[]", "\uF7FE", 500),
                new Rank("[#A29BFE]Dr[#FD79A8]ea[#E17055]dnought[]", "\uF7FB", 1000),
                new Rank("[#FD79A8]El[#E17055]it[#FDCB6E]e Destroyer[]", "\uF7F8", 2000),
                new Rank("[#E17055]Ne[#FDCB6E]st[#FFEAA7] Warden[]", "\uF7F4", 3000),
                new Rank("[#FDCB6E]Ro[#FFEAA7]gu[#FF7675]e Armada[]", "\uF7C3", 4000),
                new Rank("[#FFEAA7]Ex[#FF7675]or[#D63031]cistor[]", "\uF7C4", 5000),
                new Rank("[#FF7675]Za[#D63031]ph[#E17055]kiel[]", "\uF782", 10000),
                new Rank("[#D63031]Th[#E17055]au[#FFEAA7]maturge[]", "\uF780", 20000),
                new Rank("[#E17055]Kr[#FDCB6E]on[#FFEAA7]os[]", "\uF7C6", 30000)
        };

        public static Rank getRank(int rankPoints) {
            Rank result = RANKS[0];
            for (Rank rank : RANKS) {
                if (rankPoints >= rank.rankPointsRequired) {
                    result = rank;
                }
            }
            return result;
        }
    }

    private void generateMaze() {
        if (mazeWall == null || mazeGenerated) return;

        int wallSize = 2;
        int passageSize = 20;

        int worldWidth = Vars.world.width();
        int worldHeight = Vars.world.height();

        int centerW = 124;
        int centerH = 123;
        int startCenterX = (worldWidth - centerW) / 2;
        int endCenterX = startCenterX + centerW;
        int startCenterY = (worldHeight - centerH) / 2;
        int endCenterY = startCenterY + centerH;

        int cols = (worldWidth - wallSize * 2) / (passageSize + wallSize) + 1;
        int rows = (worldHeight - wallSize * 2) / (passageSize + wallSize) + 1;

        int gridWidth = 2 * cols + 1;
        int gridHeight = 2 * rows + 1;
        boolean[][] isWall = new boolean[gridHeight][gridWidth];

        for (int r = 0; r < gridHeight; r++) {
            for (int c = 0; c < gridWidth; c++) {
                isWall[r][c] = true;
            }
        }

        java.util.function.BiFunction<Integer, Integer, Boolean> isInCenterGrid = (r, c) -> {
            int x = wallSize + (c / 2) * (passageSize + wallSize) + passageSize / 2;
            int y = wallSize + (r / 2) * (passageSize + wallSize) + passageSize / 2;
            return (x >= startCenterX && x < endCenterX && y >= startCenterY && y < endCenterY);
        };

        Random rand = new Random();
        java.util.Stack<int[]> stack = new java.util.Stack<>();

        isWall[1][1] = false;
        stack.push(new int[]{1, 1});

        int[][] dirs = {{0, 2}, {2, 0}, {0, -2}, {-2, 0}};

        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            int r = current[0], c = current[1];
            java.util.List<int[]> neighbors = new java.util.ArrayList<>();

            for (int[] d : dirs) {
                int nr = r + d[0];
                int nc = c + d[1];

                if (nr > 0 && nr < gridHeight && nc > 0 && nc < gridWidth && isWall[nr][nc]) {
                    if (isInCenterGrid.apply(nr, nc)) {
                        isWall[nr][nc] = false;
                        isWall[r + d[0]/2][c + d[1]/2] = false;
                    } else {
                        neighbors.add(new int[]{nr, nc, r + d[0]/2, c + d[1]/2});
                    }
                }
            }

            if (!neighbors.isEmpty()) {
                int[] chosen = neighbors.get(rand.nextInt(neighbors.size()));
                isWall[chosen[0]][chosen[1]] = false;
                isWall[chosen[2]][chosen[3]] = false;
                stack.push(new int[]{chosen[0], chosen[1]});
            } else {
                stack.pop();
            }
        }

        for (int r = 1; r < gridHeight - 1; r++) {
            for (int c = 1; c < gridWidth - 1; c++) {
                if (isWall[r][c] && ((r % 2 == 0 && c % 2 != 0) || (r % 2 != 0 && c % 2 == 0))) {
                    if (!isInCenterGrid.apply(r, c) && rand.nextFloat() < 0.12f) {
                        isWall[r][c] = false;
                    }
                }
            }
        }

        for (int x = 0; x < worldWidth; x++) {
            for (int y = 0; y < worldHeight; y++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile == null) continue;

                boolean placeWall = false;

                if (x < wallSize || x >= worldWidth - wallSize || y < wallSize || y >= worldHeight - wallSize) {
                    placeWall = true;
                }
                else if (x >= startCenterX && x < endCenterX && y >= startCenterY && y < endCenterY) {
                    placeWall = false;
                }
                else {
                    int gridX = (x - wallSize) / (passageSize + wallSize);
                    int remX = (x - wallSize) % (passageSize + wallSize);
                    int mazeC = gridX * 2 + (remX >= passageSize ? 2 : 1);

                    int gridY = (y - wallSize) / (passageSize + wallSize);
                    int remY = (y - wallSize) % (passageSize + wallSize);
                    int mazeR = gridY * 2 + (remY >= passageSize ? 2 : 1);

                    if (mazeR >= gridHeight || mazeC >= gridWidth) {
                        placeWall = false;
                    } else {
                        placeWall = isWall[mazeR][mazeC];
                    }
                }

                if (placeWall) {
                    if (tile.block() == Blocks.air) {
                        Call.setTile(tile, mazeWall, Team.crux, 0);
                    }
                } else {
                    if (tile.block() == mazeWall) {
                        Call.setTile(tile, Blocks.air, Team.crux, 0);
                    }
                }
            }
        }

        mazeGenerated = true;
        Log.info("Balanced diep.io maze generated: tighter paths, clean edges.");
    }




    // ================ INIT ================
    @Override
    public void init() {
        basicUnit = UnitTypes.alpha;
        droneUnit = UnitTypes.merui;
        bossTemplates = new Boss[]{
                new Boss("Elite Crasher", 50000, 0, 0, 0, UnitTypes.corvus),
                new Boss("Enforce", 50000, 48, 100, 15, UnitTypes.omura),
                new Boss("Ragnarok Titan", 50000, 8, 2500, 600, UnitTypes.reign)
        };

        initEvolutions();
        initClassDamages();
        loadShapeBlocks();
        loadRanks();


        Timer.schedule(this::updateHud, 0, HUD_INTERVAL);
        Timer.schedule(this::expToLevel, 0, EXP_INTERVAL);
        Timer.schedule(this::spawnNormalShape, 0, NORMAL_SHAPE_MIN + random.nextFloat() * (NORMAL_SHAPE_MAX - NORMAL_SHAPE_MIN));
        Timer.schedule(this::spawnAlphaShape, 0, random.nextFloat() * ALPHA_DELAY_MAX);
        Timer.schedule(this::clearNonPlayerUnits, 0, 1f);
        Timer.schedule(this::updateLeaderboard, 0, 0.5f);

        Events.run(EventType.Trigger.update, this::onUpdate);

        Events.on(EventType.WorldLoadEvent.class, e -> {
            loadShapeBlocks();
            mazeGenerated = false;
            generateMaze();
        });
        Events.on(EventType.TileChangeEvent.class, e -> {
            if (e.tile != null && activeShapes.containsKey(e.tile)) {
                activeShapes.remove(e.tile);
            }
        });
        Events.on(EventType.PlayerJoin.class, this::onPlayerJoin);
        Events.on(EventType.BuildDamageEvent.class, this::onBuildDamage);
        Events.on(EventType.MenuOptionChooseEvent.class, this::onMenuOption);
        Events.on(EventType.UnitDamageEvent.class, this::onUnitDamage);
        Events.on(EventType.UnitDestroyEvent.class, this::onUnitDestroy);
        Events.on(EventType.PlayerLeave.class, this::onPlayerLeave);
        Events.on(EventType.BlockDestroyEvent.class, this::onBuildDestroy);
    }

    // ================ INIT HELPERS ================
    private void initEvolutions() {
        // Tier 1
        evolutionMap.put("alpha", new String[]{"dagger","nova","crawler","flare","mono","risso","retusa"});
        displayNames.put("alpha", new String[]{"\uF800 Machine gun","\uF7FD Twin","\uF7FA Rammer","\uF7F6 Flank guard","\uF7F1 Sniper","\uF7E7 Rocketseer","\uF788 Trapper"});
        // Tier 2
        evolutionMap.put("dagger", new String[]{"mace","horizon"});
        displayNames.put("dagger", new String[]{"\uF7FF Destroyer","\uF7F5 Gunner"});
        evolutionMap.put("nova", new String[]{"beta","horizon","pulsar"});
        displayNames.put("nova", new String[]{"\uF7EA Quad-tank","\uF7F5 Gunner","\uF7FC Triple shot"});
        evolutionMap.put("crawler", new String[]{"atrax"});
        displayNames.put("crawler", new String[]{"\uF7F9 Smasher"});
        evolutionMap.put("flare", new String[]{"gamma","beta"});
        displayNames.put("flare", new String[]{"\uF7E9 Auto-3","\uF7EA Quad-tank"});
        evolutionMap.put("mono", new String[]{"poly","pulsar"});
        displayNames.put("mono", new String[]{"\uF7F0 Overseer","\uF7FC Triple shot"});
        evolutionMap.put("risso", new String[]{"minke","elude","stell"});
        displayNames.put("risso", new String[]{"\uF7ED Battleseer","\uF697 Auto-rocketseer","\uF6B5 Rocket-trapper"});
        evolutionMap.put("retusa", new String[]{"oxynoe"});
        displayNames.put("retusa", new String[]{"\uF784 Tri-trapper"});
        // Tier 3
        evolutionMap.put("mace", new String[]{"fortress","locus"});
        displayNames.put("mace", new String[]{"\uF7FE Hybrid","\uF6B3 Annihilator"});
        evolutionMap.put("horizon", new String[]{"zenith","avert","evoke"});
        displayNames.put("horizon", new String[]{"\uF7F4 Over-gunner","\uF6B2 Auto-gunner","\uF735 Streamliner"});
        evolutionMap.put("beta", new String[]{"anthicus","cleroi"});
        displayNames.put("beta", new String[]{"\uF719 Octo-tank","\uF6B1 Auto-5"});
        evolutionMap.put("pulsar", new String[]{"anthicus","quasar", "obviate"});
        displayNames.put("pulsar", new String[]{"\uF719 Octo-tank","\uF7FB Penta shot","\uF6A3 Triplet"});
        evolutionMap.put("atrax", new String[]{"spiroct"});
        displayNames.put("atrax", new String[]{"\uF7F8 Aegis"});
        evolutionMap.put("gamma", new String[]{"cleroi"});
        displayNames.put("gamma", new String[]{"\uF6B1 Auto-5"});
        evolutionMap.put("poly", new String[]{"mega","bryde"});
        displayNames.put("poly", new String[]{"\uF7EF Overlord","\uF7EC Battleship"});
        evolutionMap.put("minke", new String[]{"bryde"});
        displayNames.put("minke", new String[]{"\uF7EC Battleship"});
        evolutionMap.put("elude", new String[]{"bryde"});
        displayNames.put("elude", new String[]{"\uF7EC Battleship"});
        evolutionMap.put("stell", new String[]{"bryde"});
        displayNames.put("stell", new String[]{"\uF7EC Battleship"});
        evolutionMap.put("oxynoe", new String[]{"cyerce"});
        displayNames.put("oxynoe", new String[]{"\uF783 Constructor"});
        //tier 4
        evolutionMap.put("fortress", new String[]{"scepter"});
        displayNames.put("fortress", new String[]{"\uF7DB Beta Guardian"});
        evolutionMap.put("locus", new String[]{"scepter"});
        displayNames.put("locus", new String[]{"\uF7DB Beta Guardian"});
        evolutionMap.put("zenith", new String[]{"antumbra"});
        displayNames.put("zenith", new String[]{"\uF7F3 Advanced Decimator"});
        evolutionMap.put("avert", new String[]{"antumbra"});
        displayNames.put("avert", new String[]{"\uF7F3 Advanced Decimator"});
        evolutionMap.put("evoke", new String[]{"antumbra"});
        displayNames.put("evoke", new String[]{"\uF7F3 Advanced Decimator"});
        evolutionMap.put("cleroi", new String[]{"antumbra"});
        displayNames.put("cleroi", new String[]{"\uF7F3 Advanced Decimator"});
        evolutionMap.put("anthicus", new String[]{"vela"});
        displayNames.put("anthicus", new String[]{"\uF7C1 Goliath Sprayer"});
        evolutionMap.put("quasar", new String[]{"vela"});
        displayNames.put("quasar", new String[]{"\uF7C1 Goliath Sprayer"});
        evolutionMap.put("obviate", new String[]{"vela"});
        displayNames.put("obviate", new String[]{"\uF7C1 Goliath Sprayer"});
        evolutionMap.put("spiroct", new String[]{"arkyid"});
        displayNames.put("spiroct", new String[]{"\uF7F7 Heavy Vanguard"});
        evolutionMap.put("mega", new String[]{"quad"});
        displayNames.put("mega", new String[]{"\uF7C3 Advanced Hive"});
        evolutionMap.put("bryde", new String[]{"quad"});
        displayNames.put("bryde", new String[]{"\uF7C3 Advanced Hive"});
        evolutionMap.put("zenith", new String[]{"quad"});
        displayNames.put("zenith", new String[]{"\uF7C3 Advanced Hive"});
        evolutionMap.put("fortress", new String[]{"quad"});
        displayNames.put("fortress", new String[]{"\uF7C3 Advanced Hive"});
        evolutionMap.put("cyerce", new String[]{"aegires"});
        displayNames.put("cyerce", new String[]{"\uF782 Combat Manager"});
        evolutionMap.put("bryde", new String[]{"sei"});
        displayNames.put("bryde", new String[]{"\uF7C4 Missile master"});
        //tier 5
        evolutionMap.put("scepter", new String[]{"eclipse"});
        displayNames.put("scepter", new String[]{"\uF7F2 Alpha Guardian"});
        evolutionMap.put("antumbra", new String[]{"eclipse"});
        displayNames.put("antumbra", new String[]{"\uF7F2 Alpha Guardian"});
        evolutionMap.put("sei", new String[]{"eclipse"});
        displayNames.put("sei", new String[]{"\uF7F2 Alpha Guardian"});
        evolutionMap.put("quad", new String[]{"oct"});
        displayNames.put("quad", new String[]{"\uF7C2 Mothership"});
        evolutionMap.put("vela", new String[]{"navanax"});
        displayNames.put("vela", new String[]{"\uF7C0 Gersemi"});
        evolutionMap.put("aegires", new String[]{"navanax"});
        displayNames.put("aegires", new String[]{"\uF7C0 Gersemi"});
        evolutionMap.put("arkyid", new String[]{"toxopid"});
        displayNames.put("arkyid", new String[]{"\uF7DE Dispector"});
    }

    private void initClassDamages() {
        // Tier 0
        classDamageMult.put("alpha", 1f);

        // Tier 1
        classDamageMult.put("dagger", 0.07f);
        classDamageMult.put("nova", 0.75f);
        classDamageMult.put("crawler", 1f);
        classDamageMult.put("flare", 1f);
        classDamageMult.put("mono", 2f);
        classDamageMult.put("risso", 1f);
        classDamageMult.put("retusa", 1.25f);

        // Tier 2
        classDamageMult.put("mace", 5f);
        classDamageMult.put("horizon", 0.35f);
        classDamageMult.put("beta", 1f);
        classDamageMult.put("pulsar", 1f);
        classDamageMult.put("atrax", 1f);
        classDamageMult.put("gamma", 1f);
        classDamageMult.put("poly", 1f);
        classDamageMult.put("minke", 0.75f);
        classDamageMult.put("elude", 1f);
        classDamageMult.put("stell", 1f);
        classDamageMult.put("oxynoe", 1.25f);

        // Tier 3
        classDamageMult.put("fortress", 5f);
        classDamageMult.put("locus", 5f);
        classDamageMult.put("zenith", 0.5f);
        classDamageMult.put("avert", 0.25f);
        classDamageMult.put("evoke", 1f);
        classDamageMult.put("cleroi", 1f);
        classDamageMult.put("anthicus", 1f);
        classDamageMult.put("quasar", 1f);
        classDamageMult.put("obviate", 1f);
        classDamageMult.put("spiroct", 0.1f);
        classDamageMult.put("mega", 1f);
        classDamageMult.put("bryde", 0.45f);
        classDamageMult.put("cyerce", 1f);

        // Tier 4
        classDamageMult.put("scepter", 2.5f);
        classDamageMult.put("antumbra", 1f);
        classDamageMult.put("sei", 1f);
        classDamageMult.put("quad", 1f);
        classDamageMult.put("vela", 1f);
        classDamageMult.put("aegires", 1f);
        classDamageMult.put("arkyid", 0.075f);

        // Tier 5
        classDamageMult.put("eclipse", 1f);
        classDamageMult.put("oct", 1f);
        classDamageMult.put("navanax", 1f);
        classDamageMult.put("toxopid", 0.075f);
    }

    private void loadShapeBlocks() {
        normalBlocks.clear();
        alphaBlocks.clear();
        Block sq = Vars.content.getByName(ContentType.block, "dp-square");
        Block tr = Vars.content.getByName(ContentType.block, "dp-triagle");
        Block pen = Vars.content.getByName(ContentType.block, "dp-pentagon");
        Block alpha = Vars.content.getByName(ContentType.block, "dp-alpha-pentagon");
        if (sq != null) normalBlocks.add(sq);
        if (tr != null) normalBlocks.add(tr);
        if (pen != null) normalBlocks.add(pen); alphaBlocks.add(pen);
        if (alpha != null) alphaBlocks.add(alpha);
        mazeWall = Vars.content.getByName(ContentType.block, "dp-maze-wall");
    }

    // ================ UPDATE LOOP ================
    private void onUpdate() {
        updateCarrierCache();
        spawnDrones();
        updateDroneAI();
        globalTick++;
        updateBossSpawning();
        updateBossAI();
    }

    private void updateCarrierCache() {
        cacheTimer += Time.delta;
        if (cacheTimer >= CARRIER_CACHE_TICKS) {
            cacheTimer = 0f;
            carrierCache.clear();
            Groups.unit.each(u -> {
                if (u.isValid() && carrierUnitNames.contains(u.type.name)) {
                    carrierCache.put(u.team(), u);
                }
            });
        }
    }

    private void spawnDrones() {
        carrierCache.each((team, carrier) -> {
            if (carrier == null || !carrier.isValid()) return;

            BossData bossData = activeBosses.get(carrier);
            boolean isBoss = bossData != null;

            float spawntime;
            int maxDrones;
            int droneHealth;
            float timer;

            if (isBoss) {
                spawntime = bossData.boss.droneSpawnTimer / 60f;
                maxDrones = bossData.boss.bonus.droneCount;
                droneHealth = bossData.boss.bonus.droneHealth;
                timer = bossData.droneTimer;
            } else {
                spawntime = DRONE_SPAWN_TIME;
                if (carrier.type == UnitTypes.mega) spawntime = DRONE_SPAWN_TIME / 2;
                else if (carrier.type == UnitTypes.quad) spawntime = DRONE_SPAWN_TIME / 4;
                else if (carrier.type == UnitTypes.oct) spawntime = DRONE_SPAWN_TIME / 8;

                Player player = carrier.getPlayer();
                if (player == null) return;
                Tank tank = playerTanks.get(player.uuid());
                if (tank == null) return;

                maxDrones = autonomousCarriers.contains(carrier.type.name) ? 3 : tank.bonus.droneCount;
                if (carrier.type == UnitTypes.quad) maxDrones = tank.bonus.droneCount * 2;
                if (carrier.type == UnitTypes.oct) maxDrones = tank.bonus.droneCount * 4;
                droneHealth = baseDroneHealth * tank.bonus.droneHealth;
                timer = teamTimers.get(team, 0f);
            }

            timer += Time.delta;
            if (timer >= spawntime) {
                timer = 0f;
                int alive = Groups.unit.count(u -> u.type == droneUnit && u.team() == team);
                if (alive < maxDrones) {
                    float angle = Mathf.random(0f, 360f);
                    float distance = Mathf.random(0f, 16f);
                    float dx = Mathf.cos(angle) * distance;
                    float dy = Mathf.sin(angle) * distance;
                    Unit drone = droneUnit.create(Team.sharded);
                    drone.set(carrier.x + dx, carrier.y + dy);
                    drone.add();
                    drone.team(team);
                    droneHealthMap.put(drone.id(), droneHealth);
                    if (isBoss) {
                        bossData.drones.add(drone);
                    }
                }
            }
            if (isBoss) {
                bossData.droneTimer = timer;
            } else {
                teamTimers.put(team, timer);
            }
        });
    }

    private void spawnBoss(Boss template) {
        float cx = Vars.world.width() / 2f * Vars.tilesize;
        float cy = Vars.world.height() / 2f * Vars.tilesize;
        Unit unit = template.unitType.create(Team.crux);
        unit.set(cx, cy);
        unit.maxHealth = template.maxHealth;
        unit.health = template.health;
        unit.add();
        activeBosses.put(unit, new BossData(template, unit));
        String message = "[scarlet]The [gold]" + template.bossName + "[] has awoken!";
        Call.sendMessage(message);
    }

    private void updateBossSpawning() {
        Seq<Unit> toRemove = new Seq<>();
        for (Unit u : activeBosses.keys()) {
            if (!u.isValid() || u.dead()) {
                toRemove.add(u);
                bossSpawnTimer = 0f;
            }
        }
        for (Unit u : toRemove) {
            BossData data = activeBosses.get(u);
            if (data != null) {
                for (Unit drone : data.drones) {
                    if (drone.isValid()) {
                        drone.kill();
                        bossDroneLastHitTick.remove(drone.id());
                    }
                }
                data.drones.clear();
            }
            activeBosses.remove(u);
        }

        if (!activeBosses.isEmpty()) return;

        bossSpawnTimer += Time.delta;
        if (bossSpawnTimer >= BASE_BOSS_SPAWN_TIME * 60f * 60f) {
            bossSpawnTimer = 0f;
            Boss template = bossTemplates[Mathf.random(bossTemplates.length - 1)];
            spawnBoss(template);
        }
    }

    private void applyBossDroneDamage(Unit drone, Player player, Boss boss) {
        if (drone == null || !drone.isValid() || drone.dead()) return;
        if (player == null || player.unit() == null || !player.unit().isValid()) return;
        float dist = drone.dst(player.unit());
        if (dist > drone.hitSize() + player.unit().hitSize()) return;

        int lastTick = bossDroneLastHitTick.get(drone.id(), 0);
        if (globalTick - lastTick < 15) return;
        bossDroneLastHitTick.put(drone.id(), globalTick);

        Tank hitTank = playerTanks.get(player.uuid());
        if (hitTank == null || hitTank.health <= 0) return;
        float damage = bulletDamage * boss.bonus.bulletDamage * 0.125f;
        hitTank.health -= damage * (1f - (hitTank.bonus.resistance - 1) * BASE_DAMAGE_RESISTANCE);
        if (hitTank.health <= 0) {
            hitTank.health = 0;
            player.unit().kill();
        }
    }

    private void updateBossAI() {
        for (ObjectMap.Entry<Unit, BossData> entry : activeBosses) {
            Unit boss = entry.key;
            if (boss == null || !boss.isValid() || boss.dead()) continue;

            Player target = null;
            float minDist = Float.MAX_VALUE;
            for (Player p : Groups.player) {
                if (p == null || p.unit() == null || !p.unit().isValid()) continue;
                float dist = p.unit().dst2(boss);
                if (dist < minDist) {
                    minDist = dist;
                    target = p;
                }
            }

            if (target != null) {
                Unit targetUnit = target.unit();
                float angle = boss.angleTo(targetUnit);
                boss.vel.trns(angle, boss.speed());
                boss.aimX = targetUnit.x;
                boss.aimY = targetUnit.y;
                boss.rotation = angle;
                boss.isShooting(true);
            } else {
                boss.vel.setZero();
                boss.isShooting(false);
            }
        }
    }

    private void updateDroneAI() {
        Groups.unit.each(drone -> {
            if (drone == null || !drone.isValid() || drone.type != droneUnit) return;
            Team team = drone.team();
            Unit carrier = carrierCache.get(team);
            if (carrier == null) {
                drone.vel.set(Vec2.ZERO);
                return;
            }
            BossData bossData = activeBosses.get(carrier);
            boolean isBoss = bossData != null;

            Vec2 target = null;

            if (isBoss) {
                float bossRange = 400f;
                Player enemy = null;
                float minDist = Float.MAX_VALUE;
                for (Player p : Groups.player) {
                    if (p == null || p.unit() == null || !p.unit().isValid()) continue;
                    float distToBoss = carrier.dst(p.unit());
                    if (distToBoss < minDist && distToBoss < bossRange) {
                        minDist = distToBoss;
                        enemy = p;
                    }
                }
                if (enemy != null) {
                    target = new Vec2(enemy.unit().x, enemy.unit().y);
                    applyBossDroneDamage(drone, enemy, bossData.boss);
                } else {
                    target = new Vec2(carrier.x, carrier.y);
                }
            } else {
                Player player = carrier.getPlayer();
                if (player == null) {
                    drone.kill();
                    droneHealthMap.remove(drone.id());
                    return;
                }
                Tank tank = playerTanks.get(player.uuid());
                if (tank == null) return;

                float range = player.unit().range() * (1.0f + (tank.bonus.sightRange - 1) * (1.0f / 7.0f));
                tmp1.set(carrier.x, carrier.y);
                boolean isAutonomous = autonomousCarriers.contains(carrier.type.name);
                boolean shooting = player.shooting() && !isAutonomous;

                if (shooting) {
                    tmp2.set(player.mouseX, player.mouseY);
                    target = tmp2;
                    if (tmp1.dst(target) > range) {
                        tmp3.set(target.x - tmp1.x, target.y - tmp1.y).setLength(range).add(tmp1);
                        target = tmp3;
                    }
                } else {
                    Unit enemy = Units.closestEnemy(team, drone.x, drone.y, range, Healthc::isValid);
                    if (enemy != null) {
                        tmp2.set(enemy.x, enemy.y);
                        target = tmp2;
                        if (tmp1.dst(target) > range) target = tmp1;
                    } else {
                        target = tmp1;
                    }
                }
                applyDroneDamage(drone, player);
            }
            if (target != null) {
                float dist = drone.dst(target.x, target.y);
                float radius = (target == tmp1) ? 44f : 0f;
                if (dist > radius + 8f) {
                    tmp3.set(target.x - drone.x, target.y - drone.y).setLength(drone.speed());
                    drone.vel.set(tmp3);
                    drone.lookAt(target);
                } else if (dist < radius - 8f && radius > 0f) {
                    tmp3.set(drone.x - target.x, drone.y - target.y).setLength(drone.speed());
                    drone.vel.set(tmp3);
                    drone.lookAt(target);
                } else {
                    drone.vel.set(Vec2.ZERO);
                    drone.lookAt(target);
                }
            } else {
                drone.vel.set(Vec2.ZERO);
            }
        });
    }

    private void applyDroneDamage(Unit drone, Player player) {
        if (drone.dead()) return;
        Tank tank = playerTanks.get(player.uuid());
        if (tank == null) return;

        int lastTick = droneLastHitTick.get(drone.id(), 0);
        if (globalTick - lastTick < 15) return;
        droneLastHitTick.put(drone.id(), globalTick);

        float dps = bulletDamage * tank.bonus.bulletDamage;
        float damage = dps * 0.25f;

        Unit enemy = Units.closestEnemy(drone.team(), drone.x, drone.y, drone.hitSize() + 3f, u -> !u.dead());
        if (enemy != null && enemy.getPlayer() != null) {
            Player ePlayer = enemy.getPlayer();
            Tank eTank = playerTanks.get(ePlayer.uuid());
            if (eTank != null) {
                eTank.health -= damage * (1f - (eTank.bonus.resistance - 1) * BASE_DAMAGE_RESISTANCE);
                if (eTank.health <= 0) {
                    eTank.health = 0;
                    enemy.kill();
                    tank.experience += eTank.experience / 2;
                }
            }
        }

        Shape targetShape = null;
        Tile targetTile = null;
        for (ObjectMap.Entry<Tile, Shape> entry : activeShapes) {
            Tile tile = entry.key;
            Shape shape = entry.value;
            float dx = drone.x - tile.worldx();
            float dy = drone.y - tile.worldy();
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            Block block = tile.build != null ? tile.build.block : null;
            if (block == null) continue;
            float radius = 0;
            if (block.name.equals("dp-square") || block.name.equals("dp-triagle")) radius = 8f;
            else if (block.name.equals("dp-pentagon")) radius = 16f;
            else if (block.name.equals("dp-alpha-pentagon")) radius = 60f;
            else continue;
            if (dist <= radius + drone.hitSize()) {
                targetShape = shape;
                targetTile = tile;
                break;
            }
        }

        if (targetShape != null && targetTile != null) {
            targetShape.health -= damage;
            Block block = targetTile.build.block;
            float offsetY = block.name.equals("dp-alpha-pentagon") ? 75f : 20f;


            if (targetShape.health <= 0) {
                targetTile.setNet(Blocks.air);
                activeShapes.remove(targetTile);
                tank.experience += targetShape.expReward;
            }
        }
    }

    // ================ HUD & EXPERIENCE ================
    private void updateHud() {
        for (Player p : Groups.player) {
            if (p == null || p.con == null) continue;
            Tank t = playerTanks.get(p.uuid());
            if (t == null || !t.showHud) continue;
            refreshTankStats(p, t);
            String unitName = (p.unit() != null && p.unit().type != null) ? p.unit().type.name : "";
            boolean canEvolve = !unitName.isEmpty() && canEvolve(t, unitName);
            RankSystem.Rank rank = RankSystem.getRank(t.rankPoints);
            String hud = "[grey]\uE80EX[gold]Core [grey]>[] [cyan]Diep.io [grey]Ranked[lightgrey]\n\n" +
                    "Health: " + (int)t.health + "/" + t.maxHealth + "\n" +
                    "Exp: " + t.experience + "\n" +
                    "Points: " + (t.points > 0 ? "[gold]" + t.points : t.points) + "[lightgrey]\n" +
                    "Level: " + t.level + "\n" +
                    "Upgrade: " + (canEvolve ? "[green]available\n" : "[red]not available\n") +
                    "[lightgrey]Rank: " + rank.emoji + " " + rank.name + "[]\n" +
                    "\n[gold]=====STATS=====\n" +
                    "[#ffbe94]Health regen: " + (t.bonus.healthRegen - 1) + "\n" +
                    "[#ff73ff]Max health: " + (t.bonus.maxHealth - 1) + "\n" +
                    "[#b073ff]Body damage: " + (t.bonus.bodyDamage - 1) + "\n" +
                    "[#ff4d4d]Bullet damage: " + (t.bonus.bulletDamage - 1) + "\n" +
                    "[#ff2bb3]Resistance: " + (t.bonus.resistance - 1) + "\n" +
                    "[#73ff4d]Drone count: " + (t.bonus.droneCount - 1) + "\n" +
                    "[#ffaa33]Drone health: " + (t.bonus.droneHealth - 1) + "\n" +
                    "[#33ccff]Sight range: " + (t.bonus.sightRange - 1);
            Call.infoPopup(p.con, hud, 0.25f, Align.topLeft, 150, 0, 0, 0);
        }
    }

    private void expToLevel() {
        for (Player p : Groups.player) {
            if (p == null) continue;
            Tank t = playerTanks.get(p.uuid());
            if (t == null) continue;
            if (t.level <= 0) t.level = 1;
            int next = t.level + 1;
            int required = 0;
            for (int i = 2; i <= next; i++) required += (i * EXP_MULT);
            while (t.experience >= required && required > 0 && next <= MAX_LEVEL) {
                t.level = next;

                if (t.level < 29) t.points++;
                else if (t.level > 28 && t.level % 3 == 0 && t.level <= 45) t.points++;
                else if (t.level > 45 && t.level % 5 == 0) t.points++;

                if (p.unit() != null) Call.effect(Fx.smokeCloud, p.unit().x, p.unit().y, 0, Color.acid);
                next = t.level + 1;
                required = 0;
                for (int i = 2; i <= next; i++) required += (i * EXP_MULT);
            }
        }
    }

    private void refreshTankStats(Player player, Tank tank) {
        if (player.unit() == null || player.unit().type == null) return;
        Unit unit = player.unit();
        if (tank.health < tank.maxHealth && !unit.dead()) {
            tank.health += healthRegen * tank.bonus.healthRegen * 0.25f;
        }
        int oldMax = tank.maxHealth;
        int evoLvl = 1;
        if (tank.level >= 15 && tank.level < 30) evoLvl = 2;
        else if (tank.level >= 30 && tank.level < 45) evoLvl = 3;
        else if (tank.level >= 45 && tank.level < 60) evoLvl = 4;
        else if (tank.level >= 60 && tank.level < 75) evoLvl = 5;
        else if (tank.level >= 75) evoLvl = 6;
        tank.maxHealth = maxHealth * tank.bonus.maxHealth * evoLvl;
        if (tank.maxHealth > oldMax) tank.health += (tank.maxHealth - oldMax);
        if (tank.health > tank.maxHealth) tank.health = tank.maxHealth;
        tank.bodyDamage = bodyDamage * tank.bonus.bodyDamage * 0.25f;
        applyBodyDamage(player, tank);
        unit.health = unit.maxHealth * (tank.health / tank.maxHealth);
    }

    private void applyBodyDamage(Player p, Tank tank) {
        if (p.unit() == null || p.unit().dead()) return;
        Tile tile = p.unit().tileOn();
        if (tile == null) return;
        float dps = bodyDamage * tank.bonus.bodyDamage;
        if (dps <= 0) return;
        float tick = dps * Time.delta;
        if (tick <= 0) return;

        // Check shape collisions
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Tile near = Vars.world.tile(tile.x + dx, tile.y + dy);
                if (near == null || near.build == null) continue;
                if (near.build.team == Team.crux) {
                    String name = near.build.block.name;
                    float radius = 0;
                    if (name.equals("dp-square") || name.equals("dp-triagle")) radius = 1f;
                    else if (name.equals("dp-pentagon")) radius = 2f;
                    else if (name.equals("dp-alpha-pentagon")) radius = 7.5f;
                    else continue;
                    Shape shape = findShape(near, radius);
                    if (shape != null) {
                        shape.health -= tick;
                        tank.targetTile = near;
                        Call.label(p.con, "hp: " + (int)shape.health, 0.5f, near.worldx(), near.worldy() + 20f);
                        tank.health -= 20f * Time.delta;
                        if (tank.health <= 0) { tank.health = 0; p.unit().kill(); }
                        if (shape.health <= 0) {
                            near.setNet(Blocks.air);
                            activeShapes.remove(near);
                            tank.experience += shape.expReward;
                        }
                        return;
                    }
                }
            }
        }

        // PvP body damage
        Unit enemyUnit = Units.closestEnemy(p.team(), p.unit().x, p.unit().y, PVP_RADIUS, u -> !u.dead() && u.getPlayer() != null);
        if (enemyUnit == null) return;

        Player enemyPlayer = enemyUnit.getPlayer();
        if (enemyPlayer == null) return;
        Tank enemyTank = playerTanks.get(enemyPlayer.uuid());
        if (enemyTank == null) return;
        if (enemyUnit.dead()) return;
        float dmgToEnemy = tick;
        float dmgToMe = (bodyDamage * enemyTank.bonus.bodyDamage) * Time.delta;

        enemyTank.health -= dmgToEnemy * (1f - (enemyTank.bonus.resistance - 1) * BASE_DAMAGE_RESISTANCE);
        tank.health -= dmgToMe;

        Call.label(p.con, "hp: " + (int)enemyTank.health, 0.5f, enemyUnit.x, enemyUnit.y + 20f);

        if (enemyTank.health <= 0) {
            enemyTank.health = 0;
            enemyUnit.kill();
            tank.experience += enemyTank.experience / 2;

            int killedLevel = enemyTank.level;
            int frags = 1;
            if (killedLevel >= 30 && killedLevel < 45) frags = 3;
            else if (killedLevel >= 15 && killedLevel < 30) frags = 2;

            tank.rankPoints += frags;
            String shooterUUID = p.uuid();
            playerRankPoints.put(shooterUUID, tank.rankPoints);
            saveRanks();

            p.sendMessage("[acid]+" + frags + " [stat]You've killed[white] " + enemyPlayer.coloredName() + " (body damage)");
        }

        if (tank.health <= 0) {
            tank.health = 0;
            p.unit().kill();
        }
    }

    private Shape findShape(Tile near, float radius) {
        for (ObjectMap.Entry<Tile, Shape> entry : activeShapes) {
            Tile center = entry.key;
            Shape shape = entry.value;
            if (Math.abs(near.x - center.x) <= radius && Math.abs(near.y - center.y) <= radius) {
                return shape;
            }
        }
        return null;
    }

    // ================ EVOLUTION ================
    private boolean canEvolve(Tank tank, String unitName) {
        if (tank.evolvedTier5) return false;

        int required = 15;
        if (tank.evolvedTier1 && !tank.evolvedTier2) required = 30;
        else if (tank.evolvedTier1 && tank.evolvedTier2 && !tank.evolvedTier3) required = 45;
        else if (tank.evolvedTier1 && tank.evolvedTier2 && tank.evolvedTier3 && !tank.evolvedTier4) required = 60;
        else if (tank.evolvedTier1 && tank.evolvedTier2 && tank.evolvedTier3 && tank.evolvedTier4 && !tank.evolvedTier5) required = 75;

        return tank.level >= required && evolutionMap.containsKey(unitName);
    }

    private void openEvolutionMenu(Player player) {
        Tank tank = playerTanks.get(player.uuid());
        if (tank == null || player.unit() == null) return;

        if (tank.evolvedTier5) {
            player.sendMessage("[scarlet]You have completed all evolutions![]");
            return;
        }

        int tier = 1, required = 15;
        if (tank.evolvedTier1 && !tank.evolvedTier2) {
            tier = 2;
            required = 30;
        } else if (tank.evolvedTier1 && tank.evolvedTier2 && !tank.evolvedTier3) {
            tier = 3;
            required = 45;
        } else if (tank.evolvedTier1 && tank.evolvedTier2 && tank.evolvedTier3 && !tank.evolvedTier4) {
            tier = 4;
            required = 60;
        } else if (tank.evolvedTier1 && tank.evolvedTier2 && tank.evolvedTier3 && tank.evolvedTier4 && !tank.evolvedTier5) {
            tier = 5;
            required = 75;
        }

        if (tank.level < required) {
            player.sendMessage("[scarlet]Next evolution available at level " + required + " (Current: " + tank.level + ")[]");
            return;
        }

        String current = player.unit().type.name;
        if (!evolutionMap.containsKey(current)) {
            player.sendMessage("[scarlet]No evolution branches for " + current + "[]");
            return;
        }

        String[] units = evolutionMap.get(current);
        String[] names = displayNames.get(current);
        if (units == null || units.length == 0) {
            player.sendMessage("[scarlet]Error: Empty evolution paths.[]");
            return;
        }

        String[][] options = new String[units.length + 1][1];
        for (int i = 0; i < units.length; i++) {
            options[i][0] = "[white]" + (names != null && names.length > i ? names[i] : units[i]);
        }
        options[units.length][0] = "[scarlet]Cancel";
        Call.menu(player.con, EVOLUTION_MENU, "[gold]Evolution Tier " + tier, "[lightgray]Choose your path (Level " + tank.level + "):[]", options);
    }

    // ================ SHAPES SPAWN ================
    private void spawnNormalShape() {
        int w = Vars.world.width(), h = Vars.world.height();
        if (w == 0 || h == 0 || activeShapes.size >= MAX_SHAPES) return;
        Tile tile = Vars.world.tile(random.nextInt(w), random.nextInt(h));
        if (tile == null || tile.floor().name.equals("shale")) return;
        Block block = normalBlocks.random();
        if (block == null) return;
        int radius = 0;
        if (block.name.equals("dp-square") || block.name.equals("dp-triagle")) radius = 1;
        else if (block.name.equals("dp-pentagon")) radius = 2;
        if (!isAreaFree(tile, radius)) return;
        tile.setNet(block, Team.crux, 0);
        activeShapes.put(tile, new Shape(block));
    }

    private void spawnAlphaShape() {
        int w = Vars.world.width(), h = Vars.world.height();
        if (w == 0 || h == 0) return;
        for (int attempt = 0; attempt < 50; attempt++) {
            if (activeShapes.size >= MAX_SHAPES) return;
            Tile tile = Vars.world.tile(random.nextInt(w), random.nextInt(h));
            if (tile == null || !tile.floor().name.equals("shale") || tile.block() != Blocks.air) continue;
            Block block = alphaBlocks.random();
            if (block == null) continue;
            int radius = 0;
            if (block.name.equals("dp-pentagon")) radius = 2;
            else if (block.name.equals("dp-alpha-pentagon")) radius = 8;
            if (!isAreaFree(tile, radius)) continue;
            tile.setNet(block, Team.crux, 0);
            activeShapes.put(tile, new Shape(block));
            return;
        }
    }

    private boolean isAreaFree(Tile center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                Tile t = Vars.world.tile(center.x + dx, center.y + dy);
                if (t == null || t.block() != Blocks.air) return false;
            }
        }
        return true;
    }

    private void clearNonPlayerUnits() {
        Groups.unit.each(u -> {
            if (u.team() == Team.crux) return;
            if (u.getPlayer() != null && u.getPlayer().isAdded()) return;
            if (u.type == droneUnit) return;
            u.kill();
        });
    }

    // ================ MENUS ================
    private void openUpgradeMenu(Player player) {
        Tank tank = playerTanks.get(player.uuid());
        if (tank == null) return;
        Call.menu(player.con, UPGRADE_MENU, "Points: [green]" + tank.points, "Upgrade your tank: ",
                new String[][]{
                        {"[#ffbe94]Health regen"},
                        {"[#ff73ff]Max health"},
                        {"[#b073ff]Body damage"},
                        {"[#ff4d4d]Bullet damage"},
                        {"[#ff2bb3]Resistance"},
                        {"[#73ff4d]Drone count"},
                        {"[#ffaa33]Drone health"},
                        {"[#33ccff]Sight range"},
                        {"[red]Close"}
                }
        );
    }

    // ================ EVENT HANDLERS ================
    private void onPlayerJoin(EventType.PlayerJoin event) {
        Player p = event.player;
        if (p == null) return;

        String uuid = p.uuid();
        Tank tank = playerTanks.get(uuid);
        if (tank == null) {
            tank = new Tank();
            playerTanks.put(uuid, tank);
        }
        tank.rankPoints = playerRankPoints.get(uuid, 0);
        Team spawnTeam = getAvailableTeam();
        if (spawnTeam == null) return;
        Vec2 pos = randomSpawnPoint();
        Unit unit = basicUnit.create(Team.sharded);
        unit.set(pos);
        unit.add();
        unit.controller(p);
        p.unit(unit);
        unit.team(spawnTeam);
        p.team(spawnTeam);
        int frags = tank.rankPoints;
        RankSystem.Rank rank = RankSystem.getRank(frags);
        String rankDisplay = rank.emoji + " [yellow]" + rank.name + "[]";
        String msg =
                "[cyan][grey]\uE80EX[gold]Core [grey]>[] [cyan]Diep.io [grey]Ranked[lightgrey]\n\n" +
                        "[lightgray]Player: [white]" + p.name + "\n" +
                        "[lightgray]Rank:   " + rankDisplay + "\n" +
                        "[lightgray]Frags:  [accent]" + frags + "\n\n" +
                        "[gold]/u  [lightgray]- Upgrades\n" +
                        "[gold]/t  [lightgray]- Evolution\n" +
                        "[gold]/s  [lightgray]- Respawn with 50% exp\n" +
                        "[gold]/rank  [lightgray]- Shows your current stats\n" +
                        "[gold]/ranks  [lightgray]- Shows all avaliable ranks\n" +
                        "[gold]/top  [lightgray]- Shows top 10 players\n" +
                        "[gold]/hud[lightgray]- Toggle/Remove HUD\n\n" +
                        "[green]★ Good luck and have fun! ★";

        Call.infoMessage(p.con, msg);
    }

    private void onBuildDestroy(EventType.BlockDestroyEvent event) {
        if (event.tile == null) return;
        Tile tile = event.tile;
        if (tile.build != null && tile.build.team == Team.crux) {
            String name = tile.build.block.name;
            if (name.equals("dp-square") || name.equals("dp-triagle") ||
                    name.equals("dp-pentagon") || name.equals("dp-alpha-pentagon")) {
                activeShapes.remove(tile);
            }
        }
    }

    private void onBuildDamage(EventType.BuildDamageEvent event) {
        if (event.build == null || event.build.team != Team.crux) return;
        Tile tile = event.build.tile;
        String name = event.build.block.name;
        float radius = 0;
        if (name.equals("dp-square") || name.equals("dp-triagle")) radius = 1;
        else if (name.equals("dp-pentagon")) radius = 2;
        else if (name.equals("dp-alpha-pentagon")) radius = 7.5f;
        else return;
        if (event.source == null || !(event.source.owner() instanceof Unit)) return;
        Unit killer = (Unit) event.source.owner();
        Player player = killer.getPlayer();
        if (player == null || player.con == null) return;
        Tank tank = playerTanks.get(player.uuid());
        if (tank == null) return;
        Shape shape = findShape(tile, radius);
        if (shape != null) {
            float mult = classDamageMult.get(player.unit().type.name, 1.0f);
            float damage = bulletDamage * tank.bonus.bulletDamage * mult;
            shape.health -= damage;
            float offsetY = name.equals("dp-alpha-pentagon") ? 75f : 20f;
            Call.label(player.con, "hp: " + (int)shape.health, 0.5f, event.build.x, event.build.y + offsetY);
            if (shape.health <= 0) {
                tile.setNet(Blocks.air);
                activeShapes.remove(tile);
                tank.experience += shape.expReward;
            }
        }
    }

    private void onMenuOption(EventType.MenuOptionChooseEvent event) {
        if (event.player == null) return;
        if (event.menuId == UPGRADE_MENU) {
            Tank tank = playerTanks.get(event.player.uuid());
            if (tank == null) return;
            int choice = event.option;
            if (choice == 8 || choice == -1) return;
            if (tank.points <= 0) { event.player.sendMessage("[scarlet]No points :/"); return; }
            switch (choice) {
                case 0: if (tank.bonus.healthRegen >= 8) return; tank.bonus.healthRegen++; break;
                case 1: if (tank.bonus.maxHealth >= 8) return; tank.bonus.maxHealth++; break;
                case 2: if (tank.bonus.bodyDamage >= 8) return; tank.bonus.bodyDamage++; break;
                case 3: if (tank.bonus.bulletDamage >= 8) return; tank.bonus.bulletDamage++; break;
                case 4: if (tank.bonus.resistance >= 8) return; tank.bonus.resistance++; break;
                case 5: if (tank.bonus.droneCount >= 8) return; tank.bonus.droneCount++; break;
                case 6: if (tank.bonus.droneHealth >= 8) return; tank.bonus.droneHealth++; break;
                case 7: if (tank.bonus.sightRange >= 8) return; tank.bonus.sightRange++; break;
            }
            tank.points--;
            if (tank.points > 0) openUpgradeMenu(event.player);
        } else if (event.menuId == EVOLUTION_MENU) {
            // Evolution selection
            Tank tank = playerTanks.get(event.player.uuid());
            if (tank == null || event.player.unit() == null) return;
            String current = event.player.unit().type.name;
            String[] units = evolutionMap.get(current);
            int choice = event.option;
            if (units == null || choice == -1 || choice >= units.length) return;
            String nextName = units[choice];
            UnitType nextUnit = Vars.content.units().find(u -> u.name.equals(nextName));
            if (nextUnit == null) return;
            if (!tank.evolvedTier1) {
                tank.evolvedTier1 = true;
            } else if (!tank.evolvedTier2) {
                tank.evolvedTier2 = true;
            } else if (!tank.evolvedTier3) {
                tank.evolvedTier3 = true;
            } else if (!tank.evolvedTier4) {
                tank.evolvedTier4 = true;
            } else if (!tank.evolvedTier5) {
                tank.evolvedTier5 = true;
            }
            Team oldTeam = event.player.team();
            float ox = event.player.x, oy = event.player.y;
            if (event.player.unit() != null) {
                event.player.unit().kill();
                event.player.unit(null);
            }
            Unit newUnit = nextUnit.create(Team.sharded);
            newUnit.set(ox, oy);
            newUnit.add();
            newUnit.controller(event.player);
            event.player.unit(newUnit);
            newUnit.team(oldTeam);
            event.player.team(oldTeam);
            if (evolutionMap.containsKey(nextName)) {
                boolean canNext = false;
                if (!tank.evolvedTier5) {
                    int nextRequired = 75;
                    if (!tank.evolvedTier4) nextRequired = 60;
                    else if (!tank.evolvedTier3) nextRequired = 45;
                    else if (!tank.evolvedTier2) nextRequired = 30;
                    else if (!tank.evolvedTier1) nextRequired = 15;
                    if (tank.level >= nextRequired) {
                        canNext = true;
                    }
                }
                if (canNext) {
                    openEvolutionMenu(event.player);
                }
            }
        }
    }

    private void onUnitDamage(EventType.UnitDamageEvent event) {
        if (event.unit == null || event.bullet == null) return;
        if (event.unit.dead()) return;
        Unit shooterUnit = (event.bullet.owner() instanceof Unit) ? (Unit) event.bullet.owner() : null;
        Player shooterPlayer = shooterUnit != null ? shooterUnit.getPlayer() : null;

        if (shooterUnit != null && activeBosses.containsKey(shooterUnit)) {
            Player hitPlayer = event.unit.getPlayer();
            if (hitPlayer != null) {
                Tank hitTank = playerTanks.get(hitPlayer.uuid());
                if (hitTank != null && !hitPlayer.unit().dead()) {
                    BossData bossData = activeBosses.get(shooterUnit);
                    Boss boss = bossData.boss;
                    float damage = bulletDamage * boss.bonus.bulletDamage * 0.25f;
                    hitTank.health -= damage * (1f - (hitTank.bonus.resistance - 1) * BASE_DAMAGE_RESISTANCE);
                    if (hitTank.health <= 0) {
                        hitTank.health = 0;
                        hitPlayer.unit().kill();
                    }
                }
            }
            return;
        }

        if (activeBosses.containsKey(event.unit)) {
            BossData bossData = activeBosses.get(event.unit);
            Boss boss = bossData.boss;
            if (shooterPlayer != null) {
                Tank shooterTank = playerTanks.get(shooterPlayer.uuid());
                if (shooterTank != null && !shooterPlayer.unit().dead()) {
                    float mult = classDamageMult.get(shooterUnit.type.name, 1.0f);
                    float damage = bulletDamage * shooterTank.bonus.bulletDamage * mult;
                    if (damage <= 0) damage = 1f;
                    boss.health -= damage;
                    if (boss.health <= 0) {
                        boss.health = 0;
                        event.unit.kill();
                        activeBosses.remove(event.unit);
                        bossSpawnTimer = 0f;
                        shooterTank.experience += boss.experience;
                        shooterTank.rankPoints += 5;
                        shooterPlayer.sendMessage("[acid]Boss " + boss.bossName + " defeated!");
                    }
                }
            }
            return;
        }

        // Drone damage from bullets
        if (event.unit.type == droneUnit) {
            int damage = 1;
            if (shooterPlayer != null) {
                Tank shooterTank = playerTanks.get(shooterPlayer.uuid());
                if (shooterTank != null) {
                    damage = (bulletDamage * shooterTank.bonus.bulletDamage == 0) ? bulletDamage : bulletDamage * shooterTank.bonus.bulletDamage;
                }
            }
            Integer hp = droneHealthMap.get(event.unit.id());
            if (hp != null) {
                int newHp = hp - damage;
                if (newHp <= 0) {
                    droneHealthMap.remove(event.unit.id());
                    event.unit.kill();
                } else {
                    droneHealthMap.put(event.unit.id(), newHp);
                }
            }
            return;
        }

        // Player damage
        event.unit.health = event.unit.maxHealth; // reset health (handled by tank stats)
        Player hitPlayer = event.unit.getPlayer();
        if (hitPlayer == null) return;
        Tank hitTank = playerTanks.get(hitPlayer.uuid());
        if (hitTank == null) return;
        if (hitPlayer.unit() == null || hitPlayer.unit().dead()) return;

        if (shooterPlayer != null && shooterPlayer != hitPlayer) {
            Tank shooterTank = playerTanks.get(shooterPlayer.uuid());
            if (shooterTank == null) return;
            float mult = classDamageMult.get(shooterUnit.type.name, 1.0f);
            int damage = (int)(bulletDamage * shooterTank.bonus.bulletDamage * mult);
            hitTank.health -= damage * (1f - (hitTank.bonus.resistance - 1) * BASE_DAMAGE_RESISTANCE);
            if (hitTank.health <= 0) {
                hitTank.health = 0;
                hitPlayer.unit().kill();
                shooterTank.experience += hitTank.experience / 2;
                hitTank.respExp = hitTank.experience / 2;
                int killedLevel = hitTank.level;
                int frags = 1;
                if (killedLevel >= 30 && killedLevel < 45) frags = 3;
                else if (killedLevel >= 15 && killedLevel < 30) frags = 2;
                else frags = 1;
                shooterTank.rankPoints += frags;
                String shooterUUID = shooterPlayer.uuid();
                playerRankPoints.put(shooterUUID, shooterTank.rankPoints);
                saveRanks();
                shooterPlayer.sendMessage("[acid]+" + frags + " [stat]You've killed[white] " + hitPlayer.coloredName());
            }
        }
    }

    private void onUnitDestroy(EventType.UnitDestroyEvent event) {
        if (event.unit == null || !event.unit.isPlayer()) return;
        if (activeBosses.containsKey(event.unit)) { activeBosses.remove(event.unit); return; }
        Player player = event.unit.getPlayer();
        if (player == null) return;
        Team team = player.team();
        Groups.unit.each(u -> {
            if (u.team() == team && u.type == droneUnit) {
                u.kill();
                droneHealthMap.remove(u.id());
            }
        });
    }

    private void onPlayerLeave(EventType.PlayerLeave event) {
        if (event.player == null) return;
        String uuid = event.player.uuid();
        if (playerTanks.containsKey(uuid)) {
            if (event.player.unit() != null && event.player.unit().isValid()) {
                event.player.unit().kill();
            }
            Team team = event.player.team();
            Groups.unit.each(u -> {
                if (u.team() == team && u.type == droneUnit) {
                    u.kill();
                    droneHealthMap.remove(u.id());
                }
            });
            playerTanks.remove(uuid);
        }
    }

    // ================ UTILITY ================
    private Team getAvailableTeam() {
        Seq<Team> available = new Seq<>();
        for (Team t : Team.all) if (t.id > 2 && !t.active()) available.add(t);
        return available.isEmpty() ? null : available.get(Mathf.random(available.size - 1));
    }

    private Vec2 randomSpawnPoint() {
        int w = Vars.world.width() * Vars.tilesize;
        int h = Vars.world.height() * Vars.tilesize;
        return new Vec2(random.nextFloat() * w, random.nextFloat() * h);
    }

    // ================ COMMANDS ================
    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("u", "Open upgrade menu", (args, player) -> {
            Tank tank = playerTanks.get(player.uuid());
            if (tank != null) openUpgradeMenu(player);
        });
        handler.<Player>register("t", "Open evolution menu", (args, player) -> {
            Tank tank = playerTanks.get(player.uuid());
            if (tank == null || player.unit() == null || !player.unit().isValid()) return;
            openEvolutionMenu(player);
        });
        handler.<Player>register("s", "Respawn your tank", (args, player) -> {
            if (player == null) return;
            if (player.unit() != null && player.unit().isValid()) player.unit().kill();
            Tank tank = playerTanks.get(player.uuid());
            if (tank == null) {
                tank = new Tank();
                playerTanks.put(player.uuid(), tank);
            }
            int maxRespawnXp = 0;
            for (int i = 2; i <= 37; i++) maxRespawnXp += (i * EXP_MULT);
            long halvedExp = tank.experience / 2;
            tank.respExp = (int) Math.min(halvedExp, maxRespawnXp);
            tank.evolvedTier1 = false;
            tank.evolvedTier2 = false;
            tank.evolvedTier3 = false;
            tank.bonus = new Tank.Bonus();
            tank.health = 150;
            tank.maxHealth = 150;
            tank.level = 1;
            tank.points = 0;
            tank.bodyDamage = 0;
            Team spawnTeam = getAvailableTeam();
            if (spawnTeam == null) return;
            Vec2 pos = randomSpawnPoint();
            Unit unit = basicUnit.create(Team.sharded);
            unit.set(pos);
            unit.add();
            unit.controller(player);
            player.unit(unit);
            unit.team(spawnTeam);
            player.team(spawnTeam);
            tank.experience = tank.respExp;
            refreshTankStats(player, tank);
        });
        handler.<Player>register("hud", "Toggle HUD", (args, player) -> {
            Tank tank = playerTanks.get(player.uuid());
            if (tank == null) return;
            tank.showHud = !tank.showHud;
            player.sendMessage("[stat]HUD " + (tank.showHud ? "[green]shown" : "[red]hidden"));
        });
        handler.<Player>register("ranks", "Show all ranks with requirements", (args, player) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("[gold]==== ALL RANKS ====\n");
            for (RankSystem.Rank rank : RankSystem.RANKS) {
                sb.append("[lightgray]").append(rank.emoji).append(" ").append("[white]").append(rank.name).append(" [lightgray]→ [accent]").append(rank.rankPointsRequired).append(" frags\n");
            }
            Call.infoMessage(player.con, sb.toString());
        });

        handler.<Player>register("rank", "Show your own rank and frags", (args, player) -> {
            String uuid = player.uuid();
            int points = playerRankPoints.get(uuid, 0);
            RankSystem.Rank rank = RankSystem.getRank(points);
            String msg = "[gold]==== YOUR STATS ====\n" +
                    "[lightgray]Nick: [white]" + player.coloredName() + "\n" +
                    "[lightgray]Rank: [white]" + rank.emoji + " " + rank.name + "\n" +
                    "[lightgray]Frags: [acid]" + points;
            Call.infoMessage(player.con, msg);
        });
    }

    // ================ RANKS ================
    private void loadRanks() {
        try {
            if (!rankFile.exists()) return;
            String content = rankFile.readString();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    String uuid = parts[0].trim();
                    int points = Integer.parseInt(parts[1].trim());
                    playerRankPoints.put(uuid, points);
                }
            }
            Log.info("Loaded " + playerRankPoints.size + " player ranks.");
        } catch (Throwable t) {
            Log.err("Failed to load ranks: " + t.getMessage());
        }
    }

    private void saveRanks() {
        try {
            StringBuilder sb = new StringBuilder();
            for (var entry : playerRankPoints.entries()) {
                sb.append(entry.key).append("=").append(entry.value).append("\n");
            }
            rankFile.writeString(sb.toString());
        } catch (Throwable t) {
            Log.err("Failed to save ranks: " + t.getMessage());
        }
    }

    private void updateLeaderboard() {
        Seq<Player> activePlayers = new Seq<>();
        for (Player p : Groups.player) {
            if (p != null && p.isAdded()) {
                Tank tank = playerTanks.get(p.uuid());
                if (tank != null) {
                    activePlayers.add(p);
                }
            }
        }

        activePlayers.sort((a, b) -> {
            Tank ta = playerTanks.get(a.uuid());
            Tank tb = playerTanks.get(b.uuid());
            return Integer.compare(tb.experience, ta.experience);
        });

        StringBuilder sb = new StringBuilder();
        sb.append("[gold]-----Leaderboard-----\n");
        int limit = Math.min(5, activePlayers.size);
        for (int i = 0; i < limit; i++) {
            Player p = activePlayers.get(i);
            Tank tank = playerTanks.get(p.uuid());
            RankSystem.Rank rank = RankSystem.getRank(tank.rankPoints);
            sb.append("[lightgray]")
                    .append(i + 1)
                    .append(". ")
                    .append("[white]")
                    .append(rank.emoji)
                    .append(" ")
                    .append(rank.name)
                    .append(" || ")
                    .append(p.name)
                    .append(" ")
                    .append("[accent]")
                    .append(tank.experience)
                    .append("\n");
        }

        String text = sb.toString();
        for (Player p : Groups.player) {
            if (p == null || p.con == null) continue;
            Call.infoPopup(p.con, text, 0.5f, Align.topRight, 0, 0, 0, 150);
        }
    }
}