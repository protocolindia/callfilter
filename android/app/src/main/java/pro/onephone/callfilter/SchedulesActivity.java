package pro.onephone.callfilter;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class SchedulesActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView emptyView;
    private ScheduleManager schedules;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedules);

        schedules = ScheduleManager.getInstance(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        listContainer = findViewById(R.id.schedulesList);
        emptyView     = findViewById(R.id.emptyView);

        findViewById(R.id.btnAddSchedule).setOnClickListener(v ->
            startActivity(new Intent(SchedulesActivity.this, EditScheduleActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        List<Schedule> all = schedules.getAll();
        listContainer.removeAllViews();
        if (all.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            listContainer.setVisibility(View.GONE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        listContainer.setVisibility(View.VISIBLE);

        long now = System.currentTimeMillis();
        Schedule activeNow = schedules.getActiveSchedule(now);

        LayoutInflater inf = LayoutInflater.from(this);
        for (final Schedule s : all) {
            final View tile = inf.inflate(R.layout.schedule_tile, listContainer, false);

            TextView nameView   = tile.findViewById(R.id.scheduleName);
            TextView windowView = tile.findViewById(R.id.scheduleWindow);
            TextView daysView   = tile.findViewById(R.id.scheduleDays);
            TextView allowView  = tile.findViewById(R.id.scheduleAllow);
            TextView statusView = tile.findViewById(R.id.scheduleStatus);
            Switch enableSwitch = tile.findViewById(R.id.scheduleSwitch);
            Button quickBtn     = tile.findViewById(R.id.btnQuickActivate);

            nameView.setText(s.name.isEmpty() ? "Untitled" : s.name);
            windowView.setText(s.formatWindow());
            daysView.setText(s.formatDays());

            int allowCount = s.allowNumbers.size();
            allowView.setText(allowCount == 0
                ? "No exceptions — blocks everyone"
                : (allowCount == 1 ? "1 contact allowed" : allowCount + " contacts allowed"));

            boolean isActive = activeNow != null && activeNow.clientId.equals(s.clientId);
            if (isActive) {
                if (s.quickUntilMs > now) {
                    statusView.setText("⚡ ACTIVE \u00B7 ends "
                        + DateUtils.getRelativeTimeSpanString(s.quickUntilMs, now, DateUtils.MINUTE_IN_MILLIS));
                } else {
                    statusView.setText("ACTIVE NOW");
                }
                statusView.setVisibility(View.VISIBLE);
            } else {
                statusView.setVisibility(View.GONE);
            }

            enableSwitch.setOnCheckedChangeListener(null);
            enableSwitch.setChecked(s.isEnabled);
            enableSwitch.setOnCheckedChangeListener((b, checked) -> {
                schedules.toggleEnabled(s.clientId);
                refreshList();
            });

            tile.setOnClickListener(v -> {
                Intent i = new Intent(SchedulesActivity.this, EditScheduleActivity.class);
                i.putExtra("clientId", s.clientId);
                startActivity(i);
            });

            tile.setOnLongClickListener(v -> {
                new AlertDialog.Builder(SchedulesActivity.this)
                    .setTitle("Delete schedule?")
                    .setMessage("Delete \"" + s.name + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        schedules.delete(s.clientId);
                        refreshList();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });

            quickBtn.setOnClickListener(v -> showQuickActivateDialog(s));

            listContainer.addView(tile);
        }
    }

    private void showQuickActivateDialog(final Schedule s) {
        final long now = System.currentTimeMillis();
        if (s.quickUntilMs > now) {
            new AlertDialog.Builder(this)
                .setTitle("Cancel quick activation?")
                .setMessage("\"" + s.name + "\" is currently quick-activated.")
                .setPositiveButton("Cancel it", (d, w) -> {
                    schedules.cancelQuickActivation(s.clientId);
                    refreshList();
                })
                .setNegativeButton("Keep", null)
                .show();
            return;
        }

        final int[] options = { 30, 60, 120, 240 };
        final String[] labels = { "30 minutes", "1 hour", "2 hours", "4 hours" };

        new AlertDialog.Builder(this)
            .setTitle("Activate \"" + s.name + "\" for")
            .setItems(labels, (d, which) -> {
                schedules.quickActivate(s.clientId, options[which]);
                Toast.makeText(SchedulesActivity.this,
                    "\"" + s.name + "\" active for " + labels[which],
                    Toast.LENGTH_SHORT).show();
                refreshList();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
