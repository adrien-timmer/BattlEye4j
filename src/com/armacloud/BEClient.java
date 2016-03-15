package com.armacloud;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * Created by Adrien on 3/14/2016.
 */
public class BEClient {

    private static final CRC32 CRC = new CRC32();
    private static ByteBuffer sendBuffer;
    private static ByteBuffer receiveBuffer;
    private BELoginCredential beloginCredential;
    private DatagramChannel datagramChannel;
    private AtomicLong lastReceivedTime;
    private AtomicLong lastSentTime;

    Runnable receiveRunnable = () -> {
        try {
            while (datagramChannel.isConnected()) {
                if (receiveData()) {
                    lastReceivedTime.set(System.currentTimeMillis());
                    BEMessageType packetType = BEMessageType.convertByteToPacketType(receiveBuffer.get());
                    switch (packetType) {
                        case Login: {
                            System.out.println("Received login message");
                            //TODO: handle response timeout (indicates that BE is not enabled on the host)
                            BEConnectType connectionResult = BEConnectType.convertByteToConnectType(receiveBuffer.array()[8]);
                            switch (connectionResult) {
                                case Failure:
                                    System.out.println("Failed to login");
                                    break;
                                case Success:
                                    System.out.println("Successful login");
                                    break;
                                case Unknown:
                                    System.out.println("Login received an unexpected response");
                                    break;
                            }
                        }
                        break;
                        case Command: {

                        }
                        break;
                        case Server: {
                            System.out.println("Received server message");
                            byte serverSequenceNumber = receiveBuffer.get();
                            System.out.println(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
                            constructPacket(BEMessageType.Server, serverSequenceNumber, null);
                            sendData();
                        }
                        break;
                        case Unknown: {
                            System.out.println("Received unknown packet/response type");
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    Thread receiveThread = new Thread(receiveRunnable);

    public BEClient(BELoginCredential beLoginCredential) {
        this.beloginCredential = beLoginCredential;
    }

    public void connect() throws IOException {
        datagramChannel = DatagramChannel.open();
        datagramChannel.connect(beloginCredential.getHostAddress());

        sendBuffer = ByteBuffer.allocate(datagramChannel.getOption(StandardSocketOptions.SO_SNDBUF));
        sendBuffer.order(ByteOrder.LITTLE_ENDIAN);
        receiveBuffer = ByteBuffer.allocate(datagramChannel.getOption(StandardSocketOptions.SO_RCVBUF));
        receiveBuffer.order(ByteOrder.LITTLE_ENDIAN);

        lastSentTime = new AtomicLong(System.currentTimeMillis());
        lastReceivedTime = new AtomicLong(System.currentTimeMillis());

        receiveThread.start();

        constructPacket(BEMessageType.Login, -1, beloginCredential.getHostPassword());
        sendData();
    }

    //Contructs a packet following the BE protocol
    //See http://www.battleye.com/downloads/BERConProtocol.txt for details
    public void constructPacket(BEMessageType packetType, int sequenceNumber, String command) throws IOException {
        sendBuffer.clear();
        sendBuffer.put((byte) 'B');
        sendBuffer.put((byte) 'E');
        sendBuffer.position(6);
        sendBuffer.put((byte) 0xFF);
        sendBuffer.put(packetType.getType());

        if (sequenceNumber >= 0)
            sendBuffer.put((byte) sequenceNumber);

        if (command != null && !command.isEmpty())
            sendBuffer.put(command.getBytes());

        CRC.reset();
        CRC.update(sendBuffer.array(), 6, sendBuffer.position() - 6);
        sendBuffer.putInt(2, (int) CRC.getValue());

        sendBuffer.flip();
    }

    public void sendData() {
        if (datagramChannel.isConnected()){
            try {
                datagramChannel.write(sendBuffer);
                lastSentTime.set(System.currentTimeMillis());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean receiveData() throws IOException {
        //TODO: simplify to a single return
        receiveBuffer.clear();
        int read = datagramChannel.read(receiveBuffer);
        if (read < 7) {
            System.out.println("Received invalid header size");
            return false;
        }

        receiveBuffer.flip();

        if (receiveBuffer.get() != (byte) 'B' || receiveBuffer.get() != (byte) 'E') {
            System.out.println("Received invalid header");
            return false;
        }

        receiveBuffer.getInt();

        if (receiveBuffer.get() != (byte) 0xFF) {
            System.out.println("Received invalid header");
            return false;
        }

        return true;
    }
}
