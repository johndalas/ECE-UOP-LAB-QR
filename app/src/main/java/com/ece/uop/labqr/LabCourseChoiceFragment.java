package com.ece.uop.labqr;

import android.content.Context;
import android.content.SharedPreferences;
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
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class LabCourseChoiceFragment extends Fragment {

    private static final String PREFS_NAME = "LabPrefs";

    private Spinner spinner;
    private DatabaseReference databaseReference;
    private Button regattendanceButton;
    private ArrayAdapter<String> spinnerAdapter;
    private String selectedCourse;
    private String userDisplayName;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lab_course_choice, container, false);

        databaseReference = FirebaseDatabase.getInstance().getReference();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        userDisplayName = currentUser.getDisplayName();

        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        spinner = view.findViewById(R.id.spinner);
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        fetchAndSetSpinnerData();

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedCourse = parentView.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });

        regattendanceButton = view.findViewById(R.id.attendance);
        regattendanceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openQrCodeFragment();
            }
        });

        return view;
    }

    private void fetchAndSetSpinnerData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            DatabaseReference coursesReference = databaseReference.child("Μαθήματα");
            String currentSemester = MainActivity.getCurrentSemester();
            String currentAcademicYear = MainActivity.getCurrentAcademicYear();

            coursesReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    spinnerAdapter.clear();

                    for (DataSnapshot semesterSnapshot : dataSnapshot.getChildren()) {
                        String semesterName = semesterSnapshot.getKey();

                        if (semesterName.equals(currentSemester)) {

                            for (DataSnapshot courseSnapshot : semesterSnapshot.getChildren()) {
                                String courseName = courseSnapshot.getKey();

                                for (DataSnapshot academicYearSnapshot : courseSnapshot.getChildren()) {
                                    String academicYear = academicYearSnapshot.getKey();

                                    if (academicYear.equals(currentAcademicYear)) {

                                        for (DataSnapshot teamSnapshot : academicYearSnapshot.getChildren()) {
                                            String teamName = teamSnapshot.getKey();

                                            if (teamSnapshot.hasChild(userDisplayName)) {

                                                spinnerAdapter.add(courseName);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    spinnerAdapter.notifyDataSetChanged();

                    if (spinnerAdapter.getCount() == 0) {
                        regattendanceButton.setEnabled(false);
                        Toast.makeText(requireContext(), "Δεν έχετε εγγραφεί σε κανένα μάθημα.", Toast.LENGTH_SHORT).show();
                    } else {
                        regattendanceButton.setEnabled(true);

                        spinner.setSelection(0);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                    Toast.makeText(requireContext(), "Failed to fetch data", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    private void openQrCodeFragment() {
        selectedCourse = (String) spinner.getSelectedItem();
        QrCodeFragment qrCodeFragment = new QrCodeFragment();

        Bundle args = new Bundle();
        args.putString("selected_course", selectedCourse);
        qrCodeFragment.setArguments(args);

        if (getFragmentManager() != null) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, qrCodeFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
}
