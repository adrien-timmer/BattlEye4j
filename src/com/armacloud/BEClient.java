package com.armacloud;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

class BEClient {

    public AtomicBoolean connected;
    private static final CRC32 CRC = new CRC32();
    private static ByteBuffer sendBuffer;
    private static ByteBuffer receiveBuffer;
    private final List<ConnectionHandler> connectionHandlerList;
    private final List<ResponseHandler> responseHandlerList;
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
                            //TODO: handle response timeout (indicates that BE is not enabled on the host)
                            BEConnectType connectionResult = BEConnectType.convertByteToConnectType(receiveBuffer.array()[8]);
                            switch (connectionResult) {
                                case Failure:
                                    fireConnectionConnectedEvent(BEConnectType.Failure);
                                    disconnect(BEDisconnectType.ConnectionLost);
                                    break;
                                case Success:
                                    fireConnectionConnectedEvent(BEConnectType.Success);
                                    connected.set(true);
                                    break;
                                case Unknown:
                                    fireConnectionConnectedEvent(BEConnectType.Unknown);
                                    disconnect(BEDisconnectType.ConnectionLost);
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

                                    fireResponseEvent(completeMessage);
                                } else {
                                    receiveBuffer.position(receiveBuffer.position() - 1);
                                    fireResponseEvent(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
                                }
                                sendNextCommand();
                            }
                        }
                        break;
                        case Server: {
                            //Output the message and send an acknowledgement to the server
                            byte serverSequenceNumber = receiveBuffer.get();
                            fireResponseEvent(new String(receiveBuffer.array(), receiveBuffer.position(), receiveBuffer.remaining()));
                            constructPacket(BEMessageType.Server, serverSequenceNumber, null);
                            sendData();
                            sendNextCommand();
                        }
                        break;
                        case Unknown: {
                            //?
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
        connectionHandlerList = new ArrayList<>();
        responseHandlerList = new ArrayList<>();
        Runnable monitorRunnable = () -> {
            while (datagramChannel.isConnected()) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    System.out.println("Last received time: " + lastReceivedTime.get());
                    System.out.println("Last sent time: " + lastSentTime.get());
                    //Check to see if we've exceeded our timeout
                    if (lastSentTime.get() - lastReceivedTime.get() > 10000) {
                        disconnect(BEDisconnectType.ConnectionLost);
                    }

                    //Send an empty packet to keep our connection alive
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
        connected = new AtomicBoolean(false);

        receiveThread.start();
        monitorThread.start();

        //Login packet is a bit unique since we want to set -1 as the sequence number
        constructPacket(BEMessageType.Login, -1, beloginCredential.getHostPassword());
        sendData();
    }

    private void disconnect(BEDisconnectType disconnectType) {
        try {
            connected.set(false);
            commandQueue = null;
            datagramChannel.disconnect();
            datagramChannel.close();
            receiveThread.interrupt();
            monitorThread.interrupt();
            receiveThread = null;
            sendBuffer = null;
            receiveBuffer = null;
            fireConnectionDisconnectVent(disconnectType);
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
        receiveBuffer.clear();
        int read = datagramChannel.read(receiveBuffer);
        if (read < 7) {
            return false;
        }

        receiveBuffer.flip();

        if (receiveBuffer.get() != (byte) 'B' || receiveBuffer.get() != (byte) 'E') {
            return false;
        }

        receiveBuffer.getInt();

        if (receiveBuffer.get() != (byte) 0xFF) {
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

    void sendCommand(BECommandType commandType, String... commandArgs) {
        StringBuilder commandBuilder = new StringBuilder(commandType.toString());
        for (String arg : commandArgs) {
            commandBuilder.append(' ');
            commandBuilder.append(arg);
        }
        BECommand command = new BECommand(BEMessageType.Command, commandBuilder.toString());
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

    void addConnectionHandler(ConnectionHandler connectionHandler) {
        connectionHandlerList.add(connectionHandler);
    }

    void addResponseHandler(ResponseHandler responseHandler) {
        responseHandlerList.add(responseHandler);
    }

    private void fireConnectionConnectedEvent(BEConnectType connectType) {
        for (ConnectionHandler connectionHandler : connectionHandlerList) {
            connectionHandler.onConnected(connectType);
        }
    }

    private void fireConnectionDisconnectVent(BEDisconnectType disconnectType) {
        for (ConnectionHandler connectionHandler : connectionHandlerList) {
            connectionHandler.onDisconnected(disconnectType);
        }
    }

    private void fireResponseEvent(String response) {
        for (ResponseHandler responseHandler : responseHandlerList) {
            responseHandler.onResponse(response);
        }
    }
}
