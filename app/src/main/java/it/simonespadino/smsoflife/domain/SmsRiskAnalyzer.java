package it.simonespadino.smsoflife.domain;

import it.simonespadino.smsoflife.data.SmsRawRecord;

public final class SmsRiskAnalyzer {
    public enum Indicator { DOCUMENTED_PORT_PATTERN, WAP_PUSH_PORT, NONE, UNKNOWN }
    private SmsRiskAnalyzer() { }

    /** AOSP InboundSmsTracker encoding, interpreted locally without invoking hidden APIs. */
    public static Integer decodedPort(Long stored) {
        if (stored == null || stored < 0 || stored > 0xFFFFF || (stored & 0x10000) != 0) return null;
        return (int) (stored & 0xFFFF);
    }
    public static Indicator classify(SmsRawRecord record) {
        if (record.destinationPort == null || record.destinationPort < 0
                || record.destinationPort > 0xFFFFF) return Indicator.UNKNOWN;
        if (record.destinationPort == 134020L) return Indicator.DOCUMENTED_PORT_PATTERN;
        Integer port = decodedPort(record.destinationPort);
        if (Integer.valueOf(2948).equals(port)) return Indicator.WAP_PUSH_PORT;
        return Indicator.NONE;
    }
    public static boolean isSuspicious(SmsRawRecord record) {
        // A match is metadata evidence only. Ordinary WAP Push messages can match too.
        return classify(record) == Indicator.DOCUMENTED_PORT_PATTERN;
    }
}
