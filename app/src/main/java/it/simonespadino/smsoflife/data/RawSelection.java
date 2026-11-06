package it.simonespadino.smsoflife.data;

import java.util.ArrayList;
import java.util.List;

/** Only fixed column names and placeholders enter SQL. Values are always bound separately. */
public final class RawSelection {
    public final String sql;
    private final String[] args;
    private RawSelection(String sql, String[] args) { this.sql = sql; this.args = args.clone(); }
    public String[] args() { return args.clone(); }
    public static RawSelection id(long id) {
        if (id < 0) throw new IllegalArgumentException("Invalid record ID");
        return new RawSelection("_id = ?", new String[]{Long.toString(id)});
    }
    public static RawSelection unchangedActive(SmsRawRecord record) {
        if (record.id < 0 || !record.isActive()) throw new IllegalArgumentException("Inactive record");
        StringBuilder sql = new StringBuilder("_id = ? AND deleted = ?");
        List<String> args = new ArrayList<>();
        args.add(Long.toString(record.id)); args.add("0");
        append(sql, args, "address", record.address);
        append(sql, args, "destination_port", record.destinationPort);
        append(sql, args, "date", record.date);
        return new RawSelection(sql.toString(), args.toArray(new String[0]));
    }
    private static void append(StringBuilder sql, List<String> args, String column, Object value) {
        sql.append(" AND ").append(column);
        if (value == null) sql.append(" IS NULL");
        else { sql.append(" = ?"); args.add(value.toString()); }
    }
}
