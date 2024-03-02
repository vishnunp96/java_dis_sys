package field;
/*
 * Updated on Feb 2023
 */
import centralserver.ICentralServer;
import common.MessageInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class FieldUnit implements IFieldUnit {
    private ICentralServer central_server;
    private final List<MessageInfo> receivedMessages;
    private final List<Float> smaValues;
    private final int sendAttempts;

    private int retriesElapsed;
    private DatagramSocket socket;

    /* Note: Could you discuss in one line of comment what do you think can be
     * an appropriate size for buffsize?
     * (Which is used to init DatagramPacket?)
     * The buffer size should be set to the maximum size of the message that we want to send.
     * After seeing the average message size, we can set the buffer size to a value that is
     * slightly larger than the average message size, which was found to be 20.
     */

    private static final int buffsize = 32;
    private final int timeout;


    public FieldUnit () {
        receivedMessages = new ArrayList<>();
        smaValues = new ArrayList<>();
        sendAttempts = 3;
        timeout = 30000;
        retriesElapsed = 0;
    }

    @Override
    public void addMessage (MessageInfo msg) {
        receivedMessages.add(msg);
        System.out.println("[Field Unit] Message " + msg.getMessageNum() + " out of " + msg.getTotalMessages() + " received. Value = " + msg.getMessage());
    }

    @Override
    public void sMovingAverage (int k) {
        System.out.println("[Field Unit] Computing SMAs");
        smaValues.clear();
        float sum = 0;
        for(int i = 0; i < receivedMessages.size(); i++) {
            sum += receivedMessages.get(i).getMessage();
            if(i < k) {
                smaValues.add(receivedMessages.get(i).getMessage());
            } else {
                sum -= receivedMessages.get(i - k).getMessage();
                smaValues.add(sum / k);
            }
        }
    }



    @Override
    public void receiveMeasures(int port, int timeout) {

        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(timeout);
        } catch (SocketException e) {
            throw new RuntimeException(" [Field Unit] - UDP socket could not bind to local port:" + port, e);
        }

        boolean listen = true;
        System.out.println("[Field Unit] Listening on port: " + port);

        while (listen) {
            DatagramPacket packet = new DatagramPacket(new byte[buffsize], buffsize);
//            System.out.println("[Field Unit] Waiting for message...");
            try{
                socket.receive(packet);
            } catch (IOException e) {
                if(e instanceof SocketTimeoutException){
                    if(!receivedMessages.isEmpty()){
                        System.out.println("[Field Unit] Timeout reached. No more messages to receive.");
                        break;
                    } else {
                        continue;
                    }
                } else {
                    throw new RuntimeException("[Field Unit] Error receiving message", e);
                }
            }

            String msgString = new String(packet.getData(), 0, packet.getLength());
            MessageInfo msg = null;
            try {
                msg = new MessageInfo(msgString);
            } catch (Exception e) {
                throw new RuntimeException("[Field Unit] Received ill-formed message:" + msgString, e);
            }
            if(msg.getMessageNum() == 1) {
                receivedMessages.clear();
                retriesElapsed = 0;
            }
            addMessage(msg);

            if(msg.getMessageNum() == msg.getTotalMessages()) {
                listen = false;
            }
        }
        printStats();

        socket.close();
    }

    public static void main (String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ./fieldunit.sh <UDP rcv port> <RMI server HostName/IPAddress>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String rmiHost = args[1];

        FieldUnit fieldUnit = new FieldUnit();
        fieldUnit.initRMI(rmiHost);

        while(true) {
            fieldUnit.receiveMeasures(port, fieldUnit.timeout);
            if(!fieldUnit.receivedMessages.isEmpty()) {
                fieldUnit.sMovingAverage(7);
                fieldUnit.sendAverages();
            }
        }


    }


    @Override
    public void initRMI (String address) {
        System.out.println("[Field Unit] Initialising RMI connection to Central Server at " + address);
        Registry registry = null;
        try {
            registry = LocateRegistry.getRegistry(address);
        } catch (RemoteException e) {
            throw new RuntimeException("[Field Unit] Unable to find the Central Server address:" + address, e);
        }

        try {
            central_server = (ICentralServer) registry.lookup("CentralServer");
        } catch (NotBoundException | RemoteException e) {
            throw new RuntimeException("[Field Unit] Could not bind to Central Server", e);
        }
    }

    @Override
    public void sendAverages () {
        System.out.println("[Field Unit] Sending SMAs to RMI");
        for(int i = 0; i < receivedMessages.size(); i++) {
            MessageInfo msg = new MessageInfo(receivedMessages.size(),
                    i+1, smaValues.get(i));
            for(int attempt = 0; attempt < sendAttempts; attempt++){
                try {
                    central_server.receiveMsg(msg);
                    break;
                } catch (RemoteException e) {
                    retriesElapsed += 1;
                    if(attempt == sendAttempts - 1) {
                        throw new RuntimeException("[Field Unit] Could not send message to RMI", e);
                    }
                }
            }
        }
        receivedMessages.clear();
        smaValues.clear();
        if(retriesElapsed > 0){
            System.out.println("[Field Unit] Retries elapsed: " + retriesElapsed);
        }
        retriesElapsed = 0;
        System.out.println();
    }

    @Override
    public void printStats () {
        int totalMessages = receivedMessages.get(0).getTotalMessages();
        List<Boolean> received = new ArrayList<>(Collections.nCopies(totalMessages, Boolean.FALSE));
        for(MessageInfo msg : receivedMessages) {
            received.set(msg.getMessageNum() - 1, Boolean.TRUE);
        }
        int missing = (int) received.stream().filter(b -> !b).count();
        System.out.println("Total missing messages = " + missing + " out of " + totalMessages);

        if(missing > 0){
            printMissingMessages(received);
        }

        System.out.println("========================================");
    }

    private void printMissingMessages(List<Boolean> received) {
        List<Integer> missingMessages = new ArrayList<>();
        for(int i = 0; i < received.size(); i++) {
            if(!received.get(i)) {
                missingMessages.add(i + 1);
            }
        }
        System.out.println("[Field Unit] Missing messages: " + missingMessages);
    }


}
