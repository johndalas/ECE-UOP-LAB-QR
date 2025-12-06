package com.ece.uop.labqr;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Arrays;
import java.util.List;

public class ChooseRoleActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "MyPrefs";
    private static final String KEY_FIRST_TIME = "first_time";
    private static final String KEY_USER_CHOICE = "user_choice";
    private static final String KEY_IS_USER_LOGGED_IN = "isUserLoggedIn";
    private static final String TAG = "ChooseRoleActivity";
    private static final int RC_SIGN_IN = 9001;
    private RadioGroup radioGroup;
    private RadioButton radioButtonStudent;
    private RadioButton radioButtonProfessor;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private List<String> allowedEmails = Arrays.asList("ece1815740@go.uop.gr", "sxristod@go.uop.gr");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        databaseReference = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstTime = preferences.getBoolean(KEY_FIRST_TIME, true);
        String userChoice = preferences.getString(KEY_USER_CHOICE, "");

        if (!isFirstTime) {
            if (userChoice.equals("student")) {
                startActivity(new Intent(ChooseRoleActivity.this, MainActivity.class));
            } else if (userChoice.equals("professor")) {
                startActivity(new Intent(ChooseRoleActivity.this, ProfMainActivity.class));
            }
            finish();
            return;
        }
        setContentView(R.layout.activity_choose_role);
        radioGroup = findViewById(R.id.radioGroup);
        radioButtonStudent = findViewById(R.id.radioButtonStudent);
        radioButtonProfessor = findViewById(R.id.radioButtonProfessor);
        Button buttonContinue = findViewById(R.id.buttonContinue);
        buttonContinue.setOnClickListener(v -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(ChooseRoleActivity.this, "Παρακαλώ επιλέξτε την ιδιότητα σας.", Toast.LENGTH_SHORT).show();
            } else if (selectedId == radioButtonStudent.getId()) {

                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(KEY_FIRST_TIME, false);
                editor.putString(KEY_USER_CHOICE, "student");
                editor.apply();

                Intent intent = new Intent(ChooseRoleActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            } else if (selectedId == radioButtonProfessor.getId()) {

                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }

        });
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

                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_FIRST_TIME, true);
                editor.apply();
            }
        }
    }

    private void handleGoogleSignInResult(GoogleSignInAccount account) {
        String email = account.getEmail();
        String displayName = account.getDisplayName();

        if (email != null && allowedEmails.contains(email)) {
            firebaseAuthWithGoogle(account);

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_FIRST_TIME, false);
            editor.putString(KEY_USER_CHOICE, "professor");
            editor.putBoolean(KEY_IS_USER_LOGGED_IN, true);
            editor.apply();

            Log.d(TAG, "Checking for professor in DB: " + displayName);

            // ΕΔΩ κάνουμε τον έλεγχο στη βάση
            String currentSemester = MainActivity.getCurrentSemester();
            String currentAcademicYear = MainActivity.getCurrentAcademicYear();


            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Μαθήματα")
                    .child(currentSemester);

            dbRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    boolean professorFound = false;

                    for (DataSnapshot courseSnapshot : task.getResult().getChildren()) {
                        DataSnapshot yearSnapshot = courseSnapshot.child(currentAcademicYear);
                        if (yearSnapshot.hasChild(displayName)) {
                            professorFound = true;
                            break;
                        }
                    }

                    Intent intent = new Intent(ChooseRoleActivity.this, ProfMainActivity.class);
                    if (professorFound) {
                        Log.d(TAG, "Professor FOUND — will open SwitchButtonFragment");
                        intent.putExtra("showFragment", "switch");
                    } else {
                        Log.d(TAG, "Professor NOT found — will open ProfRegisterFragment");
                        intent.putExtra("showFragment", "register");
                        addProfessorToDatabase(account);
                    }

                    startActivity(intent);
                    finish();
                } else {
                    Log.e(TAG, "Error checking professor in database", task.getException());
                    Toast.makeText(this, "Σφάλμα κατά τον έλεγχο στη βάση.", Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            Toast.makeText(this, "Παρακαλώ συνδεθείτε με τον ακαδημαϊκό σας λογαριασμό.", Toast.LENGTH_SHORT).show();
            mGoogleSignInClient.signOut();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_FIRST_TIME, true);
            editor.apply();
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {

                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {

                        }
                    } else {

                        Log.e(TAG, "Firebase Auth failed: " + task.getException());
                        Toast.makeText(this, "Ανεπιτυχής Σύνδεση.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addProfessorToDatabase(GoogleSignInAccount account) {
        String professorName = account.getDisplayName();
        String professorEmail = account.getEmail();

        if (professorName != null && professorEmail != null) {
            DatabaseReference professorRef = databaseReference.child("Καθηγητές").child(professorName);

            professorRef.child("email").setValue(professorEmail)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Professor added to database.");
                        } else {
                            Log.e(TAG, "Failed to add professor to database.", task.getException());
                        }
                    });
        } else {
            Log.e(TAG, "Professor name or email is null.");
        }
    }
}


