package it.simonespadino.smsoflife.data;

public final class DeleteResult {
    public enum Status { RETURNED, ACCESS_DENIED, UNSUPPORTED, DATABASE_ERROR, PROVIDER_ERROR }
    public final Status status;
    public final int count;
    private DeleteResult(Status status, int count) { this.status = status; this.count = count; }
    public static DeleteResult returned(int count) { return new DeleteResult(Status.RETURNED, count); }
    public static DeleteResult error(Status status) { return new DeleteResult(status, 0); }
}
