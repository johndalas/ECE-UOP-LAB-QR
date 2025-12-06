package com.ece.uop.labqr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ProfRegisterFragment extends Fragment {

    private Spinner spinner;
    private DatabaseReference databaseReference;
    private Button registerButton;
    private Button unregisterButton;
    private ArrayAdapter<String> spinnerAdapter;
    private String selectedCourse;
    private boolean isRegistered = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_prof_register, container, false);

        databaseReference = FirebaseDatabase.getInstance().getReference();

        spinner = view.findViewById(R.id.spinner);
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        registerButton = view.findViewById(R.id.register);
        unregisterButton = view.findViewById(R.id.unregister);

        unregisterButton.setEnabled(false);

        fetchAndSetSpinnerData();

        registerButton.setOnClickListener(v -> onRegisterButtonClick());
        unregisterButton.setOnClickListener(v -> showUnregisterConfirmationDialog());

        return view;
    }

    private void onRegisterButtonClick() {

        if (selectedCourse != null) {

            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            String displayName = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getDisplayName() : null;

            if (displayName != null) {
                String academicYear = MainActivity.getCurrentAcademicYear();
                String currentSemester = MainActivity.getCurrentSemester();

                DatabaseReference academicYearReference = databaseReference
                        .child("Μαθήματα")
                        .child(currentSemester)
                        .child(selectedCourse)
                        .child(academicYear);

                DatabaseReference userNode = academicYearReference.child(displayName);
                userNode.setValue(true);

                Map<String, Object> additionalInfo = new HashMap<>();
                additionalInfo.put("Διακόπτης", "OFF");
                academicYearReference.updateChildren(additionalInfo);

                isRegistered = true;
                registerButton.setEnabled(false);
                unregisterButton.setEnabled(true);

                Toast.makeText(requireContext(), "Επιτυχής εγγραφή.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Δεν βρέθηκε το όνομα χρήστη.", Toast.LENGTH_SHORT).show();
            }
        } else {

            Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε μάθημα.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showUnregisterConfirmationDialog() {

        new AlertDialog.Builder(requireContext())
                .setTitle("Επιβεβαίωση")
                .setMessage("Θέλετε σίγουρα να διαγραφείτε από το μάθημα;")
                .setPositiveButton("Ναι", (dialog, which) -> onUnregisterButtonClick())
                .setNegativeButton("Όχι", null)
                .show();
    }

    private void onUnregisterButtonClick() {

        if (selectedCourse != null) {

            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            String displayName = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getDisplayName() : null;

            if (displayName != null) {
                String academicYear = MainActivity.getCurrentAcademicYear();
                String currentSemester = MainActivity.getCurrentSemester();

                DatabaseReference academicYearReference = databaseReference
                        .child("Μαθήματα")
                        .child(currentSemester)
                        .child(selectedCourse)
                        .child(academicYear);


                academicYearReference.child(displayName).removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        Toast.makeText(requireContext(), "Απεγγραφή ολοκληρώθηκε.", Toast.LENGTH_SHORT).show();
                        isRegistered = false;
                        registerButton.setEnabled(true);
                        unregisterButton.setEnabled(false);
                    } else {
                        Toast.makeText(requireContext(), "Αποτυχία κατά την αφαίρεση της εγγραφής.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(requireContext(), "Δεν βρέθηκε το όνομα χρήστη.", Toast.LENGTH_SHORT).show();
            }
        } else {

            Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε μάθημα.", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchAndSetSpinnerData() {
        DatabaseReference coursesReference = databaseReference.child("Μαθήματα");

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);

        String currentSemester = (currentMonth >= Calendar.MARCH && currentMonth <= Calendar.JULY) ? "Εαρινό Εξάμηνο" : "Χειμερινό Εξάμηνο";

        coursesReference.child(currentSemester).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                spinnerAdapter.clear();

                for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) {
                    String itemName = childSnapshot.getKey();
                    if (itemName != null) {
                        spinnerAdapter.add(itemName);
                    }
                }

                spinnerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

                Toast.makeText(requireContext(), "Failed to fetch data", Toast.LENGTH_SHORT).show();
            }
        });

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {

                selectedCourse = spinner.getSelectedItem().toString();

                if (selectedCourse != null) {
                    String academicYear = MainActivity.getCurrentAcademicYear();
                    String currentSemester = MainActivity.getCurrentSemester();
                    DatabaseReference academicYearReference = databaseReference
                            .child("Μαθήματα")
                            .child(currentSemester)
                            .child(selectedCourse)
                            .child(academicYear);

                    FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
                    String displayName = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getDisplayName() : null;

                    if (displayName != null) {
                        academicYearReference.child(displayName).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {

                                    isRegistered = true;
                                    registerButton.setEnabled(false);
                                    unregisterButton.setEnabled(true);
                                } else {

                                    isRegistered = false;
                                    registerButton.setEnabled(true);
                                    unregisterButton.setEnabled(false);
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {

                                Toast.makeText(requireContext(), "Failed to check registration status", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });
    }
}
