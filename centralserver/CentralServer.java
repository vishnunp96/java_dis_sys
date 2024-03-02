package centralserver;

import common.*;

/*
 * Updated on Feb 2023
 */
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CentralServer implements ICentralServer {

    private final List<MessageInfo> receivedMessages;
    private final List<Boolean> received;

    protected CentralServer () throws RemoteException {
        super();

        receivedMessages = new ArrayList<>();
        received = new ArrayList<>();
    }

    private void initialise(int totalMessages) {
        receivedMessages.clear();
        received.clear();
        received.addAll(Collections.nCopies(totalMessages, Boolean.FALSE));
    }

    public static void main (String[] args) throws RemoteException {
        CentralServer cs = new CentralServer();
        ICentralServer csStub = (ICentralServer) UnicastRemoteObject.exportObject(
                (ICentralServer)cs, 0);

        int port = 1099;
        if(args.length == 1){
            port = Integer.parseInt(args[0]);
        }
        Registry registry = LocateRegistry.createRegistry(port);

        registry.rebind("CentralServer", csStub);

        System.out.println("Central Server ready");
    }

    private void printMissingMessages(int totalMessages) {
        List<Integer> missingMessages = new ArrayList<>();
        for(int i = 0; i < totalMessages; i++) {
            if(!received.get(i)) {
                missingMessages.add(i + 1);
            }
        }
        System.out.println("Missing messages: " + missingMessages);
    }

    @Override
    public void receiveMsg (MessageInfo msg) {
        System.out.println("[Central Server] Received message " + (msg.getMessageNum()) + " out of " +
                msg.getTotalMessages() + ". Measure = " + msg.getMessage());

        if(msg.getMessageNum() == 1) {
            initialise(msg.getTotalMessages());
        }

        receivedMessages.add(msg);
        received.set(msg.getMessageNum() - 1, Boolean.TRUE);

        if(msg.getMessageNum() == msg.getTotalMessages()) {
            printStats();
        }
    }

    public void printStats() {
        int totalMessages = receivedMessages.get(0).getTotalMessages();
        int missing = Collections.frequency(received, Boolean.FALSE);
        System.out.println("Total missing messages: " + missing + " out of " + totalMessages);

        if(missing > 0) {
            printMissingMessages(totalMessages);
        }

        initialise(0);
        System.out.println();
    }

}
