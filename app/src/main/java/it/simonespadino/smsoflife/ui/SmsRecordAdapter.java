package it.simonespadino.smsoflife.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import it.simonespadino.smsoflife.R;
import it.simonespadino.smsoflife.data.SmsRawRecord;
import it.simonespadino.smsoflife.domain.SmsRecordSelection;
import it.simonespadino.smsoflife.domain.SmsRiskAnalyzer;

public final class SmsRecordAdapter extends RecyclerView.Adapter<SmsRecordAdapter.Holder> {
    private boolean selectionEnabled = true;
    private List<SmsRawRecord> rows = Collections.emptyList();
    private final SmsRecordSelection selection;
    private final Runnable onSelectionChanged;
    public SmsRecordAdapter(SmsRecordSelection selection, Runnable onSelectionChanged) {
        this.selection = selection;
        this.onSelectionChanged = onSelectionChanged;
        setHasStableIds(true);
    }
    public void setSelectionEnabled(boolean enabled) {
        if (selectionEnabled != enabled) {
            selectionEnabled = enabled;
            notifyItemRangeChanged(0, getItemCount());
        }
    }
    public void submit(List<SmsRawRecord> rows) { this.rows = rows; notifyDataSetChanged(); }
    @Override public long getItemId(int position) { return rows.get(position).id; }
    @Override public int getItemCount() { return rows.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sms_record, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        SmsRawRecord record = rows.get(position);
        Context context = holder.itemView.getContext();
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setText(context.getString(R.string.record_id, record.id));
        holder.checkbox.setChecked(selection.isSelected(record.id));
        holder.checkbox.setEnabled(selectionEnabled && record.isActive());
        holder.checkbox.setOnCheckedChangeListener((button, checked) -> {
            selection.select(record.id, checked);
            onSelectionChanged.run();
        });
        holder.sender.setText(context.getString(R.string.record_sender,
                record.address == null || record.address.isEmpty() ? context.getString(R.string.unknown) : record.address));
        String date = record.date == null ? context.getString(R.string.unknown)
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(record.date));
        holder.date.setText(context.getString(R.string.record_date, date));
        Integer decoded = SmsRiskAnalyzer.decodedPort(record.destinationPort);
        holder.port.setText(context.getString(R.string.record_port,
                record.destinationPort == null ? context.getString(R.string.unknown) : record.destinationPort.toString(),
                decoded == null ? context.getString(R.string.no_decoded_port) : decoded.toString()));
        holder.deleted.setText(context.getString(R.string.record_deleted,
                record.deleted == null ? context.getString(R.string.unknown) : record.deleted.toString(),
                context.getString(record.deleted == null ? R.string.unknown : record.isActive() ? R.string.active : R.string.deleted)));
        int indicator;
        switch (SmsRiskAnalyzer.classify(record)) {
            case DOCUMENTED_PORT_PATTERN: indicator = R.string.indicator_suspicious; break;
            case WAP_PUSH_PORT: indicator = R.string.indicator_wap; break;
            case UNKNOWN: indicator = R.string.indicator_unknown; break;
            default: indicator = R.string.indicator_none;
        }
        holder.indicator.setText(indicator);
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView sender, date, port, deleted, indicator;
        Holder(View view) {
            super(view);
            checkbox = view.findViewById(R.id.record_checkbox);
            sender = view.findViewById(R.id.record_sender);
            date = view.findViewById(R.id.record_date);
            port = view.findViewById(R.id.record_port);
            deleted = view.findViewById(R.id.record_deleted);
            indicator = view.findViewById(R.id.record_indicator);
        }
    }
}
