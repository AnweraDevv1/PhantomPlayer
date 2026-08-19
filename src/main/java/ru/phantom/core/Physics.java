package ru.phantom.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * Серверная физика для фейковых игроков.
 * <p>
 * Ключевой момент: {@code ServerPlayer} управляется клиентом. Сервер не применяет
 * к игрокам ни гравитацию, ни {@code setVelocity} — он лишь отправляет пакет
 * реальному клиенту. У бота клиента нет, поэтому {@code setVelocity} для него
 * не делает ровным счётом ничего. Двигать бота можно только прямой сменой
 * позиции, а падение, столкновения и шаг вверх приходится считать вручную.
 */
public final class Physics {

    /** Ускорение свободного падения за тик (близко к ванильному). */
    private static final double GRAVITY = 0.08;
    /** Предельная скорость падения. */
    private static final double MAX_FALL = 3.0;
    /** Ширина хитбокса игрока. */
    private static final double WIDTH = 0.6;
    /** Высота, на которую бот может шагнуть без прыжка. */
    private static final double STEP_HEIGHT = 1.0;

    private Physics() {
    }

    /** Результат расчёта одного шага. */
    public record Step(Location location, double verticalVelocity, boolean onGround) {
    }

    /**
     * Считает новую позицию бота с учётом гравитации и столкновений.
     *
     * @param from        текущая позиция
     * @param motion      желаемое горизонтальное смещение за тик
     * @param verticalVel текущая вертикальная скорость (отрицательная = падение)
     */
    public static Step step(Location from, Vector motion, double verticalVel) {
        World world = from.getWorld();
        double x = from.getX();
        double y = from.getY();
        double z = from.getZ();

        // --- Горизонтальное движение с попыткой шагнуть вверх ---
        double targetX = x + motion.getX();
        double targetZ = z + motion.getZ();

        if (motion.getX() != 0 || motion.getZ() != 0) {
            if (canStand(world, targetX, y, targetZ)) {
                x = targetX;
                z = targetZ;
            } else {
                // Пробуем подняться на блок (лестница, ступенька, забор в 1 блок).
                boolean stepped = false;
                for (double lift = 0.5; lift <= STEP_HEIGHT; lift += 0.5) {
                    if (canStand(world, targetX, y + lift, targetZ)
                            && isPassable(world, targetX, y + lift + 1.0, targetZ)) {
                        x = targetX;
                        z = targetZ;
                        y = y + lift;
                        stepped = true;
                        break;
                    }
                }
                if (!stepped) {
                    // Скользим вдоль стены: пробуем оси по отдельности.
                    if (canStand(world, targetX, y, z)) {
                        x = targetX;
                    } else if (canStand(world, x, y, targetZ)) {
                        z = targetZ;
                    }
                }
            }
        }

        // --- Вертикальное движение ---
        boolean onGround;
        double newVertical = verticalVel;

        if (isInLiquid(world, x, y, z)) {
            // В воде бот всплывает, а не тонет камнем.
            newVertical = 0.05;
            double swimY = y + newVertical;
            if (canStand(world, x, swimY, z)) {
                y = swimY;
            }
            onGround = false;
        } else if (isSolidBelow(world, x, y, z)) {
            // Стоим на земле.
            y = Math.floor(y * 2.0) / 2.0;
            double supportY = supportHeight(world, x, y, z);
            if (supportY > Double.NEGATIVE_INFINITY) {
                y = supportY;
            }
            newVertical = 0;
            onGround = true;
        } else {
            // Падение.
            newVertical = Math.max(-MAX_FALL, newVertical - GRAVITY);
            double nextY = y + newVertical;

            // Не проваливаемся сквозь пол: ищем ближайшую опору.
            double landing = landingHeight(world, x, y, nextY, z);
            if (!Double.isNaN(landing)) {
                y = landing;
                newVertical = 0;
                onGround = true;
            } else {
                y = nextY;
                onGround = false;
            }
        }

        Location result = new Location(world, x, y, z, from.getYaw(), from.getPitch());
        return new Step(result, newVertical, onGround);
    }

    /** Может ли бот занимать позицию (хитбокс 0.6 x 1.8 свободен). */
    public static boolean canStand(World world, double x, double y, double z) {
        double half = WIDTH / 2.0;
        for (double dx = -half; dx <= half; dx += WIDTH) {
            for (double dz = -half; dz <= half; dz += WIDTH) {
                if (!isPassable(world, x + dx, y + 0.1, z + dz)) {
                    return false;
                }
                if (!isPassable(world, x + dx, y + 1.7, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Есть ли твёрдый блок прямо под ногами. */
    private static boolean isSolidBelow(World world, double x, double y, double z) {
        double half = WIDTH / 2.0;
        for (double dx = -half; dx <= half; dx += WIDTH) {
            for (double dz = -half; dz <= half; dz += WIDTH) {
                Block block = world.getBlockAt(
                        (int) Math.floor(x + dx),
                        (int) Math.floor(y - 0.05),
                        (int) Math.floor(z + dz));
                if (block.getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Верхняя грань блока, на котором стоит бот. */
    private static double supportHeight(World world, double x, double y, double z) {
        Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y - 0.05), (int) Math.floor(z));
        if (!block.getType().isSolid()) {
            return Double.NEGATIVE_INFINITY;
        }
        var box = block.getBoundingBox();
        return box.getMaxY();
    }

    /**
     * Ищет поверхность между текущей и следующей высотой.
     *
     * @return высота приземления или {@code NaN}, если бот продолжает падать
     */
    private static double landingHeight(World world, double x, double fromY, double toY, double z) {
        int startBlock = (int) Math.floor(fromY);
        int endBlock = (int) Math.floor(toY);

        for (int by = startBlock; by >= endBlock - 1; by--) {
            Block block = world.getBlockAt((int) Math.floor(x), by, (int) Math.floor(z));
            if (block.getType().isSolid()) {
                double top = block.getBoundingBox().getMaxY();
                if (top <= fromY + 0.001 && top >= toY - 0.001) {
                    return top;
                }
            }
        }
        return Double.NaN;
    }

    /** Проходим ли блок насквозь (воздух, трава, вода). */
    private static boolean isPassable(World world, double x, double y, double z) {
        Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        Material type = block.getType();
        if (type.isAir() || !type.isSolid()) {
            return true;
        }
        return block.isPassable();
    }

    /** Находится ли бот в жидкости. */
    private static boolean isInLiquid(World world, double x, double y, double z) {
        Material type = world.getBlockAt(
                (int) Math.floor(x), (int) Math.floor(y + 0.5), (int) Math.floor(z)).getType();
        return type == Material.WATER || type == Material.LAVA;
    }
}
