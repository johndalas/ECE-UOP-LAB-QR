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

public class MyCoursesFragment extends Fragment {
    private ListView coursesListView;
    private ArrayAdapter<String> adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_my_courses, container, false);

        coursesListView = rootView.findViewById(R.id.coursesListView);
        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1);
        coursesListView.setAdapter(adapter);

        showRegisteredCoursesAndTeams();

        return rootView;
    }

    private void showRegisteredCoursesAndTeams() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            DatabaseReference coursesRef = FirebaseDatabase.getInstance().getReference().child("Μαθήματα");

            coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    List<String> coursesAndTeamsList = new ArrayList<>();

                    String currentSemester = MainActivity.getCurrentSemester();
                    if (dataSnapshot.hasChild(currentSemester)) {
                        DataSnapshot semesterSnapshot = dataSnapshot.child(currentSemester);

                        for (DataSnapshot courseSnapshot : semesterSnapshot.getChildren()) {
                            String courseName = courseSnapshot.getKey();

                            if (courseSnapshot.hasChild(MainActivity.getCurrentAcademicYear())) {

                                for (DataSnapshot teamSnapshot : courseSnapshot.child(MainActivity.getCurrentAcademicYear()).getChildren()) {
                                    String teamName = teamSnapshot.getKey();

                                    if (teamSnapshot.hasChild(user.getDisplayName())) {

                                        StringBuilder courseAndTeamInfo = new StringBuilder();
                                        courseAndTeamInfo.append(courseName).append(": \n \u2022").append(teamName).append("\n");

                                        int attendances = 0;
                                        DataSnapshot userSnapshot = teamSnapshot.child(user.getDisplayName());
                                        if (userSnapshot.hasChild("Παρουσίες")) {
                                            attendances = userSnapshot.child("Παρουσίες").getValue(Integer.class);
                                        }

                                        courseAndTeamInfo.append(" \u2022Παρουσίες: ").append(attendances);

                                        if (userSnapshot.hasChild("Ώρα")) {
                                            String time = userSnapshot.child("Ώρα").getValue(String.class);
                                            courseAndTeamInfo.append("\n \u2022Ώρα: ").append(time);
                                        }

                                        if (userSnapshot.hasChild("Θέση")) {
                                            String seat = userSnapshot.child("Θέση").getValue(String.class);
                                            courseAndTeamInfo.append("\n \u2022Θέση: ").append(seat);
                                        }

                                        if (userSnapshot.hasChild("Ημερομηνία")) {
                                            String date = userSnapshot.child("Ημερομηνία").getValue(String.class);
                                            courseAndTeamInfo.append("\n \u2022Ημερομηνία: ").append(date);
                                        }

                                        coursesAndTeamsList.add(courseAndTeamInfo.toString());
                                    }
                                }
                            }
                        }
                    }

                    if (!coursesAndTeamsList.isEmpty()) {

                        adapter.addAll(coursesAndTeamsList);
                    } else {

                        Toast.makeText(requireContext(), "Δεν έχετε εγγραφεί σε κάποιο μάθημα.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
        }
    }

}
