public enum PACKET_TYPES {
    SYN((byte) 0),
    SYNACK((byte)1),
    REQUEST((byte)2),
    DATA((byte)3),
    ACK((byte)4),
    FIN((byte)5);

    private final byte value;

    PACKET_TYPES(final byte newValue) {
        value = newValue;
    }

    public byte value() { return value; }
}
