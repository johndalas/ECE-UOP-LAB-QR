package com.ece.uop.labqr;

import static com.ece.uop.labqr.MainActivity.getCurrentAcademicYear;
import static com.ece.uop.labqr.MainActivity.getCurrentSemester;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.util.Log;


public class ProfMainActivity extends AppCompatActivity implements MenuAdapter.OnItemClickListener {
    private RecyclerView sideMenuRecyclerView;
    private MenuAdapter menuAdapter;
    private List<MenuItem> menuItems;
    private FirebaseAuth mAuth;
    private static final String TAG = "ProfMainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prof_main);

        mAuth = FirebaseAuth.getInstance();

        sideMenuRecyclerView = findViewById(R.id.side_menu);

        menuItems = new ArrayList<>();
        menuItems.add(new MenuItem(R.drawable.ic_register, ""));
        menuItems.add(new MenuItem(R.drawable.ic_courses, ""));
        menuItems.add(new MenuItem(R.drawable.ic_qr, ""));

        menuAdapter = new MenuAdapter(menuItems, this);
        sideMenuRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sideMenuRecyclerView.setAdapter(menuAdapter);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            checkIfProfessorExists(currentUser);
        } else if (savedInstanceState == null) {
            replaceFragment(new ProfRegisterFragment());
        }
    }
    private void checkIfProfessorExists(FirebaseUser user) {
        if (user == null) return;

        String displayName = user.getDisplayName();
        String currentSemester = getCurrentSemester();
        String currentYear = getCurrentAcademicYear();

        Log.d(TAG, "Checking for professor (displayName from Auth): " + displayName);

        DatabaseReference semesterRef = FirebaseDatabase.getInstance()
                .getReference("Μαθήματα")
                .child(currentSemester);

        semesterRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;

                for (DataSnapshot courseSnap : snapshot.getChildren()) {
                    DataSnapshot yearSnap = courseSnap.child(currentYear);
                    if (yearSnap.exists()) {
                        for (DataSnapshot profSnap : yearSnap.getChildren()) {
                            String name = profSnap.getKey();
                            if (name != null && name.trim().equalsIgnoreCase(displayName.trim())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (found) break;
                }

                if (found) {
                    Log.d(TAG, "Professor FOUND — showing SwitchButtonFragment");
                    replaceFragment(new SwitchButtonFragment());
                } else {
                    Log.d(TAG, "Professor NOT found — showing ProfRegisterFragment");
                    replaceFragment(new ProfRegisterFragment());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking professor", error.toException());
            }
        });
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.container, fragment);
        fragmentTransaction.commit();
    }

    @Override
    public void onItemClick(int position) {
        Fragment fragment = null;
        switch (position) {
            case 0:
                fragment = new ProfRegisterFragment();
                break;
            case 1:
                fragment = new ProfCoursesFragment();
                break;
            case 2:
                fragment = new SwitchButtonFragment();
                break;
        }
        if (fragment != null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.container, fragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }
}