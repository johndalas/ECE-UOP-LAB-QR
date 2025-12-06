package com.ece.uop.labqr;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        MainActivity.createCoursesNode(rootRef);

        new Handler().postDelayed(() -> {
            navigateToChooseRoleActivity();
        }, 1000);
    }
    private void navigateToChooseRoleActivity() {
        Intent intent = new Intent(WelcomeActivity.this, ChooseRoleActivity.class);
        startActivity(intent);
        finish();
    }
}
