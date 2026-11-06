package it.simonespadino.smsoflife.data;

/** Metadata only. Never holds message bodies or PDUs. Nullable database values stay nullable. */
public final class SmsRawRecord {
    public final long id;
    public final String address;
    public final Long destinationPort;
    public final Long date;
    public final Integer deleted;

    public SmsRawRecord(long id, String address, Long destinationPort, Long date, Integer deleted) {
        this.id = id;
        this.address = address;
        this.destinationPort = destinationPort;
        this.date = date;
        this.deleted = deleted;
    }

    public boolean sameMetadata(SmsRawRecord other) {
        return other != null && id == other.id && java.util.Objects.equals(address, other.address)
                && java.util.Objects.equals(destinationPort, other.destinationPort)
                && java.util.Objects.equals(date, other.date);
    }

    public boolean isActive() { return Integer.valueOf(0).equals(deleted); }
}
