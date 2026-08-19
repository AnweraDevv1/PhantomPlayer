package ru.phantom.core;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import ru.phantom.config.BotSettings;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Фейковый игрок на основе настоящего NMS {@link ServerPlayer}.
 * <p>
 * Такой бот регистрируется в PlayerList сервера, а значит:
 * получает урон, ест, ломает блоки, виден в TAB, обрабатывается плагинами
 * и античитами как обычный игрок.
 */
public class PhantomBot {

    private final UUID uuid;
    private final String name;
    private final BotSettings settings;
    private final UUID ownerUuid;

    private ServerPlayer handle;
    private boolean spawned;

    // Состояние ИИ
    private UUID aiTargetUuid;
    private Location guardPoint;
    private Location wanderTarget;
    private int attackTicks;
    private int deathTicks = -1;

    // Кто сейчас управляет ботом (режим вселения)
    private UUID possessedBy;

    /** Технический переспавн (смена скина): не шуметь в чат сообщениями входа/выхода. */
    private boolean silentRespawn;

    /** Вертикальная скорость для ручной физики (у бота нет клиента). */
    private double verticalVelocity;
    private boolean onGround = true;

    public PhantomBot(UUID uuid, String name, UUID ownerUuid, BotSettings settings) {
        this.uuid = uuid;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.settings = settings;
    }

    // ------------------------------------------------------------------
    // Спавн / удаление
    // ------------------------------------------------------------------

    /**
     * Создаёт NMS-игрока и добавляет его в мир.
     *
     * @return true, если бот успешно заспавнен
     */
    public boolean spawn(Location location) {
        if (spawned) {
            return false;
        }
        MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        GameProfile profile = buildProfile();

        handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        handle.setPos(location.getX(), location.getY(), location.getZ());
        handle.setYRot(location.getYaw());
        handle.setXRot(location.getPitch());
        handle.setYHeadRot(location.getYaw());

        // Фейковое соединение: без него сервер не примет игрока.
        EmptyConnection connection = new EmptyConnection();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        handle.connection = new ServerGamePacketListenerImpl(server, connection, handle, cookie);

        // Регистрируем в списке игроков — это делает бота «настоящим».
        server.getPlayerList().placeNewPlayer(connection, handle, cookie);

        spawned = true;
        applySettings();
        return true;
    }

    /**
     * Собирает профиль бота вместе со скином.
     * <p>
     * В 1.21.11 {@code GameProfile} — record, а {@code properties()} у профиля,
     * созданного конструктором с двумя аргументами, неизменяем. Поэтому карту
     * свойств строим сами и передаём в конструктор.
     */
    private GameProfile buildProfile() {
        // PropertyMap в 1.21.11 оборачивает Multimap и не имеет пустого конструктора.
        com.google.common.collect.Multimap<String, Property> backing =
                com.google.common.collect.ArrayListMultimap.create();
        String value = settings.getSkinValue();
        if (value != null) {
            backing.put("textures", new Property("textures", value, settings.getSkinSignature()));
        }
        return new GameProfile(uuid, name, new PropertyMap(backing));
    }

    /** Полностью убирает бота с сервера. */
    public void despawn() {
        if (!spawned || handle == null) {
            return;
        }
        try {
            MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
            server.getPlayerList().remove(handle);
        } catch (Throwable ignored) {
            // на всякий случай — принудительно
            try {
                handle.discard();
            } catch (Throwable ignored2) {
            }
        }
        spawned = false;
        handle = null;
    }

    // ------------------------------------------------------------------
    // Применение настроек
    // ------------------------------------------------------------------

    /** Применяет все настройки к живому боту. */
    public void applySettings() {
        Player bukkit = getBukkitPlayer();
        if (bukkit == null) {
            return;
        }

        bukkit.setGameMode(settings.getGameMode());
        bukkit.setInvulnerable(settings.isInvulnerable());
        bukkit.setCollidable(settings.isCollidable());
        bukkit.setGlowing(settings.isGlowing());
        bukkit.setWalkSpeed(settings.getWalkSpeed());
        bukkit.setSneaking(settings.isSneaking());

        var attr = bukkit.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(settings.getMaxHealth());
            if (bukkit.getHealth() > settings.getMaxHealth()) {
                bukkit.setHealth(settings.getMaxHealth());
            }
        }

