package ru.phantom.config;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Полный набор настраиваемых параметров одного фейкового игрока.
 * Всё, что можно менять через GUI, живёт здесь.
 */
public class BotSettings {

    /** Режим поведения ИИ. */
    public enum AiMode {
        IDLE("Стоять", "Бот просто стоит на месте"),
        FOLLOW("Следовать", "Бот идёт за владельцем"),
        GUARD("Охрана", "Атакует врагов рядом с точкой охраны"),
        HUNT("Охота", "Активно ищет и атакует мобов"),
        WANDER("Бродить", "Свободно гуляет по округе"),
        MIRROR("Зеркало", "Повторяет движения владельца");

        private final String display;
        private final String description;

        AiMode(String display, String description) {
            this.display = display;
            this.description = description;
        }

        public String display() {
            return display;
        }

        public String description() {
            return description;
        }

        public AiMode next() {
            AiMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /** Кого бот считает врагом. */
    public enum TargetPolicy {
        NONE("Никого", "Бот не атакует"),
        MONSTERS("Монстров", "Только враждебных мобов"),
        ANIMALS("Животных", "Только пассивных мобов"),
        PLAYERS("Игроков", "Только игроков"),
        ALL("Всех", "Любую живую цель");

        private final String display;
        private final String description;

        TargetPolicy(String display, String description) {
            this.display = display;
            this.description = description;
        }

        public String display() {
            return display;
        }

        public String description() {
            return description;
        }

        public TargetPolicy next() {
            TargetPolicy[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    // --- Идентичность ---
    private String displayName;
    private String skinOwner;
    private String skinValue;
    private String skinSignature;

    // --- Отображение ---
    private boolean tabVisible = true;
    private boolean collidable = true;
    private boolean nameTagVisible = true;
    private boolean glowing = false;

    // --- Игровые параметры ---
    private GameMode gameMode = GameMode.SURVIVAL;
    private double maxHealth = 20.0;
    private boolean invulnerable = false;
    private boolean dropInventoryOnDeath = true;
    private boolean autoRespawn = true;
    private int autoRespawnDelay = 100;
    private float walkSpeed = 0.2f;

    // --- ИИ ---
    private AiMode aiMode = AiMode.IDLE;
    private TargetPolicy targetPolicy = TargetPolicy.MONSTERS;
    private boolean lookAtNearest = true;
    private double followDistance = 3.0;
    private double attackRadius = 12.0;
    private int attackCooldown = 12;

    // --- Реализм ---
    private boolean idleHeadMovement = true;
    private boolean idleSwing = false;
    private boolean takeFireDamage = true;
    private boolean drowning = true;
    private boolean hunger = false;
    private boolean chatEnabled = true;
    private boolean sneaking = false;

    public BotSettings(String displayName) {
        this.displayName = displayName;
        this.skinOwner = displayName;
    }

    /** Загружает значения по умолчанию из config.yml. */
    public void applyDefaults(ConfigurationSection defaults, ConfigurationSection ai, ConfigurationSection realism) {
        if (defaults != null) {
            tabVisible = defaults.getBoolean("tab-visible", tabVisible);
            try {
                gameMode = GameMode.valueOf(defaults.getString("gamemode", "SURVIVAL").toUpperCase());
            } catch (IllegalArgumentException ignored) {
                gameMode = GameMode.SURVIVAL;
            }
            maxHealth = defaults.getDouble("max-health", maxHealth);
            invulnerable = defaults.getBoolean("invulnerable", invulnerable);
            dropInventoryOnDeath = defaults.getBoolean("drop-inventory-on-death", dropInventoryOnDeath);
            autoRespawn = defaults.getBoolean("auto-respawn", autoRespawn);
            autoRespawnDelay = defaults.getInt("auto-respawn-delay", autoRespawnDelay);
            walkSpeed = (float) defaults.getDouble("walk-speed", walkSpeed);
        }
        if (ai != null) {
            followDistance = ai.getDouble("follow-distance", followDistance);
            attackRadius = ai.getDouble("attack-radius", attackRadius);
            attackCooldown = ai.getInt("attack-cooldown", attackCooldown);
        }
        if (realism != null) {
            idleHeadMovement = realism.getBoolean("idle-head-movement", idleHeadMovement);
            idleSwing = realism.getBoolean("idle-swing", idleSwing);
            takeFireDamage = realism.getBoolean("fire-damage", takeFireDamage);
            drowning = realism.getBoolean("drowning", drowning);
            hunger = realism.getBoolean("hunger", hunger);
            chatEnabled = realism.getBoolean("chat-enabled", chatEnabled);
        }
    }

    // ------------------------------------------------------------------
    // Геттеры / сеттеры
    // ------------------------------------------------------------------

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSkinOwner() {
        return skinOwner;
    }

    public void setSkinOwner(String skinOwner) {
        this.skinOwner = skinOwner;
    }

    public String getSkinValue() {
        return skinValue;
    }

    public String getSkinSignature() {
        return skinSignature;
    }

    public void setSkin(String value, String signature) {
        this.skinValue = value;
        this.skinSignature = signature;
    }

    public boolean isTabVisible() {
        return tabVisible;
    }

    public void setTabVisible(boolean tabVisible) {
        this.tabVisible = tabVisible;
    }

    public boolean isCollidable() {
        return collidable;
    }

    public void setCollidable(boolean collidable) {
        this.collidable = collidable;
    }

    public boolean isNameTagVisible() {
        return nameTagVisible;
    }

    public void setNameTagVisible(boolean nameTagVisible) {
        this.nameTagVisible = nameTagVisible;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = Math.max(1.0, Math.min(1024.0, maxHealth));
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public boolean isDropInventoryOnDeath() {
        return dropInventoryOnDeath;
    }

    public void setDropInventoryOnDeath(boolean dropInventoryOnDeath) {
        this.dropInventoryOnDeath = dropInventoryOnDeath;
    }

    public boolean isAutoRespawn() {
        return autoRespawn;
    }

    public void setAutoRespawn(boolean autoRespawn) {
        this.autoRespawn = autoRespawn;
    }

    public int getAutoRespawnDelay() {
        return autoRespawnDelay;
    }

    public void setAutoRespawnDelay(int autoRespawnDelay) {
        this.autoRespawnDelay = Math.max(0, autoRespawnDelay);
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = Math.max(0f, Math.min(1f, walkSpeed));
    }

    public AiMode getAiMode() {
        return aiMode;
    }

    public void setAiMode(AiMode aiMode) {
        this.aiMode = aiMode;
    }

    public TargetPolicy getTargetPolicy() {
        return targetPolicy;
    }

    public void setTargetPolicy(TargetPolicy targetPolicy) {
        this.targetPolicy = targetPolicy;
    }

    public boolean isLookAtNearest() {
        return lookAtNearest;
    }

    public void setLookAtNearest(boolean lookAtNearest) {
        this.lookAtNearest = lookAtNearest;
    }

    public double getFollowDistance() {
        return followDistance;
    }

    public void setFollowDistance(double followDistance) {
        this.followDistance = Math.max(1.0, Math.min(64.0, followDistance));
    }

    public double getAttackRadius() {
        return attackRadius;
    }

    public void setAttackRadius(double attackRadius) {
        this.attackRadius = Math.max(1.0, Math.min(64.0, attackRadius));
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(int attackCooldown) {
        this.attackCooldown = Math.max(1, Math.min(200, attackCooldown));
    }

    public boolean isIdleHeadMovement() {
        return idleHeadMovement;
    }

    public void setIdleHeadMovement(boolean idleHeadMovement) {
        this.idleHeadMovement = idleHeadMovement;
    }

    public boolean isIdleSwing() {
        return idleSwing;
    }

    public void setIdleSwing(boolean idleSwing) {
        this.idleSwing = idleSwing;
    }

    public boolean isTakeFireDamage() {
        return takeFireDamage;
    }

    public void setTakeFireDamage(boolean takeFireDamage) {
        this.takeFireDamage = takeFireDamage;
    }

    public boolean isDrowning() {
        return drowning;
    }

    public void setDrowning(boolean drowning) {
        this.drowning = drowning;
    }

    public boolean isHunger() {
        return hunger;
    }

    public void setHunger(boolean hunger) {
        this.hunger = hunger;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public void setChatEnabled(boolean chatEnabled) {
        this.chatEnabled = chatEnabled;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }
}
