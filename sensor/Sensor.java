package sensor;

import common.MessageInfo;

import java.io.IOException;
import java.net.*;
import java.util.Random;

public class Sensor implements ISensor {
    private float measurement;

    private final static int max_measure = 50;
    private final static int min_measure = 10;

    private final DatagramSocket socket;
    private byte[] buffer;

    /* Note: Could you discuss in one line of comment what you think can be
     * an appropriate size for buffsize?
     * (Which is used to init DatagramPacket?)
     * The buffer size should be set to the maximum size of the message that we want to send.
     * After seeing the average message size, we can set the buffer size to a value that is
     * slightly larger than the average message size, which was found to be 20.
     */
    private static final int buffsize = 32;

    private String address;
    private int port;
    private int totMsg;

    private long startTime;

    public Sensor(String address, int port, int totMsg) throws SocketException {
        socket = new DatagramSocket();
        this.address = address;
        this.port = port;
        this.totMsg = totMsg;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run (int N) {
        for(int i = 0; i < N; i++) {
            float measurement = this.getMeasurement();
            MessageInfo msg = new MessageInfo(N, i + 1, measurement);
            sendMessage(address, port, msg);
        }
    }

    public static void main (String[] args) throws SocketException {
        if (args.length < 3) {
            System.out.println("Usage: ./sensor.sh field_unit_address port number_of_measures");
            return;
        }

        String address = args[0];
        int port = Integer.parseInt(args[1]);
        int totMsg = Integer.parseInt(args[2]);

        Sensor sensor = new Sensor(address, port, totMsg);

        sensor.run(sensor.totMsg);

        sensor.socket.close();
        System.out.println("[Sensor] All messages sent. Time taken: " +
                (System.currentTimeMillis() - sensor.startTime) + "ms.");
    }

    @Override
    public void sendMessage (String address, int port, MessageInfo msg) {
        buffer = msg.toString().getBytes();

        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new RuntimeException("[Sensor] Unable to find the field unit address.", e);
        }

        System.out.println("[Sensor] Sending message " + msg.getMessageNum() + " out of " + msg.getTotalMessages()
                + ". Measure = " + msg.getMessage());
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, inetAddress, port);

        try {
            socket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException("[Sensor] Unable to send packet.", e);
        }
    }

    @Override
    public float getMeasurement () {
        Random r = new Random();
        measurement = r.nextFloat() * (max_measure - min_measure) + min_measure;
        return measurement;
    }
}
