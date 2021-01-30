package nl.friendlymirror.top10;

import java.net.ServerSocket;

import lombok.SneakyThrows;

public class RandomPort {

    @SneakyThrows
    public static int get() {
        var socket = new ServerSocket(0);
        var port = socket.getLocalPort();
        socket.close();

        return port;
    }
}
