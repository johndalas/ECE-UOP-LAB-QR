package com.ece.uop.labqr;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MenuAdapter.OnItemClickListener {

    private RecyclerView sideMenuRecyclerView;
    private MenuAdapter menuAdapter;
    private List<MenuItem> menuItems;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private static final int RC_SIGN_IN = 9001;
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_IS_USER_LOGGED_IN = "isUserLoggedIn";
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        sideMenuRecyclerView = findViewById(R.id.side_menu);
        menuItems = new ArrayList<>();
        menuItems.add(new MenuItem(R.drawable.ic_login, ""));
        menuItems.add(new MenuItem(R.drawable.ic_register, ""));
        menuItems.add(new MenuItem(R.drawable.ic_courses, ""));
        menuItems.add(new MenuItem(R.drawable.ic_courses_management, ""));
        menuItems.add(new MenuItem(R.drawable.ic_qr, ""));
        menuItems.add(new MenuItem(R.drawable.ic_logout, ""));

        menuAdapter = new MenuAdapter(menuItems, this);
        sideMenuRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sideMenuRecyclerView.setAdapter(menuAdapter);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isUserLoggedIn = prefs.getBoolean(KEY_IS_USER_LOGGED_IN, false);

        if (!isUserLoggedIn) {
            Toast.makeText(this, "Παρακαλώ συνδεθείτε με τον ακαδημαϊκό σας λογαριασμό.", Toast.LENGTH_SHORT).show();
        }

        menuAdapter.setAllEnabled(isUserLoggedIn);
        menuAdapter.setEnabled(0, !isUserLoggedIn);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkIfStudentIsRegistered();
        } else {
            replaceFragment(new Fragment());
        }

    }
    private void checkIfStudentIsRegistered() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String displayName = user.getDisplayName();
        String currentSemester = getCurrentSemester();
        String currentYear = getCurrentAcademicYear();

        Log.d(TAG, "Checking for student (displayName from Auth): " + displayName);

        DatabaseReference coursesRef = FirebaseDatabase.getInstance()
                .getReference("Μαθήματα")
                .child(currentSemester);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;

                for (DataSnapshot courseSnap : snapshot.getChildren()) {
                    DataSnapshot yearSnap = courseSnap.child(currentYear);
                    if (yearSnap.exists()) {
                        for (DataSnapshot teamSnap : yearSnap.getChildren()) {
                            for (DataSnapshot studentSnap : teamSnap.getChildren()) {
                                String name = studentSnap.getKey();
                                if (name != null && name.trim().equalsIgnoreCase(displayName.trim())) {
                                    found = true;
                                    break;
                                }
                            }
                            if (found) break;
                        }
                    }
                    if (found) break;
                }

                if (found) {
                    Log.d(TAG, "Student FOUND — showing LabCourseChoiceFragment");
                    replaceFragment(new LabCourseChoiceFragment());
                } else {
                    Log.d(TAG, "Student NOT found — showing RegisterFragment");
                    replaceFragment(new RegisterFragment());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking student registration", error.toException());
            }
        });
    }


    @Override
    public void onItemClick(int position) {
        Fragment fragment = null;
        switch (position) {
            case 0:
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
                return;
            case 1:
                fragment = new RegisterFragment();
                break;
            case 2:
                fragment = new MyCoursesFragment();
                break;
            case 3:
                fragment = new CoursesManagementFragment();
                break;
            case 4:
                fragment = new LabCourseChoiceFragment();
                break;
            case 5:
                showLogoutConfirmationDialog();
                return;
        }
        if (fragment != null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.container, fragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                handleGoogleSignInResult(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed: " + e.getMessage());
                Toast.makeText(this, "Η προσπάθεια σύνδεσης απέτυχε. Παρακαλώ προσπαθείστε ξανά.", Toast.LENGTH_SHORT).show();
                mGoogleSignInClient.signOut();
            }
        }
    }

    private void handleGoogleSignInResult(GoogleSignInAccount account) {

        String email = account.getEmail();
        if (isValidAcademicEmail(email)) {
            firebaseAuthWithGoogle(account);

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_IS_USER_LOGGED_IN, true);
            editor.apply();

            Toast.makeText(this, "Επιτυχής Σύνδεση.", Toast.LENGTH_SHORT).show();

            menuAdapter.setAllEnabled(true);
            menuAdapter.setEnabled(0, false);
        } else {
            Toast.makeText(this, "Παρακαλώ συνδεθείτε με τον ακαδημαικό σας λογαριασμό.", Toast.LENGTH_SHORT).show();
            mGoogleSignInClient.signOut();
        }
    }

    private boolean isValidAcademicEmail(String email) {

        String academicDomain = "@go.uop.gr";
        return email != null && email.endsWith(academicDomain);
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToDatabase(user, acct);
                            checkUserCourseRegistration(user);
                        }
                    } else {

                        Log.e(TAG, "Firebase Auth failed: " + task.getException());
                        Toast.makeText(this, "Ανεπιτυχής Σύνδεση.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void checkUserCourseRegistration(FirebaseUser user) {
        if (user == null) return;

        String currentSemester = getCurrentSemester();
        String currentYear = getCurrentAcademicYear();

        DatabaseReference coursesRef = FirebaseDatabase.getInstance()
                .getReference("Μαθήματα")
                .child(currentSemester);

        String displayName = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getDisplayName() : "";

        Log.d(TAG, "Checking for student (displayName from Auth): " + displayName);

        coursesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean found = false;

                for (DataSnapshot courseSnap : snapshot.getChildren()) {
                    DataSnapshot yearSnap = courseSnap.child(currentYear);
                    if (yearSnap.exists()) {
                        for (DataSnapshot teamSnap : yearSnap.getChildren()) {
                            for (DataSnapshot studentSnap : teamSnap.getChildren()) {
                                String name = studentSnap.getKey();
                                if (name != null && name.trim().equalsIgnoreCase(displayName.trim())) {
                                    found = true;
                                    break;
                                }
                            }
                            if (found) break;
                        }
                    }
                    if (found) break;
                }

                if (found) {
                    Log.d(TAG, "Student FOUND — showing LabCourseChoiceFragment");
                    replaceFragment(new LabCourseChoiceFragment());
                } else {
                    Log.d(TAG, "Student NOT found — showing RegisterFragment");
                    replaceFragment(new RegisterFragment());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking student registration", error.toException());
            }
        });

    }
    private void replaceFragment(Fragment fragment) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.container, fragment);
        ft.commit();
    }

    private void saveUserToDatabase(FirebaseUser user, GoogleSignInAccount account) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference userRef = databaseRef.child("Φοιτητές").child(account.getDisplayName());

        User userModel = new User(account.getEmail(), extractAMNumber(account.getEmail()));

        userRef.setValue(userModel)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User data saved to database.");
                    } else {
                        Log.e(TAG, "Failed to save user data: " + task.getException());
                    }
                });
    }

    private String extractAMNumber(String email) {

        if (email != null && email.endsWith("@go.uop.gr") && email.length() >= 6) {
            int atIndex = email.indexOf('@');
            if (atIndex >= 5) {
                return email.substring(atIndex - 5, atIndex);
            }
        }
        return null;
    }

    private void signOut() {

        mAuth.signOut();

        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Toast.makeText(MainActivity.this, "Επιτυχής Αποσύνδεση.", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "Παρακαλώ συνδεθείτε με τον ακαδημαικό σας λογαριασμό.", Toast.LENGTH_SHORT).show();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_IS_USER_LOGGED_IN, false);
            editor.apply();

            menuAdapter.setAllEnabled(false);
            menuAdapter.setEnabled(0, true);

            clearContainer();

        });
    }
    private void clearContainer() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.container, new Fragment());
        transaction.commit();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Αποσύνδεση")
                .setMessage("Θέτελε σίγουρα να αποσυνδεθείτε;")
                .setPositiveButton("Ναί", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        signOut();
                    }
                })
                .setNegativeButton("Όχι", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    public static void createCoursesNode(DatabaseReference databaseReference) {
        DatabaseReference coursesReference = databaseReference.child("Μαθήματα");

        List<String> semesters = Arrays.asList("Χειμερινό Εξάμηνο", "Εαρινό Εξάμηνο");

        for (String semester : semesters) {
            DatabaseReference semesterReference = coursesReference.child(semester);

            List<String> courses = null;

            if (semester.equals("Χειμερινό Εξάμηνο")) {
                courses = Arrays.asList("ΧΕ-Τεχνικές Προγραμματισμού Υπολογιστών", "ΧΕ-Ηλεκτρικά Κυκλώματα Ι",
                        "ΧΕ-Δομές Δεδομένων και Αλγόριθμοι", "ΧΕ-Σήματα και Συστήματα", "ΧΕ-Ψηφιακές Επικοινωνίες",
                        "ΧΕ-Δίκτυα Υπολογιστών", "ΧΕ-Λειτουργικά Συστήματα", "ΧΕ-Συστήματα Μέτρησης και Αισθητήρες",
                        "ΧΕ-Συστήματα Αυτομάτου Ελέγχου", "ΧΕ-Ηλεκτρονικά Ισχύος Ι", "ΧΕ-Ηλεκτρικές Μηχανές ΙΙ",
                        "ΧΕ-Ηλεκτρικές Εγκαταστάσεις Ισχύος", "ΧΕ-Μικροελεγκτές", "ΧΕ-Προηγμένα Συστήματα Αυτομάτου Ελέγχου",
                        "ΧΕ-Ανάπτυξη Συστημάτων Παγκοσμίου Ιστού", "ΧΕ-Συστήματα Διαχείρισης Δεδομένων", "ΧΕ-Ψηφιακή Επεξεργασία Εικόνας",
                        "ΧΕ-Οπτικά Δίκτυα Επικοινωνιών", "ΧΕ-Προγραμματιζόμενος Έλεγχος και PLCs", "ΧΕ-Σχεδιασμός Ψηφιακών Συστημάτων σε FPGAs",
                        "ΧΕ-Προηγμένα Μικροποϋπολογιστικά Συστήματα", "ΧΕ-Παράλληλα Συστήματα και Προγραμματισμός", "ΧΕ-Ανάπτυξη Λογισμικού σε Φορητές Συσκευές",
                        "ΧΕ-Γραφικά Υπολογιστών", "ΧΕ-Αδόμητα και Ασύρματα Δίκτυα Αισθητήρων", "ΧΕ-Βιομηχανικά Δίκτυα");

            } else if (semester.equals("Εαρινό Εξάμηνο")) {
                courses = Arrays.asList("ΕΕ-Ηλεκτρικά Κυκλώματα ΙΙ", "ΕΕ-Εισαγωγή στις Βάσεις Δεδομένων", "ΕΕ-Διαδικασιακός Προγραμματισμός",
                        "ΕΕ-Αρχές Τηλεπικοινωνιακών Συστημάτων", "ΕΕ-Αντικειμενοστραφής Σχεδίαση και Προγραμματισμός", "ΕΕ-Αναλογικά Ηλεκτρονικά Κυκλώματα",
                        "ΕΕ-Ψηφιακά Κυκλώματα και Συστήματα", "ΕΕ-Ηλεκτρικές Μηχανές Ι", "ΕΕ-Υπολογιστικές Μέθοδοι για Μηχανικούς",
                        "ΕΕ-Μικροϋπολογιστικά Συστήματα", "ΕΕ-Εσωτερικές Ηλεκτρικές Εγκαταστάσεις & Αυτοματισμοί", "ΕΕ-Ψηφιακή Επεξεργασία Σημάτων",
                        "ΕΕ-Ηλεκτρολογικό Σχέδιο", "ΕΕ-Ηλεκτρονικά Ισχύος ΙΙ", "ΕΕ-Τεχνολογία Φωτισμού", "ΕΕ-Γλώσσες Περιγραφής Υλικού (HDL)",
                        "ΕΕ-Προηγμένες Τεχνολογίες Παγκοσμίου Ιστού", "ΕΕ-Πληροφοριακά Συστήματα Εξόρυξης Δεδομένων και Επιχειρησιακή Ευφυία",
                        "ΕΕ-Ασύρματη Διάδοση και Κεραίες", "ΕΕ-Αναγνώριση Προτύπων", "ΕΕ-Προσομοίωση Δικτύων", "ΕΕ-Θεωρία Πληροφορίας",
                        "ΕΕ-Επεξεργασία Ήχου και Μουσικής");
            }

            if (courses != null) {
                for (String course : courses) {

                    DatabaseReference courseReference = semesterReference.child(course);
                    courseReference.get().addOnSuccessListener(snapshot -> {

                        if (!snapshot.exists()) {
                            courseReference.setValue(true);
                        }
                        createAcademicYears(courseReference, getNumberOfTeamsForCourse(course));
                    });
                }
            }
        }
    }

    private static void createAcademicYears(DatabaseReference courseReference, int numberOfTeams) {
        List<String> academicYears = Arrays.asList(
                "2023 - 2024",
                "2024 - 2025",
                "2025 - 2026",
                "2026 - 2027",
                "2027 - 2028",
                "2028 - 2029",
                "2029 - 2030");

        for (String academicYear : academicYears) {
            DatabaseReference academicYearReference = courseReference.child(academicYear);

            academicYearReference.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {

                        academicYearReference.setValue(true);
                    }

                    createTeamsNode(academicYearReference, numberOfTeams);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Error checking academic year node existence", error.toException());
                }
            });
        }
    }

    public static void createTeamsNode(DatabaseReference academicYearReference, int numberOfTeams) {

        String[] greekAlphabet = {
                "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ", "Μ", "Ν", "Ξ", "Ο", "Π",
                "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω"
        };

        for (int i = 0; i < numberOfTeams; i++) {

            if (i < greekAlphabet.length) {
                String teamName = greekAlphabet[i] + " Ομάδα";
                DatabaseReference teamReference = academicYearReference.child(teamName);

                teamReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            teamReference.setValue(true);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error checking team node existence", error.toException());
                    }
                });
            } else {
                Log.e(TAG, "Not enough Greek alphabet characters for all teams");
            }
        }
    }





    public static int getNumberOfTeamsForCourse(String courseName) {
        switch (courseName) {
            case "ΕΕ-Διαδικασιακός Προγραμματισμός":
            case "ΕΕ-Ηλεκτρικά Κυκλώματα ΙΙ":
            case "ΕΕ-Εισαγωγή στις Βάσεις Δεδομένων":
            case "ΧΕ-Τεχνικές Προγραμματισμού Υπολογιστών":
            case "ΧΕ-Ηλεκτρικά Κυκλώματα Ι":
                return 12;

            case "ΕΕ-Αρχές Τηλεπικοινωνιακών Συστημάτων":
            case "ΕΕ-Αντικειμενοστραφής Σχεδίαση και Προγραμματισμός":
            case "ΕΕ-Αναλογικά Ηλεκτρονικά Κυκλώματα":
            case "ΕΕ-Ψηφιακά Κυκλώματα και Συστήματα":
            case "ΕΕ-Ηλεκτρικές Μηχανές Ι":
            case "ΧΕ-Δομές Δεδομένων και Αλγόριθμοι":
            case "ΧΕ-Σήματα και Συστήματα":
                return 10;

            case "ΕΕ-Μικροϋπολογιστικά Συστήματα":
            case "ΕΕ-Εσωτερικές Ηλεκτρικές Εγκαταστάσεις & Αυτοματισμοί":
            case "ΕΕ-Ψηφιακή Επεξεργασία Σημάτων":
            case "ΕΕ-Ηλεκτρολογικό Σχέδιο":
            case "ΧΕ-Ψηφιακές Επικοινωνίες":
            case "ΧΕ-Δίκτυα Υπολογιστών":
            case "ΧΕ-Συστήματα Μέτρησης και Αισθητήρες":
            case "ΧΕ-Συστήματα Αυτομάτου Ελέγχου":
            case "ΧΕ-Ηλεκτρονικά Ισχύος Ι":
                return 8;

            case "ΧΕ-Ηλεκτρικές Μηχανές ΙΙ":
            case "ΧΕ-Ηλεκτρικές Εγκαταστάσεις Ισχύος":
            case "ΧΕ-Μικροελεγκτές":
            case "ΧΕ-Προηγμένα Συστήματα Αυτομάτου Ελέγχου":
            case "ΧΕ-Ψηφιακή Επεξεργασία Εικόνας":
            case "ΧΕ-Οπτικά Δίκτυα Επικοινωνιών":
            case "ΕΕ-Ηλεκτρονικά Ισχύος ΙΙ":
            case "ΧΕ-Προγραμματιζόμενος Έλεγχος και PLCs":
            case "ΧΕ-Προηγμένα Μικροποϋπολογιστικά Συστήματα":
                return 4;

            case "ΕΕ-Γλώσσες Περιγραφής Υλικού (HDL)":
                return 2;

            case "ΧΕ-Λειτουργικά Συστήματα":
            case "ΕΕ-Υπολογιστικές Μέθοδοι για Μηχανικούς":
            case "ΧΕ-Ανάπτυξη Συστημάτων Παγκοσμίου Ιστού":
            case "ΧΕ-Συστήματα Διαχείρισης Δεδομένων":
            case "ΕΕ-Τεχνολογία Φωτισμού":
            case "ΕΕ-Προηγμένες Τεχνολογίες Παγκοσμίου Ιστού":
            case "ΕΕ-Πληροφοριακά Συστήματα Εξόρυξης Δεδομένων και Επιχειρησιακή Ευφυία":
            case "ΕΕ-Ασύρματη Διάδοση και Κεραίες":
            case "ΕΕ-Αναγνώριση Προτύπων":
            case "ΕΕ-Προσομοίωση Δικτύων":
            case "ΕΕ-Θεωρία Πληροφορίας":
            case "ΕΕ-Επεξεργασία Ήχου και Μουσικής":
            case "ΧΕ-Σχεδιασμός Ψηφιακών Συστημάτων σε FPGAs":
            case "ΧΕ-Παράλληλα Συστήματα και Προγραμματισμός":
            case "ΧΕ-Ανάπτυξη Λογισμικού σε Φορητές Συσκευές":
            case "ΧΕ-Γραφικά Υπολογιστών":
            case "ΧΕ-Αδόμητα και Ασύρματα Δίκτυα Αισθητήρων":
            case "ΧΕ-Βιομηχανικά Δίκτυα":
                return 1;

            default:
                return 0;
        }
    }

    public static String getCurrentAcademicYear() {
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        int currentMonth = calendar.get(Calendar.MONTH) + 1;

        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        if ((currentMonth < 10) || (currentMonth == 10 && currentDay < 1)) {
            return (currentYear - 1) + " - " + currentYear;
        } else {
            return currentYear + " - " + (currentYear + 1);
        }
    }

    public static String getCurrentSemester() {

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);

        return (currentMonth >= Calendar.MARCH && currentMonth <= Calendar.JULY) ? "Εαρινό Εξάμηνο" : "Χειμερινό Εξάμηνο";
    }
}