package diepio.plugin;

import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;

public class Tank {
    public Player player;

    public final float BASE_HEALTH_REGEN = 5f;
    public final int BASE_MAX_HEALTH = 250;
    public final int BASE_BULLET_DAMAGE = 5;
    public final int BASE_RESISTANCE = 4; // %
    public final int BASE_DRONE_HEALTH = 50;

    /** tank health */
    public float health;
    /** tank max health */
    public int maxHealth = BASE_MAX_HEALTH;
    /** tank points earned per level */
    public int points;
    /** tank experience */
    public int experience;
    /** tank level */
    public int level;
    /** experience saved for respawn */
    public int respawnExperience;
    /** show player info & stats? */
    public boolean showHud;
    /** show leaderboard? */
    public boolean showLeaderboard;
    /** evolution flag */
    private boolean evolutionTier;
    /** player frags earned for kills */
    private int frags;

    public Bonus bonus = new Bonus();

    public static class Bonus {
        public int healthRegen = 1;
        public int maxHealth = 1;
        public int bulletDamage = 1;
        public int resistance = 0;
        public int reload = 1;
        public int droneHealth = 1;
        public int movementSpeed = 1;
    }

    public Tank() {
    }


    public void regenerateHealth() {
        while (this.health < this.maxHealth) {
            this.health += BASE_HEALTH_REGEN * this.bonus.healthRegen * Time.delta;
        }

        if (this.health > this.maxHealth) this.health = this.maxHealth;
    }

    public void update() {
        regenerateHealth();
        updateTankReloadEffect();
        updateTankMovementEffect();
    }

    public void updateTankMovementEffect() {
        if (player == null || player.unit() == null) return;

        Unit unit = player.unit();

        if (bonus.movementSpeed <= 1) return;

        String effectName = "dp-speed-boost-" + Math.min(bonus.movementSpeed, 8);
        StatusEffect effect = Vars.content.getByName(ContentType.status, effectName);

        if (effect != null) unit.apply(effect, 1f);
    }

    public void updateTankReloadEffect() {
        if (player == null || player.unit() == null) return;

        Unit unit = player.unit();

        if (bonus.reload <= 1) return;

        String effectName = "dp-reload-boost-" + Math.min(bonus.reload, 8);
        StatusEffect effect = Vars.content.getByName(ContentType.status, effectName);

        if (effect != null) unit.apply(effect, 1f);

    }

    public void takeDamage(Tank attacker) {
        if (attacker == null) return;
        float a = attacker.bonus.bulletDamage * BASE_BULLET_DAMAGE;
        float b = this.bonus.resistance / 100f;
        float c = a - (a * b);

        if (this.health - c < 0) {
            this.health = 0;
        } else {
            this.health -= c;
        }
    }

    public boolean isAlive() {
        return this.health > 0 ? true : false;
    }
}