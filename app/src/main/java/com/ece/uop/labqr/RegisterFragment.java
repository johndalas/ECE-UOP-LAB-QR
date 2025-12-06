package com.ece.uop.labqr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
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

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class RegisterFragment extends Fragment {

    private Spinner spinner;
    private DatabaseReference databaseReference;
    private Button registerButton;
    private ArrayAdapter<String> spinnerAdapter;
    private RadioGroup radioGroup;
    private String selectedCourse;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseAuth firebaseAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register, container, false);

        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        spinner = view.findViewById(R.id.spinner);
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        registerButton = view.findViewById(R.id.register);
        radioGroup = view.findViewById(R.id.radioGroup);

        authStateListener = firebaseAuth -> {
            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null) {
                disableViews();
            } else {
                enableViews();
                fetchAndSetSpinnerData();

                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                        radioGroup.removeAllViews();
                        selectedCourse = spinner.getSelectedItem().toString();

                        int numberOfTeams = ((MainActivity) requireActivity()).getNumberOfTeamsForCourse(selectedCourse);

                        String[] greekAlphabet = {
                                "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ", "Μ", "Ν", "Ξ", "Ο", "Π",
                                "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω"
                        };

                        for (int i = 0; i < numberOfTeams; i++) {

                            if (i < greekAlphabet.length) {
                                RadioButton radioButton = new RadioButton(requireContext());
                                radioButton.setText(greekAlphabet[i] + " Ομάδα");
                                radioGroup.addView(radioButton);

                                checkIfUserRegisteredForTeam(selectedCourse, MainActivity.getCurrentAcademicYear(), greekAlphabet[i] + " Ομάδα", radioButton);
                            }
                        }
                        checkIfUserRegisteredForAnyTeam(selectedCourse, MainActivity.getCurrentAcademicYear());
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parentView) {
                    }
                });

                registerButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onRegisterButtonClick();
                    }
                });
            }
        };

        firebaseAuth.addAuthStateListener(authStateListener);

        return view;
    }

    private void enableViews() {
        spinner.setEnabled(true);
        radioGroup.setEnabled(true);
        registerButton.setEnabled(true);
    }

    private void disableViews() {
        spinner.setEnabled(false);
        radioGroup.setEnabled(false);
        registerButton.setEnabled(false);
    }

    private void checkIfUserRegisteredForAnyTeam(String course, String academicYear) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null) {
                DatabaseReference courseReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester()).child(course).child(academicYear);

                courseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        boolean isRegistered = false;
                        for (DataSnapshot teamSnapshot : dataSnapshot.getChildren()) {
                            if (teamSnapshot.hasChild(displayName)) {
                                isRegistered = true;
                                break;
                            }
                        }
                        registerButton.setEnabled(!isRegistered);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            }
        }
    }

    private void checkIfUserRegisteredForTeam(String course, String academicYear, String team, RadioButton radioButton) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null) {
                DatabaseReference teamReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester()).child(course)
                        .child(academicYear).child(team).child(displayName);

                teamReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {

                            radioButton.setChecked(true);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            }
        }
    }

    private void onRegisterButtonClick() {
        if (selectedCourse != null) {
            int selectedRadioButtonId = radioGroup.getCheckedRadioButtonId();
            if (selectedRadioButtonId != -1) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    String displayName = currentUser.getDisplayName();
                    if (displayName != null) {
                        DatabaseReference studentsReference = databaseReference.child("Φοιτητές").child(displayName);
                        studentsReference.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    User user = dataSnapshot.getValue(User.class);
                                    String academicYear = MainActivity.getCurrentAcademicYear();

                                    DatabaseReference selectedCourseReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester()).child(selectedCourse);
                                    DatabaseReference academicYearReference = selectedCourseReference.child(academicYear);

                                    RadioButton selectedRadioButton = radioGroup.findViewById(selectedRadioButtonId);
                                    if (selectedRadioButton != null) {
                                        String selectedTeam = selectedRadioButton.getText().toString();
                                        DatabaseReference teamReference = academicYearReference.child(selectedTeam);

                                        teamReference.child(displayName).addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                                if (dataSnapshot.exists()) {

                                                    Toast.makeText(requireContext(), "Έχετε ήδη εγγραφεί σε αυτή την ομάδα.", Toast.LENGTH_SHORT).show();
                                                } else {

                                                    checkIfUserRegisteredForOtherTeam(selectedCourse, academicYear, displayName, selectedTeam, user);
                                                }
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError databaseError) {

                                                Toast.makeText(requireContext(), "Failed to check registration status", Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                        registerButton.setEnabled(false);
                                    } else {
                                        Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε ομάδα.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {

                                Toast.makeText(requireContext(), "Failed to retrieve user info", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            } else {

                Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε ομάδα.", Toast.LENGTH_SHORT).show();
            }
        } else {

            Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε μάθημα.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveUserInformation(String course, String academicYear, String displayName, String selectedTeam, User user) {
        DatabaseReference semesterReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester());
        DatabaseReference courseReference = semesterReference.child(course);
        DatabaseReference academicYearReference = courseReference.child(academicYear);
        DatabaseReference teamReference = academicYearReference.child(selectedTeam).child(displayName);

        teamReference.setValue(user);

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("Παρουσίες", 0);
        additionalInfo.put("Ημερομηνία", "");
        additionalInfo.put("Ώρα", "");
        additionalInfo.put("Θέση", "");

        teamReference.updateChildren(additionalInfo);

        Toast.makeText(requireContext(), "Επιτυχής εγγραφή.", Toast.LENGTH_SHORT).show();
    }

    private void checkIfUserRegisteredForOtherTeam(String course, String academicYear, String displayName, String selectedTeam, User user) {
        DatabaseReference teamReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester()).child(course)
                .child(academicYear);

        teamReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                boolean isUserRegisteredForOtherTeam = false;
                for (DataSnapshot teamSnapshot : dataSnapshot.getChildren()) {
                    if (!teamSnapshot.getKey().equals(selectedTeam) && teamSnapshot.hasChild(displayName)) {

                        isUserRegisteredForOtherTeam = true;
                        break;
                    }
                }

                if (isUserRegisteredForOtherTeam) {

                } else {

                    saveUserInformation(course, academicYear, displayName, selectedTeam, user);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

                Toast.makeText(requireContext(), "Failed to check registration status", Toast.LENGTH_SHORT).show();
            }
        });
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
    }

}
