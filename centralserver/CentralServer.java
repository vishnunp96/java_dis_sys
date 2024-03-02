package centralserver;

import common.*;

/*
 * Updated on Feb 2023
 */
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class CentralServer implements ICentralServer {

    private final List<MessageInfo> receivedMessages;
    private final List<Boolean> received;

    private final int timeout;

    private final Timer timer;

    private TimerTask timeOutTask;

    protected CentralServer () throws RemoteException {
        super();

        receivedMessages = new ArrayList<>();
        received = new ArrayList<>();
        timeout = 30000;
        timer = new Timer();
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

    @Override
    public void receiveMsg (MessageInfo msg) {
        System.out.println("[Central Server] Received message " + (msg.getMessageNum()) + " out of " +
                msg.getTotalMessages() + ". Measure = " + msg.getMessage());

        if(msg.getMessageNum() == 1) {
            initialise(msg.getTotalMessages());
            initialiseTimeout();
            timer.schedule(timeOutTask, timeout);
        }

        receivedMessages.add(msg);
        received.set(msg.getMessageNum() - 1, Boolean.TRUE);

        if(msg.getMessageNum() == msg.getTotalMessages()) {
            printStats();
            timeOutTask.cancel();
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

    private void printMissingMessages(int totalMessages) {
        List<Integer> missingMessages = new ArrayList<>();
        for(int i = 0; i < totalMessages; i++) {
            if(!received.get(i)) {
                missingMessages.add(i + 1);
            }
        }
        System.out.println("Missing messages: " + missingMessages);
    }

    private void initialiseTimeout() {
        timeOutTask = new TimerTask() {
            @Override
            public void run() {
                System.out.println("[Central Server] Timeout reached. Resetting received messages.");
                printStats();
            }
        };
    }

}
