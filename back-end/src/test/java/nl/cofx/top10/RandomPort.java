package nl.cofx.top10;

import java.net.ServerSocket;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RandomPort {

    @SneakyThrows
    public static int get() {
        var socket = new ServerSocket(0);
        var port = socket.getLocalPort();
        socket.close();

        log.info("Using random port \"{}\"", port);

        return port;
    }
}
