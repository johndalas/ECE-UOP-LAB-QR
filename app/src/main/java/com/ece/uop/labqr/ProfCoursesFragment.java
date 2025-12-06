package com.ece.uop.labqr;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ProfCoursesFragment extends Fragment {
    private ListView coursesListView;
    private ArrayAdapter<String> adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_prof_courses, container, false);

        coursesListView = rootView.findViewById(R.id.coursesListView);
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1);
        coursesListView.setAdapter(adapter);

        showProfessorCourses();

        return rootView;
    }

    private void showProfessorCourses() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String userName = user.getDisplayName();
            String currentAcademicYear = MainActivity.getCurrentAcademicYear();
            String currentSemester = MainActivity.getCurrentSemester();
            DatabaseReference coursesRef = FirebaseDatabase.getInstance().getReference().child("Μαθήματα").child(currentSemester);

            coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot semesterSnapshot) {
                    List<String> userCoursesList = new ArrayList<>();

                    for (DataSnapshot courseSnapshot : semesterSnapshot.getChildren()) {

                        if (courseSnapshot.hasChild(currentAcademicYear)) {
                            DataSnapshot academicYearSnapshot = courseSnapshot.child(currentAcademicYear);

                            if (academicYearSnapshot.hasChild(userName)) {
                                String courseName = courseSnapshot.getKey();

                                userCoursesList.add(courseName);
                            }
                        }
                    }

                    if (!userCoursesList.isEmpty()) {

                        adapter.addAll(userCoursesList);
                    } else {

                        Toast.makeText(requireContext(), "Δεν έχετε εγγραφεί σε κάποιο μάθημα για το τρέχον εξάμηνο.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }
    }
}
