package ru.phantom.ai;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.phantom.PhantomPlugin;
import ru.phantom.config.BotSettings;
import ru.phantom.core.PhantomBot;

import java.util.concurrent.ThreadLocalRandom;

/** Простой, но живой ИИ фейкового игрока. */
public class BotAI {

    private final PhantomPlugin plugin;

    public BotAI(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    /** Один тик логики для одного бота. */
    public void tick(PhantomBot bot) {
        if (!bot.isSpawned() || bot.isPossessed()) {
            return;
        }

        Player self = bot.getBukkitPlayer();
        if (self == null || self.isDead()) {
            return;
        }

        if (bot.getAttackTicks() > 0) {
            bot.setAttackTicks(bot.getAttackTicks() - 1);
        }

        BotSettings settings = bot.getSettings();
        switch (settings.getAiMode()) {
            case IDLE -> tickIdle(bot, self);
            case FOLLOW -> tickFollow(bot, self);
            case GUARD -> tickGuard(bot, self);
            case HUNT -> tickHunt(bot, self);
            case WANDER -> tickWander(bot, self);
            case MIRROR -> tickMirror(bot, self);
        }
    }

    // ------------------------------------------------------------------
    // Режимы
    // ------------------------------------------------------------------

    private void tickIdle(PhantomBot bot, Player self) {
        BotSettings settings = bot.getSettings();
        if (settings.isLookAtNearest()) {
            LivingEntity nearest = findNearestPlayer(self, 10);
            if (nearest != null) {
                bot.lookAt(nearest.getEyeLocation());
                return;
            }
        }
        // Лёгкое «дыхание» — бот выглядит живым, а не манекеном.
        if (settings.isIdleHeadMovement() && ThreadLocalRandom.current().nextInt(60) == 0) {
            float yaw = self.getLocation().getYaw() + ThreadLocalRandom.current().nextInt(-35, 36);
            float pitch = ThreadLocalRandom.current().nextInt(-12, 13);
            bot.setRotation(yaw, pitch);
        }
        if (settings.isIdleSwing() && ThreadLocalRandom.current().nextInt(200) == 0) {
            bot.swingHand();
        }
    }

    private void tickFollow(PhantomBot bot, Player self) {
        Player owner = bot.getOwnerUuid() == null ? null : Bukkit.getPlayer(bot.getOwnerUuid());
        if (owner == null || !owner.isOnline()) {
            tickIdle(bot, self);
            return;
        }
        moveToward(bot, self, owner.getLocation(), bot.getSettings().getFollowDistance());
        attackIfHostileNear(bot, self);
    }

    private void tickGuard(PhantomBot bot, Player self) {
        Location guard = bot.getGuardPoint();
        if (guard == null) {
            bot.setGuardPoint(self.getLocation().clone());
            guard = bot.getGuardPoint();
        }
        LivingEntity target = findTarget(bot, self);
        if (target != null) {
            engage(bot, self, target);
            return;
        }
        // Врагов нет — возвращаемся на пост.
        if (self.getLocation().distanceSquared(guard) > 9) {
            moveToward(bot, self, guard, 1.5);
        } else {
            tickIdle(bot, self);
        }
    }

    private void tickHunt(PhantomBot bot, Player self) {
        LivingEntity target = findTarget(bot, self);
        if (target != null) {
            engage(bot, self, target);
        } else {
            tickWander(bot, self);
        }
    }

    private void tickWander(PhantomBot bot, Player self) {
        Location target = bot.getWanderTarget();

        // Цель достигнута или ещё не выбрана — выбираем новую.
        boolean needNew = target == null
                || !target.getWorld().equals(self.getWorld())
                || self.getLocation().distanceSquared(target) < 2.25;

        if (needNew) {
            if (ThreadLocalRandom.current().nextInt(30) != 0) {
                return;
            }
            target = self.getLocation().clone().add(
                    ThreadLocalRandom.current().nextInt(-10, 11),
                    0,
                    ThreadLocalRandom.current().nextInt(-10, 11));
            bot.setWanderTarget(target);
        }
        moveToward(bot, self, target, 1.0);
    }

    private void tickMirror(PhantomBot bot, Player self) {
        Player owner = bot.getOwnerUuid() == null ? null : Bukkit.getPlayer(bot.getOwnerUuid());
        if (owner == null || !owner.isOnline()) {
            tickIdle(bot, self);
            return;
        }
        // Бот копирует поворот и приседание владельца.
        bot.setRotation(owner.getLocation().getYaw(), owner.getLocation().getPitch());
        if (self.isSneaking() != owner.isSneaking()) {
            self.setSneaking(owner.isSneaking());
            bot.getSettings().setSneaking(owner.isSneaking());
        }
        // Держимся рядом с владельцем, повторяя его перемещение.
        if (self.getLocation().distance(owner.getLocation()) > 2.0) {
            moveToward(bot, self, owner.getLocation(), 2.0);
        }
    }

    // ------------------------------------------------------------------
    // Помощники
    // ------------------------------------------------------------------

    /** Двигает бота к точке, если он дальше, чем stopDistance. */
    private void moveToward(PhantomBot bot, Player self, Location target, double stopDistance) {
        if (!self.getWorld().equals(target.getWorld())) {
            bot.teleport(target);
            return;
        }
        Location from = self.getLocation();
        double distance = from.distance(target);

        double maxDistance = plugin.getConfig().getDouble("ai.follow-teleport-distance", 40.0);
        if (distance > maxDistance) {
            bot.teleport(target);
            return;
        }
        if (distance <= stopDistance) {
            return;
        }

        bot.lookAt(target.clone().add(0, 1.6, 0));

        // Шаг за тик: скорость игрока (0.2) примерно равна 0.2 блока за тик.
        double speed = bot.getSettings().getWalkSpeed() * plugin.getConfig()
                .getDouble("ai.speed-multiplier", 1.0);
        speed = Math.min(speed, distance);

        Vector direction = target.toVector().subtract(from.toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }
        Vector motion = direction.normalize().multiply(speed);

        boolean moved = bot.move(motion);

        // Упёрлись в препятствие — пробуем перепрыгнуть.
        if (!moved && bot.isOnGround()) {
            bot.jump();
            bot.move(motion);
        }
    }

    /** Подходит к цели и бьёт её. */
    private void engage(PhantomBot bot, Player self, LivingEntity target) {
        double reach = plugin.getConfig().getDouble("ai.attack-reach", 3.2);
        bot.lookAt(target.getEyeLocation());

        double distance = self.getLocation().distance(target.getLocation());
        if (distance > reach) {
            moveToward(bot, self, target.getLocation(), reach - 0.5);
            return;
        }
        if (bot.getAttackTicks() <= 0) {
            bot.attack(target);
            bot.setAttackTicks(bot.getSettings().getAttackCooldown());
        }
    }

    /** Атакует враждебного моба рядом, не сходя с маршрута. */
    private void attackIfHostileNear(PhantomBot bot, Player self) {
        if (bot.getSettings().getTargetPolicy() == BotSettings.TargetPolicy.NONE) {
            return;
        }
        LivingEntity target = findTarget(bot, self);
        if (target == null) {
            return;
        }
        double reach = plugin.getConfig().getDouble("ai.attack-reach", 3.2);
        if (self.getLocation().distance(target.getLocation()) <= reach && bot.getAttackTicks() <= 0) {
            bot.attack(target);
            bot.setAttackTicks(bot.getSettings().getAttackCooldown());
        }
    }

    /** Ищет ближайшую подходящую цель по политике бота. */
    private LivingEntity findTarget(PhantomBot bot, Player self) {
        BotSettings.TargetPolicy policy = bot.getSettings().getTargetPolicy();
        if (policy == BotSettings.TargetPolicy.NONE) {
            return null;
        }
        double radius = bot.getSettings().getAttackRadius();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : self.getNearbyEntities(radius, radius / 2, radius)) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (living.equals(self) || !matchesPolicy(living, policy)) {
                continue;
            }
            // Не бьём других ботов того же владельца и самого владельца.
            if (living instanceof Player other) {
                if (other.getUniqueId().equals(bot.getOwnerUuid())) {
                    continue;
                }
                var otherBot = plugin.getBotManager().fromPlayer(other);
                if (otherBot != null && otherBot.getOwnerUuid() != null
                        && otherBot.getOwnerUuid().equals(bot.getOwnerUuid())) {
                    continue;
                }
            }
            double distance = self.getLocation().distanceSquared(living.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private boolean matchesPolicy(LivingEntity entity, BotSettings.TargetPolicy policy) {
        return switch (policy) {
            case NONE -> false;
            case MONSTERS -> entity instanceof Monster;
            case ANIMALS -> entity instanceof Animals;
            case PLAYERS -> entity instanceof Player;
            case ALL -> true;
        };
    }

    private LivingEntity findNearestPlayer(Player self, double radius) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : self.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player other && !other.equals(self)) {
                double distance = self.getLocation().distanceSquared(other.getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = other;
                }
            }
        }
        return best;
    }
}
