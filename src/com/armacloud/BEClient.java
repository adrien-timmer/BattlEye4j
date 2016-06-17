package com.armacloud;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

class BEClient {

    private static final CRC32 CRC = new CRC32();
    private static ByteBuffer sendBuffer;
    private static ByteBuffer receiveBuffer;
    private BELoginCredential beloginCredential;
    private DatagramChannel datagramChannel;
    private AtomicLong lastReceivedTime;
    private AtomicLong lastSentTime;
    private AtomicInteger sequenceNumber;
    private Queue<BECommand> commandQueue;

    private Runnable receiveRunnable = () -> {
        //TODO: setup to only have one command sent at a time otherwise received data maybe out of order
        try {
            while (datagramChannel.isConnected()) {
                if (receiveData()) {
                    lastReceivedTime.set(System.currentTimeMillis());
                    BEMessageType messageType = BEMessageType.convertByteToPacketType(receiveBuffer.get());
                    switch (messageType) {
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
                            //Check to see if this message is segmented
                            receiveBuffer.get();
                            //Check to prevent BufferUnderFlowException
                            if (receiveBuffer.hasRemaining()) {
                                if (receiveBuffer.get() == 0x00) {
                                    int totalPackets = receiveBuffer.get();
                                    int packetIndex = receiveBuffer.get();
                                    String[] messageArray = new String[totalPackets];
                                    messageArray[packetIndex] = new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining());
                                    packetIndex++;

                                    //Process the remaining segmented messages
                                    while (packetIndex < totalPackets) {
                                        receiveData();
                                        receiveBuffer.position(12);
                                        messageArray[packetIndex] = new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining());
                                        packetIndex++;
                                    }

                                    String completeMessage = "";
                                    for (String message : messageArray) {
                                        completeMessage += message;
                                    }

                                    System.out.println("Received a command response");
                                    System.out.println(completeMessage);
                                } else {
                                    receiveBuffer.position(receiveBuffer.position() - 1);
                                    System.out.println("Received a command response");
                                    System.out.println(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
                                }
                                sendNextCommand();
                            }
                        }
                        break;
                        case Server: {
                            //Output the message and send an acknowledgement to the server
                            System.out.println("Received server message");
                            byte serverSequenceNumber = receiveBuffer.get();
                            System.out.println(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
                            constructPacket(BEMessageType.Server, serverSequenceNumber, null);
                            sendData();
                            sendNextCommand();
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

    private Thread receiveThread = new Thread(receiveRunnable);
    private Thread monitorThread;

    BEClient(BELoginCredential beLoginCredential) {
        this.beloginCredential = beLoginCredential;
        Runnable monitorRunnable = () -> {
            while (datagramChannel.isConnected()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    System.out.println("Last received time: " + lastReceivedTime.get());
                    System.out.println("Last sent time: " + lastSentTime.get());
                    //Check to see if we've exceeded our timeout
                    if (lastSentTime.get() - lastReceivedTime.get() > 10000) {
                        disconnect();
                    }

                    //Send an empty packet to keep out connection alive
                    if (System.currentTimeMillis() - lastSentTime.get() >= 27000) {
                        try {
                            constructPacket(BEMessageType.Command, nextSequenceNumber(), null);
                            sendData();
                            System.out.println("Sent keep alive");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        monitorThread = new Thread(monitorRunnable);
    }

    void connect() throws IOException {
        datagramChannel = DatagramChannel.open();
        datagramChannel.connect(beloginCredential.getHostAddress());

        sendBuffer = ByteBuffer.allocate(datagramChannel.getOption(StandardSocketOptions.SO_SNDBUF));
        sendBuffer.order(ByteOrder.LITTLE_ENDIAN);

        receiveBuffer = ByteBuffer.allocate(datagramChannel.getOption(StandardSocketOptions.SO_RCVBUF));
        receiveBuffer.order(ByteOrder.LITTLE_ENDIAN);

        commandQueue = new ConcurrentLinkedDeque<>();

        lastSentTime = new AtomicLong(System.currentTimeMillis());
        lastReceivedTime = new AtomicLong(System.currentTimeMillis());
        sequenceNumber = new AtomicInteger(-1);

        receiveThread.start();
        monitorThread.start();

        constructPacket(BEMessageType.Login, -1, beloginCredential.getHostPassword());
        sendData();
    }

    private void disconnect() {
        try {
            System.out.println("Disconnected");
            commandQueue = null;
            datagramChannel.disconnect();
            datagramChannel.close();
            receiveThread.interrupt();
            monitorThread.interrupt();
            receiveThread = null;
            sendBuffer = null;
            receiveBuffer = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Construct a packet following the BE protocol
    //See http://www.battleye.com/downloads/BERConProtocol.txt for details
    private void constructPacket(BEMessageType messageType, int sequenceNumber, String command) throws IOException {
        sendBuffer.clear();
        sendBuffer.put((byte) 'B');
        sendBuffer.put((byte) 'E');
        sendBuffer.position(6);
        sendBuffer.put((byte) 0xFF);
        sendBuffer.put(messageType.getType());

        if (sequenceNumber >= 0)
            sendBuffer.put((byte) sequenceNumber);

        if (command != null && !command.isEmpty())
            sendBuffer.put(command.getBytes());

        CRC.reset();
        CRC.update(sendBuffer.array(), 6, sendBuffer.position() - 6);
        sendBuffer.putInt(2, (int) CRC.getValue());

        sendBuffer.flip();
    }

    private void sendData() {
        if (datagramChannel.isConnected()) {
            try {
                datagramChannel.write(sendBuffer);
                lastSentTime.set(System.currentTimeMillis());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendNextCommand() {
        if (commandQueue != null && commandQueue.size() >= 1) {
            BECommand command = commandQueue.poll();
            if (command != null) {
                try {
                    constructPacket(command.messageType, nextSequenceNumber(), command.command);
                    sendData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //Receives and validates the incoming packet
    //'B'(0x42) | 'E'(0x45) | 4-byte CRC32 checksum of the subsequent bytes | 0xFF
    private boolean receiveData() throws IOException {
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

    private int nextSequenceNumber() {
        int tempSequenceNumber = sequenceNumber.get();
        tempSequenceNumber = tempSequenceNumber == 255 ? 0 : tempSequenceNumber + 1;
        sequenceNumber.set(tempSequenceNumber);
        return sequenceNumber.get();
    }

    void sendCommand(BECommandType commandType) {
        BECommand command = new BECommand(BEMessageType.Command, commandType.toString());
        queueCommand(command);
    }

    void sendCommand(BECommandType commandType, String commandArgs) {
        BECommand command = new BECommand(BEMessageType.Command, commandType.toString() + commandArgs);
        queueCommand(command);
    }

    private void queueCommand(BECommand command) {
        if (commandQueue.size() >= 1) {
            commandQueue.add(command);
        } else {
            commandQueue.add(command);
            sendNextCommand();
        }
    }
}
