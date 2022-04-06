import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class Client {
    public static void main(String[] args) {
        String hostname = "localhost";
        int port = 9989;

        try {
            InetAddress address = InetAddress.getByName(hostname);
            DatagramSocket socket = new DatagramSocket();

            // 1. Client sends SYN packet
            DatagramPacket synPacket = new DatagramPacket(new byte[]{ PACKET_TYPES.SYN.value() }, 1, address, port);
            socket.send(synPacket);
            // 4. Client received SYNACK packet
            byte[] synackBuffer = new byte[1];
            DatagramPacket synackResponsePacket = new DatagramPacket(synackBuffer, synackBuffer.length);
            socket.receive(synackResponsePacket);
            // 5. Client sends REQUEST packet
            DatagramPacket requestPacket = new DatagramPacket(new byte[]{ PACKET_TYPES.REQUEST.value() }, 1, address, port);
            socket.send(requestPacket);
            StringBuilder quoteOfTheDay = new StringBuilder();
            byte previousSequenceNumber = 1;
            while (true) {
                byte[] dataBuffer = new byte[18];
                DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length, address, port);
                socket.receive(dataPacket);
                byte[] header = Arrays.copyOfRange(dataBuffer, 0, 2);
                byte[] quoteChunk = Arrays.copyOfRange(dataBuffer, 2, dataBuffer.length);
                byte packetType = header[0];
                byte sequenceNumber = header[1];
                if (packetType == PACKET_TYPES.FIN.value()) {
                    // Done
                    System.out.println("Received FIN packet, cleaning up and closing connection");
                    break;
                }
                // Check if valid sequence number
                else if (sequenceNumber != previousSequenceNumber) {
                    // Update previous sequence number
                    previousSequenceNumber = sequenceNumber;
                    // Append quote chunk
                    quoteOfTheDay.append(new String(quoteChunk, 0, dataPacket.getLength() - 2));
                    // Send ack of sequence number
                    DatagramPacket dataResponsePacket = new DatagramPacket(new byte[]{ PACKET_TYPES.ACK.value(), sequenceNumber}, 2, address, port);
                    socket.send(dataResponsePacket);
                }
            }
            System.out.println(quoteOfTheDay.toString());
            // Close socket
            socket.close();
        } catch (SocketTimeoutException ex) {
            System.out.println("Timeout error: " + ex.getMessage());
            ex.printStackTrace();
        } catch (IOException ex) {
            System.out.println("Client error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
