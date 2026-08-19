package ru.phantom.core;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetSocketAddress;

/**
 * Фейковое сетевое соединение для бота.
 * <p>
 * ServerPlayer на сервере обязан иметь Connection, иначе PlayerList.placeNewPlayer
 * и вся логика отправки пакетов упадут с NPE. Бот не имеет реального клиента,
 * поэтому мы подсовываем EmbeddedChannel и глушим исходящие пакеты.
 */
public class EmptyConnection extends Connection {

    public EmptyConnection() {
        super(PacketFlow.SERVERBOUND);
        // Канал нужен, чтобы isConnected() возвращал true и сервер считал игрока живым.
        this.channel = new EmbeddedChannel();
        this.address = new InetSocketAddress("127.0.0.1", 0);
        try {
            this.channel.config().setAutoRead(false);
        } catch (Throwable ignored) {
            // не критично
        }
    }

    /** Пакеты боту слать некому — гасим их, чтобы не тратить ресурсы. */
    @Override
    public void send(Packet<?> packet) {
        // no-op
    }

    @Override
    public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {
        // no-op
    }

    @Override
    public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) {
        // no-op
    }

    @Override
    public void tick() {
        // Не гоняем keep-alive / таймауты: у бота нет клиента, который на них ответит.
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}