        updateTabVisibility();
    }

    /** Скрывает или показывает бота в списке игроков (TAB). */
    public void updateTabVisibility() {
        if (handle == null) {
            return;
        }
        MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == handle) {
                continue;
            }
            if (settings.isTabVisible()) {
                viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, handle));
                viewer.connection.send(new ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, handle));
            } else {
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(uuid)));
            }
        }
    }

    /**
     * Меняет скин на лету. Требует переспавна сущности для всех наблюдателей,
     * поэтому просто пересоздаём бота на том же месте.
     */
    public void refreshSkin() {
        if (!spawned || handle == null) {
            return;
        }
        Location loc = getLocation();
        var inventoryCopy = getBukkitPlayer() != null
                ? getBukkitPlayer().getInventory().getContents().clone()
                : null;
        silentRespawn = true;
        despawn();
        spawn(loc);
        silentRespawn = false;
        if (inventoryCopy != null && getBukkitPlayer() != null) {
            getBukkitPlayer().getInventory().setContents(inventoryCopy);
        }
    }

    // ------------------------------------------------------------------
    // Действия
    // ------------------------------------------------------------------

    /** Взмах рукой — видно всем наблюдателям. */
    public void swingHand() {
        if (handle == null) {
            return;
        }
        handle.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        broadcast(new ClientboundAnimatePacket(handle, ClientboundAnimatePacket.SWING_MAIN_HAND));
    }

    /** Поворачивает голову бота в сторону точки. */
    public void lookAt(Location target) {
        if (handle == null) {
            return;
        }
        Location self = getLocation();
        double dx = target.getX() - self.getX();
        double dy = target.getY() - (self.getY() + 1.62);
        double dz = target.getZ() - self.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horiz));

        setRotation(yaw, pitch);
    }

    /** Устанавливает поворот и рассылает его наблюдателям. */
    public void setRotation(float yaw, float pitch) {
        if (handle == null) {
            return;
        }
        handle.setYRot(yaw);
        handle.setXRot(pitch);
        handle.setYHeadRot(yaw);
        broadcast(new ClientboundRotateHeadPacket(handle, (byte) (yaw * 256.0F / 360.0F)));
    }

    /** Телепорт бота. */
    public void teleport(Location location) {
        Player bukkit = getBukkitPlayer();
        if (bukkit != null) {
            bukkit.teleport(location);
            verticalVelocity = 0;
            onGround = true;
        }
    }

    /**
     * Двигает бота на заданное горизонтальное смещение с учётом гравитации.
     * <p>
     * Важно: {@code setVelocity} для бота бесполезен — сервер лишь отправил бы
     * пакет несуществующему клиенту. Поэтому позицию считаем и выставляем сами.
     *
     * @return true, если бот реально сдвинулся
     */
    public boolean move(Vector motion) {
        if (handle == null) {
            return false;
        }
        Location from = getLocation();
        if (from == null) {
            return false;
        }
        Physics.Step step = Physics.step(from, motion, verticalVelocity);
        verticalVelocity = step.verticalVelocity();
        onGround = step.onGround();

        Location to = step.location();
        boolean moved = to.getX() != from.getX() || to.getY() != from.getY() || to.getZ() != from.getZ();

        // absSnapTo двигает сущность серверно и корректно обновляет трекер.
        handle.absSnapTo(to.getX(), to.getY(), to.getZ(), from.getYaw(), from.getPitch());
        handle.setOnGround(step.onGround());
        return moved;
    }

    /** Прыжок (работает только с земли). */
    public void jump() {
        if (onGround) {
            verticalVelocity = 0.42;
            onGround = false;
        }
    }

    /** Мгновенно ставит бота в позицию без пересчёта физики. */
    public void snapTo(Location location) {
        if (handle == null) {
            return;
        }
        handle.absSnapTo(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        handle.setYHeadRot(location.getYaw());
        verticalVelocity = 0;
    }

    /** Применяет гравитацию, когда бот стоит на месте. */
    public void tickPhysics() {
        if (handle == null || isPossessed()) {
            return;
        }
        if (!onGround || verticalVelocity != 0) {
            move(new Vector(0, 0, 0));
        }
    }

    public boolean isOnGround() {
        return onGround;
    }

    /** Атакует сущность по-настоящему (с учётом оружия и зачарований). */
    public void attack(org.bukkit.entity.Entity target) {
        if (handle == null) {
            return;
        }
        net.minecraft.world.entity.Entity nmsTarget =
                ((org.bukkit.craftbukkit.entity.CraftEntity) target).getHandle();
        handle.attack(nmsTarget);
        handle.resetAttackStrengthTicker();
        swingHand();
    }

    /** Отправляет сообщение в чат от имени бота. */
    public void chat(String message) {
        Player bukkit = getBukkitPlayer();
        if (bukkit != null && settings.isChatEnabled()) {
            bukkit.chat(message);
        }
    }

    /**
     * Зеркалит позицию, поворот и позу игрока на тело бота.
     * Используется режимом вселения — вызывается каждый тик.
     */
    public void mirror(Player source) {
        if (handle == null) {
            return;
        }
        Location loc = source.getLocation();
        handle.absSnapTo(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        handle.setYHeadRot(loc.getYaw());
        handle.setOnGround(source.isOnGround());

        broadcast(new ClientboundRotateHeadPacket(handle, (byte) (loc.getYaw() * 256.0F / 360.0F)));

        Player self = getBukkitPlayer();
        if (self != null) {
            if (self.isSneaking() != source.isSneaking()) {
                self.setSneaking(source.isSneaking());
            }
            if (self.isSprinting() != source.isSprinting()) {
                self.setSprinting(source.isSprinting());
            }
        }
    }

    /** Копирует экипировку игрока боту, чтобы окружающие видели те же вещи. */
    public void syncEquipment(Player source, int heldSlot) {
        Player self = getBukkitPlayer();
        if (self == null) {
            return;
        }
        self.getInventory().setContents(source.getInventory().getContents());
        self.getInventory().setArmorContents(source.getInventory().getArmorContents());
        self.getInventory().setHeldItemSlot(heldSlot);
    }

    /** Обновляет метаданные сущности (поза, сникинг и т. д.) для наблюдателей. */
    public void refreshMetadata() {
        if (handle == null) {
            return;
        }
        var data = handle.getEntityData().getNonDefaultValues();
        if (data != null) {
            broadcast(new ClientboundSetEntityDataPacket(handle.getId(), data));
        }
    }

    private void broadcast(net.minecraft.network.protocol.Packet<?> packet) {
        MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer != handle) {
                viewer.connection.send(packet);
            }
        }
    }

    // ------------------------------------------------------------------
    // Геттеры
    // ------------------------------------------------------------------

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public BotSettings getSettings() {
        return settings;
    }

    public ServerPlayer getHandle() {
        return handle;
    }

    public boolean isSpawned() {
        return spawned && handle != null && !handle.isRemoved();
    }

    public Player getBukkitPlayer() {
        return handle == null ? null : handle.getBukkitEntity();
    }

    public Location getLocation() {
        Player bukkit = getBukkitPlayer();
        return bukkit == null ? null : bukkit.getLocation();
    }

    public boolean isDead() {
        Player bukkit = getBukkitPlayer();
        return bukkit != null && bukkit.isDead();
    }

    public UUID getAiTargetUuid() {
        return aiTargetUuid;
    }

    public void setAiTargetUuid(UUID aiTargetUuid) {
        this.aiTargetUuid = aiTargetUuid;
    }

    public Location getWanderTarget() {
        return wanderTarget;
    }

    public void setWanderTarget(Location wanderTarget) {
        this.wanderTarget = wanderTarget;
    }

    public Location getGuardPoint() {
        return guardPoint;
    }

    public void setGuardPoint(Location guardPoint) {
        this.guardPoint = guardPoint;
    }

    public int getAttackTicks() {
        return attackTicks;
    }

    public void setAttackTicks(int attackTicks) {
        this.attackTicks = attackTicks;
    }

    public int getDeathTicks() {
        return deathTicks;
    }

    public void setDeathTicks(int deathTicks) {
        this.deathTicks = deathTicks;
    }

    public boolean isSilentRespawn() {
        return silentRespawn;
    }

    public UUID getPossessedBy() {
        return possessedBy;
    }

    public void setPossessedBy(UUID possessedBy) {
        this.possessedBy = possessedBy;
    }

    public boolean isPossessed() {
        return possessedBy != null;
    }
}
