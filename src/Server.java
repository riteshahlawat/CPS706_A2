import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class Server {
    private static final List<String> quotes = new ArrayList<>();
    private static final Random random = new Random();
    private static final int PORT = 9989;
    private static final int CHUNK_SIZE = 16;
    private static final String quotesFile = "quotes.txt";
    private static DatagramSocket socket;
    public static void main(String[] args) {
        try {
            loadQuotes();
            socket = new DatagramSocket(PORT);
            System.out.println("Server started");
            while (true) {
                // Initially set timeout to indefinite until a connection is established from a client who wants to receive a QOTD
                socket.setSoTimeout(0);
                System.out.println("\nWaiting for connection from client...\n");
                // 2. Server receives SYN packet
                byte[] synReceiveBuffer = new byte[1];
                DatagramPacket synReceivePacket = new DatagramPacket(synReceiveBuffer, synReceiveBuffer.length);
                socket.receive(synReceivePacket);
                if (synReceiveBuffer[0] == PACKET_TYPES.SYN.value()) {
                    // Set timeout to 1 seconds
                    socket.setSoTimeout(1_000);

                    InetAddress clientAddress = synReceivePacket.getAddress();
                    int clientPort = synReceivePacket.getPort();
                    // 3. Server sends SYNACK packet
                    DatagramPacket synackResponsePacket = new DatagramPacket(new byte[]{ PACKET_TYPES.SYNACK.value() }, 1, clientAddress, clientPort);
                    socket.send(synackResponsePacket);
                    // 6. Server recieves REQUEST packet
                    byte[] requestReceiveBuffer = new byte[1];
                    receivePacketAndValidate(requestReceiveBuffer, clientAddress, clientPort);
                    if (requestReceiveBuffer[0] == PACKET_TYPES.REQUEST.value()) {
                        // 7. Server sends DATA packet with first 16 bytes of data and initial sequence number 0
                        // Sequence number keeps alternating until data is no more, at which point send FIN packet
                        // and close connection upon receiving ACK packet
                        String quote = getQuoteOfTheDay();
                        byte[] quoteBuffer = quote.getBytes(StandardCharsets.UTF_8);
                        byte currSequenceNumber = 0;
                        for (int i = 0; i < quoteBuffer.length; i+= CHUNK_SIZE) {
                            // Current data packet cannot be more than 16 bytes or past the buffer length
                            int endIndexOfCurrentDataPacket = Math.min(i + CHUNK_SIZE, quoteBuffer.length);
                            byte[] currentQuoteBuffer = Arrays.copyOfRange(quoteBuffer, i, endIndexOfCurrentDataPacket);
                            byte[] header = { PACKET_TYPES.DATA.value(), currSequenceNumber };
                            byte[] currentDataBuffer = concatBuffers(header, currentQuoteBuffer);
                            DatagramPacket dataResponsePacket = new DatagramPacket(currentDataBuffer, currentDataBuffer.length, clientAddress, clientPort);
                            socket.send(dataResponsePacket);
                            // Wait for ACK response and verify sequence number
                            byte[] dataAckResponseBuffer = new byte[2];
                            receivePacketAndValidate(dataAckResponseBuffer, clientAddress, clientPort);
                            byte packetType = dataAckResponseBuffer[0];
                            byte sequenceNumber = dataAckResponseBuffer[1];
                            if (packetType != PACKET_TYPES.ACK.value()) {
                                // For some reason the client didn't send an ack back but an entirely new packet type
                                // Try resending same data chunk again
                                i -= CHUNK_SIZE;
                            } else {
                                if (sequenceNumber == currSequenceNumber) {
                                    // Correct sequence number, update sequence number
                                    currSequenceNumber = currSequenceNumber == (byte) 0 ? (byte) 1 : (byte) 0;
                                } else {
                                    // Incorrect sequence number, resend same chunk again
                                    i -= CHUNK_SIZE;
                                }
                            }
                        }
                        // 8. Send FIN packet
                        DatagramPacket finResponsePacket = new DatagramPacket(new byte[]{ PACKET_TYPES.FIN.value() }, 1, clientAddress, clientPort);
                        socket.send(finResponsePacket);
                        System.out.println("Sent FIN packet to client");
                    }

                }

            }
        } catch (SocketException ex) {
            System.out.println("Socket error: " + ex.getMessage());
        } catch (SocketTimeoutException ex) {
            System.out.println("Timeout reached, exiting and closing socket!");
            socket.close();
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    /**
     * Gets the quote of the day, each day there is a new quote of the day.
     *
     * @return quote of the day.
     */
    private static String getQuoteOfTheDay() {
        // Set random seed from the current date in format year|month|day concatenated
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd");
        int seed = Integer.parseInt(dateFormat.format(now));
        random.setSeed(seed);
        System.out.println("Getting QOTD from date seed: " + seed);
        int randomIndex = random.nextInt(quotes.size());
        return quotes.get(randomIndex);
    }

    private static void loadQuotes () throws IOException {
        // Read file
        BufferedReader quoteFileReader = new BufferedReader(new FileReader(quotesFile));
        String currQuote;
        while((currQuote = quoteFileReader.readLine()) != null) {
            quotes.add(currQuote);
        }
        quoteFileReader.close();
    }

    /**
     * Concatenates two buffers.
     *
     * @param bufferA First buffer.
     * @param bufferB Second buffer.
     * @return Concatenated buffer of [...bufferA, ...bufferB].
     */
    private static byte[] concatBuffers(byte[] bufferA, byte[] bufferB) {
        int lenA = bufferA.length;
        int lenB = bufferB.length;
        byte[] c = Arrays.copyOf(bufferA, lenA + lenB);
        System.arraycopy(bufferB, 0, c, lenA, lenB);
        return c;
    }

    /**
     * Receives a datagram packet and validates that it's coming from the original client.
     * @param buffer byte buffer.
     * @param originalClientAddress original client address from the first request.
     * @param clientPort original client port from the first request.
     * @throws IOException Exception
     */
    private static void receivePacketAndValidate(byte[] buffer, InetAddress originalClientAddress, int clientPort) throws IOException {
        DatagramPacket packet;
        do {
            packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
        } while (!(packet.getAddress().equals(originalClientAddress) && packet.getPort() == clientPort));
    }
}
