package com.ece.uop.labqr;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SwitchButtonFragment extends Fragment {

    private static final String PREFS_NAME = "LabPrefs";
    private static final String SWITCH_STATE_KEY = "switch_state";
    private static final String TIMER_END_TIME_KEY = "timer_end_time";
    private static final String TIME_LEFT_KEY = "time_left";
    private static final String TIMER_RUNNING_KEY = "timer_running";
    private static final String SELECTED_COURSE_KEY = "selected_course";

    private static final long INITIAL_TIME = 1200000;
    private Spinner spinner;
    private Switch switchButton;
    private TextView timerTextView;
    private ImageView refreshImage;
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis = INITIAL_TIME;
    private boolean timerRunning = false;
    private long endTime = 0L;
    private ArrayAdapter<String> spinnerAdapter;
    private String selectedCourse;
    private String userDisplayName;
    private SharedPreferences sharedPreferences;
    private DatabaseReference databaseReference;
    private boolean isSwitchBeingRestored = false;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_switch_button, container, false);

        databaseReference = FirebaseDatabase.getInstance().getReference();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) userDisplayName = currentUser.getDisplayName();

        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        spinner = view.findViewById(R.id.spinner);
        switchButton = view.findViewById(R.id.switchButton);
        timerTextView = view.findViewById(R.id.timer);
        refreshImage = view.findViewById(R.id.refreshImage);

        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        fetchAndSetSpinnerData();

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean firstCall = true;

            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                String newCourse = spinnerAdapter.getItem(position);

                if (newCourse == null) return;

                if (!newCourse.equals(selectedCourse)) {
                    selectedCourse = newCourse;
                }

                saveSelectedCourse();

                if (firstCall) {
                    firstCall = false;
                    new Handler().postDelayed(() -> checkSwitchState(), 50);
                } else {
                    checkSwitchState();
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parentView) {}
        });

        switchButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isSwitchBeingRestored || selectedCourse == null) return;

            if (isChecked && !hasExactAlarmPermission()) {

                if (!hasNotificationPermission()) {
                    isSwitchBeingRestored = true;
                    switchButton.setChecked(false);
                    isSwitchBeingRestored = false;

                    showNotificationPermissionDialog();
                    return;
                }

                if (!hasExactAlarmPermission()) {
                    isSwitchBeingRestored = true;
                    switchButton.setChecked(false);
                    isSwitchBeingRestored = false;

                    showExactAlarmPermissionDialog();
                    return;
                }
            }

            saveSwitchStateToFirebase(isChecked);
            saveLocalSwitchState(selectedCourse, isChecked);

            if (isChecked) {

                restoreTimerForCourse(selectedCourse);
                spinner.setEnabled(false);

                if (timerRunning && timeLeftInMillis > 0) {
                    startTimer();
                } else {
                    timeLeftInMillis = INITIAL_TIME;
                    timerRunning = false;
                    startTimer();
                }

                saveSelectedCourse();

            } else {
                stopTimer();
                spinner.setEnabled(true);
                cancelTimerFinishAlarm(selectedCourse);
                clearTimerStateForCourse(selectedCourse);

                timeLeftInMillis = INITIAL_TIME;
                timerTextView.setText("20:00");

                timerRunning = false;

                removeSavedSelectedCourse();
            }
        });

        refreshImage.setOnClickListener(v -> {
            resetTimer();
            if (selectedCourse != null) saveTimerStateForCourse(selectedCourse);
        });

        updateTimerText();

        return view;
    }

    private void showExactAlarmPermissionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Απαιτείται Άδεια")
                .setMessage("Για να λειτουργήσει ο χρονοδιακόπτης και να απενεργοποιείται αυτόματα, πρέπει να ενεργοποιήσετε την άδεια 'Alarms'.")
                .setPositiveButton("Ρυθμίσεις", (dialog, which) -> {
                    requestExactAlarmPermission();
                })
                .setNegativeButton("Άκυρο", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showNotificationPermissionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Απαιτείται Άδεια")
                .setMessage("Για να λαμβάνετε ειδοποιήσεις όταν ο διακόπτης κλείνει αυτόματα, πρέπει να επιτρέψετε τις ειδοποιήσεις.")
                .setPositiveButton("OK", (dialog, which) -> requestNotificationPermission())
                .setNegativeButton("Άκυρο", (dialog, which) -> dialog.dismiss())
                .show();
    }
    private void fetchAndSetSpinnerData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String currentSemester = MainActivity.getCurrentSemester();
        String currentAcademicYear = MainActivity.getCurrentAcademicYear();

        databaseReference.child("Μαθήματα")
                .child(currentSemester)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot semesterSnapshot) {

                                spinnerAdapter.clear();

                                for (DataSnapshot courseSnapshot : semesterSnapshot.getChildren()) {

                                    if (courseSnapshot.hasChild(currentAcademicYear)) {
                                        if (courseSnapshot.child(currentAcademicYear).hasChild(userDisplayName)) {

                                            spinnerAdapter.add(courseSnapshot.getKey());
                                        }
                                    }
                                }
                                spinnerAdapter.notifyDataSetChanged();

                                if (spinnerAdapter.getCount() > 0) {

                                    String savedCourse =
                                            sharedPreferences.getString(
                                                    SELECTED_COURSE_KEY, null);

                                    if (savedCourse != null) {
                                        int idx = spinnerAdapter.getPosition(savedCourse);

                                        if (idx >= 0) {
                                            selectedCourse = savedCourse;
                                            spinner.setSelection(idx);
                                            checkSwitchState();
                                            return;
                                        }
                                    }

                                    selectedCourse = spinnerAdapter.getItem(0);
                                    spinner.setSelection(0);
                                    checkSwitchState();
                                } else {
                                    switchButton.setEnabled(false);
                                    refreshImage.setEnabled(false);
                                    Toast.makeText(requireContext(), "Δεν έχετε μαθήματα για το τρέχον εξάμηνο.", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(requireContext(), "Σφάλμα φόρτωσης.", Toast.LENGTH_SHORT).show();
                            }
                        });
    }

    private void saveSelectedCourse() {
        if (selectedCourse == null) return;

        if (switchButton.isChecked()) {
            sharedPreferences.edit().putString(SELECTED_COURSE_KEY, selectedCourse).apply();
        } else {
            removeSavedSelectedCourse();
        }
    }
    private void removeSavedSelectedCourse() {
        sharedPreferences.edit().remove(SELECTED_COURSE_KEY).apply();
    }

    private String prefKey(String base, String course) {
        return base + "_" + course;
    }

    private void saveTimerStateForCourse(String course) {
        if (course == null) return;

        sharedPreferences.edit()
                .putLong(prefKey(TIME_LEFT_KEY, course), timeLeftInMillis)
                .putBoolean(prefKey(TIMER_RUNNING_KEY, course), timerRunning)
                .putLong(prefKey(TIMER_END_TIME_KEY, course), endTime)
                .apply();
    }
    private void restoreTimerForCourse(String course) {
        if (course == null) return;

        long savedEnd = sharedPreferences.getLong(prefKey(TIMER_END_TIME_KEY, course), 0L);
        boolean savedRunning = sharedPreferences.getBoolean(prefKey(TIMER_RUNNING_KEY, course), false);
        long savedTimeLeft = sharedPreferences.getLong(prefKey(TIME_LEFT_KEY, course), INITIAL_TIME);

        if (savedRunning && savedEnd > 0) {
            long now = SystemClock.elapsedRealtime();
            long remain = savedEnd - now;

            if (remain > 0) {
                timeLeftInMillis = remain;
                timerRunning = true;
                endTime = savedEnd;
            } else {
                timeLeftInMillis = INITIAL_TIME;
                timerRunning = false;
                endTime = 0;
                clearTimerStateForCourse(course);
            }
        } else {
            timeLeftInMillis = savedTimeLeft;
            timerRunning = savedRunning;
            endTime = savedEnd;
        }
    }
    private void clearTimerStateForCourse(String course) {
        if (course == null) return;

        sharedPreferences.edit()
                .remove(prefKey(TIME_LEFT_KEY, course))
                .remove(prefKey(TIMER_RUNNING_KEY, course))
                .remove(prefKey(TIMER_END_TIME_KEY, course))
                .apply();

        timeLeftInMillis = INITIAL_TIME;
        timerRunning = false;
        endTime = 0;
    }

    private void scheduleTimerFinishAlarm(long finishTime, String course) {

        if (course == null) return;

        Intent intent = new Intent(requireContext(), com.ece.uop.labqr.TimerExpireReceiver.class);
        intent.putExtra("course", course);
        intent.putExtra("semester", MainActivity.getCurrentSemester());
        intent.putExtra("academicYear", MainActivity.getCurrentAcademicYear());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                course.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                finishTime,
                pendingIntent
        );
    }
    private void cancelTimerFinishAlarm(String course) {
        if (course == null) return;

        Intent intent = new Intent(requireContext(), com.ece.uop.labqr.TimerExpireReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                course.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }
    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            startActivity(intent);
        }
    }

    private boolean hasExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        return alarmManager.canScheduleExactAlarms();
    }

    private void startTimer() {

        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        if (timeLeftInMillis <= 0 || timeLeftInMillis > INITIAL_TIME) {
            timeLeftInMillis = INITIAL_TIME;
        }

        endTime = SystemClock.elapsedRealtime() + timeLeftInMillis;

        long finishTimeForAlarm = System.currentTimeMillis() + timeLeftInMillis;
        if (!hasExactAlarmPermission()) {
            requestExactAlarmPermission();
            return;
        }

        scheduleTimerFinishAlarm(finishTimeForAlarm, selectedCourse);

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                timeLeftInMillis = 0;
                updateTimerText();

                isSwitchBeingRestored = true;
                switchButton.setChecked(false);
                isSwitchBeingRestored = false;

                if (selectedCourse != null) {
                    saveSwitchStateToFirebase(false);
                    clearTimerStateForCourse(selectedCourse);
                    cancelTimerFinishAlarm(selectedCourse);
                }

                timerTextView.setText("20:00");
            }
        }.start();

        timerRunning = true;
        if (selectedCourse != null) saveTimerStateForCourse(selectedCourse);
    }

    private void stopTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        timerRunning = false;
        updateTimerText();

        if (selectedCourse != null) saveTimerStateForCourse(selectedCourse);
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        timeLeftInMillis = INITIAL_TIME;
        timerRunning = false;
        endTime = 0;

        if (switchButton.isChecked()) startTimer();
        else timerTextView.setText("20:00");

        if (selectedCourse != null) saveTimerStateForCourse(selectedCourse);
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;

        timerTextView.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void checkSwitchState() {
        if (selectedCourse == null) return;

        String currentSemester = MainActivity.getCurrentSemester();
        String currentAcademicYear = MainActivity.getCurrentAcademicYear();

        databaseReference.child("Μαθήματα")
                .child(currentSemester)
                .child(selectedCourse)
                .child(currentAcademicYear)
                .child("Διακόπτης")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {

                        String state = snapshot.getValue(String.class);
                        boolean isChecked = "ON".equals(state);
                        spinner.setEnabled(!isChecked);

                        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

                        boolean autoOff = prefs.getBoolean("autoSwitchOffHappened", false);
                        String autoOffCourse = prefs.getString("autoSwitchOffCourse", null);
                        boolean appWasOpen = prefs.getBoolean("appWasOpen", false);

                        if (autoOff && !isChecked && selectedCourse.equals(autoOffCourse) && !appWasOpen) {

                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Ενημέρωση")
                                    .setMessage("Ο διακόπτης για το μάθημα \"" + selectedCourse + "\" έκλεισε αυτόματα λόγω λήξης του χρόνου.")
                                    .setPositiveButton("OK", null)
                                    .show();

                            prefs.edit().putBoolean("autoSwitchOffHappened", false).apply();
                            prefs.edit().putString("autoSwitchOffCourse", null).apply();
                        }

                        isSwitchBeingRestored = true;
                        switchButton.setChecked(isChecked);
                        isSwitchBeingRestored = false;

                        if (!isChecked) {

                            stopTimer();
                            cancelTimerFinishAlarm(selectedCourse);
                            clearTimerStateForCourse(selectedCourse);

                            timeLeftInMillis = INITIAL_TIME;
                            timerTextView.setText("20:00");

                            timerRunning = false;

                            removeSavedSelectedCourse();

                        } else {

                            restoreTimerForCourse(selectedCourse);

                            if (timerRunning && timeLeftInMillis > 0) {
                                startTimer();
                            } else {
                                updateTimerText();
                            }

                            saveSelectedCourse();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return requireContext().checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    2001
            );
        }
    }

    private void saveSwitchStateToFirebase(boolean isChecked) {
        if (selectedCourse == null) return;

        databaseReference
                .child("Μαθήματα")
                .child(MainActivity.getCurrentSemester())
                .child(selectedCourse)
                .child(MainActivity.getCurrentAcademicYear())
                .child("Διακόπτης")
                .setValue(isChecked ? "ON" : "OFF");
    }

    private void saveLocalSwitchState(String course, boolean isChecked) {
        sharedPreferences.edit().putBoolean(prefKey(SWITCH_STATE_KEY, course), isChecked).apply();
    }

    @Override
    public void onStart() {
        super.onStart();
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("appIsCurrentlyOpen", true).apply();

    }

    @Override
    public void onStop() {
        super.onStop();

        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("appWasOpen", false).apply();
        prefs.edit().putBoolean("appIsCurrentlyOpen", false).apply();


        if (selectedCourse != null) {
            saveTimerStateForCourse(selectedCourse);
            saveSelectedCourse();
            saveLocalSwitchState(selectedCourse, switchButton.isChecked());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }
}
