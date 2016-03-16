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
    private int sequenceNumber;

    Runnable receiveRunnable = () -> {
        //TODO: setup to only have one command sent at a time otherwhise received data maybe out of order
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
                            //Check to see if this message is segmented by verifying if the 8th byte is a 0
                            byte[] tempArray = receiveBuffer.array();
                            if (receiveBuffer.array()[7] == 0) {
                                int totalPackets = receiveBuffer.get();
                                int packetIndex = receiveBuffer.get();
                                String[] completeMessageArray = new String[totalPackets];
                                receiveBuffer.get();
                                completeMessageArray[packetIndex] = new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining());

                                //Process existing packet and retrieve the next
                                while (packetIndex < totalPackets) {
                                    receiveData();
                                    receiveBuffer.get();
                                    completeMessageArray[packetIndex] = new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining());
                                    packetIndex++;
                                }

                                String completeMessage = "";
                                for (String message : completeMessageArray) {
                                    completeMessage += message;
                                }

                                System.out.println(completeMessage);
                            } else {
                                receiveBuffer.get();
                                System.out.println(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
                            }
                        }
                        break;
                        case Server: {
                            //Output the message and send an acknowledgement to the server
                            System.out.println("Received server message");
                            byte serverSequenceNumber = receiveBuffer.get();
                            System.out.println(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
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
        sequenceNumber = -1;

        receiveThread.start();

        constructPacket(BEMessageType.Login, -1, beloginCredential.getHostPassword());
        sendData();

        try {
            Thread.sleep(5000);
            constructPacket(BEMessageType.Command, 0, BECommandType.Bans.toString());
            sendData();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            datagramChannel.close();
            receiveThread.interrupt();
            receiveThread = null;
            sendBuffer = null;
            receiveBuffer = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        if (datagramChannel.isConnected()) {
            try {
                datagramChannel.write(sendBuffer);
                lastSentTime.set(System.currentTimeMillis());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Receives and validates the incoming packet
    //'B'(0x42) | 'E'(0x45) | 4-byte CRC32 checksum of the subsequent bytes | 0xFF
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

    public int nextSequenceNumber() {
        sequenceNumber = sequenceNumber == 255 ? 0 : sequenceNumber++;
        return sequenceNumber;
    }
}
