package pro.onephone.callfilter;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

/**
 * Add / edit a single schedule.
 *
 * Receives optional intent extra "clientId" to edit an existing schedule;
 * absence means "create new."
 */
public class EditScheduleActivity extends AppCompatActivity {

    public static final int REQ_PICK_CONTACTS = 4001;

    private Schedule editing;
    private boolean isNew;

    private EditText nameInput;
    private TextView startTimeView, endTimeView, daysSummary, allowSummary;
    private ToggleButton[] dayButtons = new ToggleButton[7];
    private Switch enabledSwitch;
    private TextView title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);

        // Load existing or create new
        String clientId = getIntent().getStringExtra("clientId");
        if (clientId != null) {
            editing = ScheduleManager.getInstance(this).getByClientId(clientId);
        }
        if (editing == null) {
            editing = new Schedule();
            editing.name = "Sleep";
            isNew = true;
        } else {
            isNew = false;
        }

        title = findViewById(R.id.editScheduleTitle);
        title.setText(isNew ? "New schedule" : "Edit schedule");

        ImageButton back = findViewById(R.id.btnBack);
        back.setOnClickListener(v -> finish());

        nameInput     = findViewById(R.id.scheduleNameInput);
        startTimeView = findViewById(R.id.startTimeView);
        endTimeView   = findViewById(R.id.endTimeView);
        daysSummary   = findViewById(R.id.daysSummary);
        allowSummary  = findViewById(R.id.allowSummary);
        enabledSwitch = findViewById(R.id.enabledSwitch);

        dayButtons[0] = findViewById(R.id.day0);  // Sun
        dayButtons[1] = findViewById(R.id.day1);
        dayButtons[2] = findViewById(R.id.day2);
        dayButtons[3] = findViewById(R.id.day3);
        dayButtons[4] = findViewById(R.id.day4);
        dayButtons[5] = findViewById(R.id.day5);
        dayButtons[6] = findViewById(R.id.day6);

        // Populate fields
        nameInput.setText(editing.name);
        renderTime();
        for (int i = 0; i < 7; i++) {
            dayButtons[i].setChecked((editing.daysMask & (1 << i)) != 0);
        }
        enabledSwitch.setChecked(editing.isEnabled);
        renderAllowSummary();

        // Time pickers
        startTimeView.setOnClickListener(v -> pickTime(true));
        endTimeView.setOnClickListener(v -> pickTime(false));

        // Day toggles
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            dayButtons[i].setOnCheckedChangeListener((b, checked) -> {
                if (checked) editing.daysMask |= (1 << idx);
                else         editing.daysMask &= ~(1 << idx);
                daysSummary.setText(editing.formatDays());
            });
        }
        daysSummary.setText(editing.formatDays());

        // Allowlist picker
        findViewById(R.id.pickContactsCard).setOnClickListener(v -> {
            Intent i = new Intent(EditScheduleActivity.this, ContactPickerActivity.class);
            i.putStringArrayListExtra("selected_numbers", new ArrayList<>(editing.allowNumbers));
            i.putStringArrayListExtra("selected_names",   new ArrayList<>(editing.allowNames));
            startActivityForResult(i, REQ_PICK_CONTACTS);
        });

        // Save / delete buttons
        Button saveBtn = findViewById(R.id.btnSaveSchedule);
        saveBtn.setOnClickListener(v -> saveSchedule());

        Button deleteBtn = findViewById(R.id.btnDeleteSchedule);
        if (isNew) {
            deleteBtn.setVisibility(View.GONE);
        } else {
            deleteBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Delete schedule?")
                    .setMessage("Delete \"" + editing.name + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        ScheduleManager.getInstance(this).delete(editing.clientId);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }
    }

    private void renderTime() {
        startTimeView.setText(String.format("%02d:%02d",
            editing.startMinute / 60, editing.startMinute % 60));
        endTimeView.setText(String.format("%02d:%02d",
            editing.endMinute / 60, editing.endMinute % 60));
    }

    private void renderAllowSummary() {
        int count = editing.allowNumbers.size();
        if (count == 0) {
            allowSummary.setText("No exceptions — every caller is blocked while active");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(count).append(count == 1 ? " contact: " : " contacts: ");
            int show = Math.min(3, editing.allowNames.size());
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(editing.allowNames.get(i));
            }
            if (count > show) sb.append(", \u2026");
            allowSummary.setText(sb.toString());
        }
    }

    private void pickTime(final boolean isStart) {
        int cur = isStart ? editing.startMinute : editing.endMinute;
        TimePickerDialog tpd = new TimePickerDialog(this,
            (view, hour, minute) -> {
                int val = hour * 60 + minute;
                if (isStart) editing.startMinute = val;
                else         editing.endMinute = val;
                renderTime();
            }, cur / 60, cur % 60, true);
        tpd.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_CONTACTS && resultCode == RESULT_OK && data != null) {
            ArrayList<String> nums   = data.getStringArrayListExtra("selected_numbers");
            ArrayList<String> names  = data.getStringArrayListExtra("selected_names");
            if (nums != null)  editing.allowNumbers = new ArrayList<>(nums);
            if (names != null) editing.allowNames = new ArrayList<>(names);
            renderAllowSummary();
        }
    }

    private void saveSchedule() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }
        editing.name = name;
        editing.isEnabled = enabledSwitch.isChecked();
        // daysMask already updated as user toggles
        ScheduleManager.getInstance(this).save(editing);
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
