package com.ece.uop.labqr;

import android.app.AlertDialog;
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
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Map;

public class CoursesManagementFragment extends Fragment {

    private Spinner spinner;
    private DatabaseReference databaseReference;
    private Button changeTeamButton;
    private Button removeButton;
    private ArrayAdapter<String> spinnerAdapter;
    private RadioGroup radioGroup;
    private String selectedCourse;
    private String userDisplayName;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_courses_management, container, false);

        databaseReference = FirebaseDatabase.getInstance().getReference();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        userDisplayName = currentUser.getDisplayName();

        spinner = view.findViewById(R.id.spinner);
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        fetchAndSetSpinnerData();

        changeTeamButton = view.findViewById(R.id.change_team);
        removeButton = view.findViewById(R.id.remove);
        removeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRemoveButtonClick();
            }
        });

        radioGroup = view.findViewById(R.id.radioGroup);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                updateRadioButtonsForSelectedCourse();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });



        changeTeamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChangeTeamButtonClick();
            }
        });
        return view;
    }

    private void onRemoveButtonClick() {

        if (selectedCourse != null) {

            int selectedRadioButtonId = radioGroup.getCheckedRadioButtonId();
            if (selectedRadioButtonId != -1) {

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userDisplayName = currentUser.getDisplayName();
                    String academicYear = MainActivity.getCurrentAcademicYear();

                    RadioButton selectedRadioButton = radioGroup.findViewById(selectedRadioButtonId);
                    String selectedTeam = selectedRadioButton.getText().toString();

                    checkIfUserRegisteredForTeam(selectedCourse, academicYear, selectedTeam);
                }
            } else {

                Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε ομάδα.", Toast.LENGTH_SHORT).show();
            }
        } else {

            Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε μάθημα.", Toast.LENGTH_SHORT).show();
        }
    }


    private void checkIfUserRegisteredForTeam(String course, String academicYear, String selectedTeam) {
        DatabaseReference semesterReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester());
        DatabaseReference courseReference = semesterReference.child(course);
        DatabaseReference academicYearReference = courseReference.child(academicYear);
        DatabaseReference teamReference = academicYearReference.child(selectedTeam).child(userDisplayName);

        teamReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {

                    showConfirmationDialog(course, academicYear, selectedTeam);
                } else {

                    Toast.makeText(requireContext(), "Δεν έχετε εγγραφεί σε αυτήν την ομάδα. Επιλέξτε την ομάδα σας.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    private void showConfirmationDialog(String course, String academicYear, String selectedTeam) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Επιβεβαίωση Απεγγραφής")
                .setMessage("Θέλετε σίγουρα να διαφραφείτε απο την " + selectedTeam + " του μαθήματος " + course + ";")
                .setPositiveButton("ΝΑΙ", (dialog, which) -> {

                    removeUserInformation(course, academicYear, selectedTeam);
                })
                .setNegativeButton("ΟΧΙ", (dialog, which) -> {

                })
                .show();
    }


    private void removeUserInformation(String course, String academicYear, String selectedTeam) {
        DatabaseReference semesterReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester());
        DatabaseReference courseReference = semesterReference.child(course);
        DatabaseReference academicYearReference = courseReference.child(academicYear);
        DatabaseReference teamReference = academicYearReference.child(selectedTeam);

        teamReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                long childrenCount = snapshot.getChildrenCount();

                if (childrenCount == 1) {
                    teamReference.setValue(true).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), "Η απεγγραφή ήταν επιτυχής.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Αποτυχία διαγραφής", Toast.LENGTH_SHORT).show();
                        }
                        fetchAndSetSpinnerData();
                        updateRadioButtonsForSelectedCourse();
                    });
                } else {
                    teamReference.child(userDisplayName).removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), "Η απεγγραφή ήταν επιτυχής.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Αποτυχία διαγραφής", Toast.LENGTH_SHORT).show();
                        }
                        fetchAndSetSpinnerData();
                        updateRadioButtonsForSelectedCourse();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void updateRadioButtonsForSelectedCourse() {

        radioGroup.removeAllViews();

        Object selectedItem = spinner.getSelectedItem();
        if (selectedItem != null) {
            selectedCourse = selectedItem.toString();

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

                    checkIfUserRegisteredForTeamInBothSemesters(selectedCourse, MainActivity.getCurrentAcademicYear(), greekAlphabet[i] + " Ομάδα", radioButton);
                } else {

                }
            }

            changeTeamButton.setEnabled(true);
            removeButton.setEnabled(true);

        } else {

            Toast.makeText(requireContext(), "Δεν έχετε εγγραφεί σε κανένα μάθημα.", Toast.LENGTH_SHORT).show();

            changeTeamButton.setEnabled(false);
            removeButton.setEnabled(false);
        }
    }

    private void checkIfUserRegisteredForTeamInBothSemesters(String course, String academicYear, String team, RadioButton radioButton) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            String[] semesters = {"Εαρινό Εξάμηνο", "Χειμερινό Εξάμηνο"};

            for (String semester : semesters) {
                DatabaseReference teamReference = databaseReference.child("Μαθήματα").child(semester).child(course)
                        .child(academicYear).child(team).child(userDisplayName);

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




    private void onChangeTeamButtonClick() {

        if (selectedCourse != null) {

            int selectedRadioButtonId = radioGroup.getCheckedRadioButtonId();
            if (selectedRadioButtonId != -1) {

                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userDisplayName = currentUser.getDisplayName();
                    String academicYear = MainActivity.getCurrentAcademicYear();

                    RadioButton selectedRadioButton = radioGroup.findViewById(selectedRadioButtonId);
                    String selectedTeam = selectedRadioButton.getText().toString();

                    checkIfUserIsInSelectedTeam(selectedCourse, academicYear, selectedTeam, isUserInCurrentTeam -> {
                        if (isUserInCurrentTeam) {

                            Toast.makeText(requireContext(), "Έχετε εγγραφεί ήδη σε αυτή την ομάδα.", Toast.LENGTH_SHORT).show();
                        } else {

                            showChangeTeamConfirmationDialog(selectedCourse, academicYear, selectedTeam);
                        }
                    });
                }
            } else {

                Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε ομάδα.", Toast.LENGTH_SHORT).show();
            }
        } else {

            Toast.makeText(requireContext(), "Παρακαλώ διαλέξτε μάθημα.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkIfUserIsInSelectedTeam(String course, String academicYear, String selectedTeam, OnTeamCheckListener listener) {
        DatabaseReference teamReference = databaseReference.child("Μαθήματα").child(MainActivity.getCurrentSemester())
                .child(course).child(academicYear).child(selectedTeam).child(userDisplayName);

        teamReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                listener.onCheck(snapshot.exists());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

                Toast.makeText(requireContext(), "Failed to check team status", Toast.LENGTH_SHORT).show();
                listener.onCheck(false);
            }
        });
    }

    private interface OnTeamCheckListener {
        void onCheck(boolean isUserInCurrentTeam);
    }


    private void showChangeTeamConfirmationDialog(String course, String academicYear, String selectedTeam) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Επιβεβαίωση Αλλαγής Ομάδας")
                .setMessage("Θέλετε σίγουρα να αλλάξετε στην " + selectedTeam + " του μαθήματος " + course + ";")
                .setPositiveButton("ΝΑΙ", (dialog, which) -> {

                    transferUserInformation(course, academicYear, selectedTeam);
                })
                .setNegativeButton("ΟΧΙ", (dialog, which) -> {

                })
                .show();
    }

    private void transferUserInformation(String course, String academicYear, String newTeam) {
        DatabaseReference selectedCourseReference = databaseReference.child("Μαθήματα")
                .child(MainActivity.getCurrentSemester()).child(course);
        DatabaseReference academicYearReference = selectedCourseReference.child(academicYear);

        academicYearReference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {

                String oldTeamName = dataSnapshot.getKey();

                if (!oldTeamName.equals(newTeam) && dataSnapshot.hasChild(userDisplayName)) {

                    DatabaseReference oldTeamUserRef = academicYearReference.child(oldTeamName).child(userDisplayName);
                    DatabaseReference newTeamUserRef = academicYearReference.child(newTeam).child(userDisplayName);
                    DatabaseReference oldTeamRef = academicYearReference.child(oldTeamName);

                    oldTeamUserRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                            if (!dataSnapshot.exists()) return;

                            Map<String, Object> userData = (Map<String, Object>) dataSnapshot.getValue();

                            newTeamUserRef.updateChildren(userData).addOnCompleteListener(task -> {

                                if (task.isSuccessful()) {

                                    oldTeamUserRef.removeValue().addOnCompleteListener(task1 -> {

                                        if (task1.isSuccessful()) {

                                            oldTeamRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot oldTeamSnap) {
                                                    if (!oldTeamSnap.hasChildren()) {
                                                        oldTeamRef.setValue(true);
                                                    }
                                                }

                                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                                            });
                                            Toast.makeText(requireContext(), "Αλλάξατε ομάδα επιτυχώς.", Toast.LENGTH_SHORT).show();
                                            updateRadioButtonsForSelectedCourse();

                                        } else {
                                            Toast.makeText(requireContext(), "Failed to remove user from old team", Toast.LENGTH_SHORT).show();
                                        }
                                    });

                                } else {
                                    Toast.makeText(requireContext(), "Failed to add user to new team", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            Toast.makeText(requireContext(), "Failed to transfer user information", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(requireContext(), "Failed to listen for team changes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndSetSpinnerData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            String academicYear = MainActivity.getCurrentAcademicYear();
            String currentSemester = MainActivity.getCurrentSemester();
            DatabaseReference coursesReference = databaseReference.child("Μαθήματα").child(currentSemester);

            coursesReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    spinnerAdapter.clear();

                    for (DataSnapshot courseSnapshot : dataSnapshot.getChildren()) {
                        String courseName = courseSnapshot.getKey();

                        DataSnapshot academicYearSnapshot = courseSnapshot.child(academicYear);
                        if (academicYearSnapshot.exists()) {

                            for (DataSnapshot teamSnapshot : academicYearSnapshot.getChildren()) {
                                String teamName = teamSnapshot.getKey();

                                if (teamSnapshot.hasChild(userDisplayName)) {

                                    spinnerAdapter.add(courseName);
                                    break;
                                }
                            }
                        }
                    }

                    spinnerAdapter.notifyDataSetChanged();
                    if (spinnerAdapter.getCount() > 0) {
                        spinner.setSelection(0);
                    }

                    updateRadioButtonsForSelectedCourse();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                    Toast.makeText(requireContext(), "Failed to fetch data", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}