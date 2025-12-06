package com.ece.uop.labqr;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class QrCodeFragment extends Fragment {

    private DecoratedBarcodeView barcodeView;
    private List<String> validCodes = Arrays.asList("Η101Θ1", "Η101Θ2", "Η101Θ3", "Η101Θ4", "Η101Θ5",
                                                    "Η101Θ6", "Η101Θ7", "Η101Θ8", "Η101Θ9", "Η101Θ10",
                                                    "Η101Θ11", "Η101Θ12", "Η101Θ13", "Η101Θ14", "Η101Θ15",
                                                    "Η101Θ16", "Η101Θ17", "Η101Θ18", "Η101Θ19", "Η101Θ20");
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private boolean isTorchOn = false;
    private static final double BUILDING_LATITUDE = 38.21799;
    private static final double BUILDING_LONGITUDE = 21.74978;
    private static final float BUILDING_RADIUS = 50;
    private boolean isInvalidCodeToastShown = false;
    private boolean isCodeScanned = false;
    private String selectedCourse;
    private boolean switchState;
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;
    private String userDisplayName;
    private boolean isAttendanceBlocked;

    private static final String SHARED_PREFS = "sharedPrefs";
    private static final String SWITCH_STATE = "switchState";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_qr_code, container, false);
        barcodeView = view.findViewById(R.id.barcode_scanner);
        List<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.initializeFromIntent(getActivity().getIntent());
        barcodeView.setStatusText("Παρακαλώ σκανάρετε έναν QR κωδικό.");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        userDisplayName = currentUser.getDisplayName();

        databaseReference = FirebaseDatabase.getInstance().getReference("Μαθήματα");
        auth = FirebaseAuth.getInstance();

        if (checkPermissions() && isLocationEnabled()) {
            if (isInBuilding()) {
                initializeScanner();
            } else {
                Toast.makeText(getContext(), "Δεν είστε εντός του κτιρίου.", Toast.LENGTH_SHORT).show();
            }
        } else {
            requestPermissions();
        }

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                handleResult(result);
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
            }
        });

        ImageButton backButton = view.findViewById(R.id.back_button);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackButtonClick();
            }
        });

        ImageButton torchButton = view.findViewById(R.id.torch_button);
        torchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTorch();
            }
        });

        SharedPreferences prefs = requireContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        switchState = prefs.getBoolean(SWITCH_STATE, false);

        Bundle args = getArguments();
        if (args != null) {
            selectedCourse = args.getString("selected_course");
            //Toast.makeText(getContext(), "Selected course: " + selectedCourse, Toast.LENGTH_SHORT).show();
        }

        return view;
    }


    private void onBackButtonClick() {
        if (getFragmentManager() != null) {
            getFragmentManager().popBackStack();
        }
    }

    private boolean checkPermissions() {
        int cameraPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA);
        int locationPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION);
        return cameraPermission == PackageManager.PERMISSION_GRANTED && locationPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        String[] permissions = {Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION};
        requestPermissions(permissions, REQUEST_CAMERA_PERMISSION);
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void showLocationSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Ενεργοποίηση Υπηρεσιών Τοποθεσίας");
        builder.setMessage("Οι υπηρεσίες τοποθεσίας δεν είναι ενεργοποιημένες. Θέλετε να τις ενεργοποιήσετε;");
        builder.setPositiveButton("Ναι", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Όχι", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private boolean isInBuilding() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        try {
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                float distance = lastKnownLocation.distanceTo(getBuildingLocation());
                return distance <= BUILDING_RADIUS;
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return false;
    }

    private Location getBuildingLocation() {
        Location location = new Location("");
        location.setLatitude(BUILDING_LATITUDE);
        location.setLongitude(BUILDING_LONGITUDE);
        return location;
    }

    private void initializeScanner() {
        List<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.initializeFromIntent(getActivity().getIntent());
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                handleResult(result);
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
            }
        });
        barcodeView.resume();
    }

    private void handleResult(BarcodeResult result) {
        if (!isCodeScanned) {
            String scannedText = result.getText();

            if (isAttendanceBlocked) {
                if (!isInvalidCodeToastShown) {
                    Toast.makeText(getActivity(), "Η λειτουργία δήλωση παρουσίας δεν είναι ενεργή αυτή τη στιγμή.", Toast.LENGTH_SHORT).show();
                    isInvalidCodeToastShown = true;
                }
                barcodeView.resume();
                return;
            }

            if (isValidCode(scannedText)) {
                isCodeScanned = true;
                checkSwitchStateAndRegister(scannedText);
            } else {
                if (!isInvalidCodeToastShown) {
                    Toast.makeText(getActivity(), "Μη έγκυρος κωδικός QR. Παρακαλώ σαρώστε ξανά.", Toast.LENGTH_SHORT).show();
                    isInvalidCodeToastShown = true;
                }
                barcodeView.resume();
            }
        }
    }

    private void checkSwitchStateAndRegister(String scannedText) {
        if (selectedCourse != null) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                String userId = user.getUid();
                MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity != null) {
                    String academicYear = mainActivity.getCurrentAcademicYear();
                    String currentSemester = mainActivity.getCurrentSemester();

                    DatabaseReference switchReference = databaseReference
                            .child(currentSemester)
                            .child(selectedCourse)
                            .child(academicYear)
                            .child("Διακόπτης");

                    switchReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String switchValue = snapshot.getValue(String.class);
                                if ("ON".equalsIgnoreCase(switchValue)) {

                                    incrementAttendance(scannedText);
                                } else {

                                    Toast.makeText(getActivity(), "Δεν μπορείτε να δηλώσετε παρουσία αυτή την στιγμή.", Toast.LENGTH_SHORT).show();
                                    barcodeView.resume();
                                }
                            } else {
                                Toast.makeText(getActivity(), "Δεν υπάρχει εγγαγραμμένος καθηγητής για αυτό το μάθημα.", Toast.LENGTH_SHORT).show();
                                barcodeView.resume();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(getActivity(), "Failed to check switch state.", Toast.LENGTH_SHORT).show();
                            barcodeView.resume();
                        }
                    });
                }
            }
        }
    }

    private boolean isValidCode(String scannedText) {
        return validCodes.contains(scannedText);
    }

    private void incrementAttendance(String scannedText) {
        if (selectedCourse != null) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                String userId = user.getUid();
                MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity != null) {
                    String academicYear = mainActivity.getCurrentAcademicYear();
                    String currentSemester = mainActivity.getCurrentSemester();

                    DatabaseReference userCourseReference = databaseReference.child(currentSemester)
                            .child(selectedCourse)
                            .child(academicYear);

                    TimeZone greeceTZ = TimeZone.getTimeZone("Europe/Athens");

                    String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) {{
                        setTimeZone(greeceTZ);
                    }}.format(new Date());

                    String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()) {{
                        setTimeZone(greeceTZ);
                    }}.format(new Date());

                    userCourseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean userFound = false;
                            for (DataSnapshot teamSnapshot : snapshot.getChildren()) {
                                if (teamSnapshot.hasChild(userDisplayName)) {

                                    DatabaseReference userTeamReference = teamSnapshot.getRef().child(userDisplayName);
                                    userTeamReference.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                            if (userSnapshot.exists()) {
                                                String amNumber = userSnapshot.child("am").getValue(String.class);

                                                // Λαμβάνουμε την τελευταία ώρα και ημερομηνία
                                                String lastDate = userSnapshot.child("Ημερομηνία").getValue(String.class);
                                                String lastTime = userSnapshot.child("Ώρα").getValue(String.class);

                                                boolean canRegister = true;

                                                if (lastDate != null && lastTime != null) {
                                                    try {
                                                        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
                                                        sdf.setTimeZone(greeceTZ);
                                                        Date lastDateTime = sdf.parse(lastDate + " " + lastTime);
                                                        Date now = new Date();

                                                        long diffSeconds = (now.getTime() - lastDateTime.getTime()) / 1000;

                                                        if (diffSeconds < 7200) {
                                                            canRegister = false;
                                                        }
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                                if (!canRegister) {
                                                    Toast.makeText(getActivity(), "Έχετε ήδη δηλώσει παρουσία για αυτό το μάθημα σήμερα.", Toast.LENGTH_SHORT).show();
                                                    barcodeView.resume();
                                                    return;
                                                }

                                                // Αν μπορεί να δηλώσει, καταγράφουμε την παρουσία
                                                DatabaseReference attendanceRef = userTeamReference.child("Παρουσίες");
                                                attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                                    @Override
                                                    public void onDataChange(@NonNull DataSnapshot attendanceSnapshot) {
                                                        Long currentAttendance = attendanceSnapshot.getValue(Long.class);
                                                        if (currentAttendance != null) {
                                                            attendanceRef.setValue(currentAttendance + 1);
                                                        } else {
                                                            attendanceRef.setValue(1L);
                                                        }

                                                        userTeamReference.child("Ημερομηνία").setValue(currentDate);
                                                        userTeamReference.child("Ώρα").setValue(currentTime);
                                                        userTeamReference.child("Θέση").setValue(scannedText);

                                                        sendDataToGoogleSheets(selectedCourse, teamSnapshot, userId, scannedText, academicYear, amNumber);

                                                        Toast.makeText(getActivity(), "Δηλώσατε παρουσία επιτυχώς.", Toast.LENGTH_SHORT).show();
                                                        barcodeView.resume();
                                                    }

                                                    @Override
                                                    public void onCancelled(@NonNull DatabaseError error) {
                                                        Toast.makeText(getActivity(), "Failed to record attendance.", Toast.LENGTH_SHORT).show();
                                                        barcodeView.resume();
                                                    }
                                                });

                                            } else {
                                                Toast.makeText(getActivity(), "User data not found.", Toast.LENGTH_SHORT).show();
                                                barcodeView.resume();
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Toast.makeText(getActivity(), "Failed to fetch user data.", Toast.LENGTH_SHORT).show();
                                            barcodeView.resume();
                                        }
                                    });

                                    userFound = true;
                                    break;
                                }
                            }

                            if (!userFound) {
                                Toast.makeText(getActivity(), "User not found in any team.", Toast.LENGTH_SHORT).show();
                                barcodeView.resume();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(getActivity(), "Failed to fetch teams.", Toast.LENGTH_SHORT).show();
                            barcodeView.resume();
                        }
                    });
                }
            }
        }
    }

    private void sendDataToGoogleSheets(String course, DataSnapshot teamSnapshot, String userId, String seat, String academicYear, String amNumber) {
        String url = getAppScriptUrl(course);

        String teamName = teamSnapshot.getKey();
        String firstLetter = teamName.substring(0, 1);

        StringBuilder teamData = new StringBuilder();
        for (DataSnapshot member : teamSnapshot.getChildren()) {
            teamData.append(member.getKey()).append(":").append(member.getValue()).append("\n");
        }

        new Thread(() -> {
            try {
                URL appScriptUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) appScriptUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("teamName", firstLetter);
                json.put("seat", seat);
                json.put("academicYear", academicYear);
                json.put("amNumber", amNumber);
                json.put("username", userDisplayName);
                json.put("teamData", teamData.toString());

                OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes());
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {

                } else {

                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getAppScriptUrl(String course) {
        switch (course) {
            case "ΕΕ-Αναγνώριση Προτύπων":
                return "https://script.google.com/macros/s/AKfycbzKHJ3AS7zeYYJg8-uHYVJiGpvco6b_YeRL13nD1BPrdgOgnrpFzr-HaqDZWwr7o2EEDg/exec";
            case "ΕΕ-Αναλογικά Ηλεκτρονικά Κυκλώματα":
                return "https://script.google.com/macros/s/AKfycbwLYjzeUmj-8M7D3PNffDVV16KBjUS9PmG_74XUvvWEZA-ceOTDpNWI69FgYD30zcvv/exec";
            case "ΧΕ-Δίκτυα Υπολογιστών":
                return "https://script.google.com/macros/s/AKfycbztYVIOr6WjdBnjkZfIHK2jfJa136ru63Re89q3ISOmuihk7-uzgjRHT_I5omGaJewVuQ/exec";
            case "ΧΕ-Τεχνικές Προγραμματισμού Υπολογιστών":
                return " ";
            case "ΧΕ-Ηλεκτρικά Κυκλώματα Ι":
                return " ";
            case "ΧΕ-Δομές Δεδομένων και Αλγόριθμοι":
                return " ";
            case "ΧΕ-Σήματα και Συστήματα":
                return " ";
            case "ΧΕ-Ψηφιακές Επικοινωνίες":
                return " ";
            case "ΧΕ-Λειτουργικά Συστήματα":
                return " ";
            case "ΧΕ-Συστήματα Μέτρησης και Αισθητήρες":
                return " ";
            case "ΧΕ-Συστήματα Αυτομάτου Ελέγχου":
                return " ";
            case "ΧΕ-Ηλεκτρονικά Ισχύος Ι":
                return " ";
            case "ΧΕ-Ηλεκτρικές Μηχανές ΙΙ":
                return " ";
            case "ΧΕ-Ηλεκτρικές Εγκαταστάσεις Ισχύος":
                return " ";
            case "ΧΕ-Μικροελεγκτές":
                return " ";
            case "ΧΕ-Προηγμένα Συστήματα Αυτομάτου Ελέγχου":
                return " ";
            case "ΧΕ-Ανάπτυξη Συστημάτων Παγκοσμίου Ιστού":
                return " ";
            case "ΧΕ-Συστήματα Διαχείρισης Δεδομένων":
                return " ";
            case "ΧΕ-Ψηφιακή Επεξεργασία Εικόνας":
                return " ";
            case "ΧΕ-Οπτικά Δίκτυα Επικοινωνιών":
                return " ";
            case "ΧΕ-Προγραμματιζόμενος Έλεγχος και PLCs":
                return " ";
            case "ΧΕ-Σχεδιασμός Ψηφιακών Συστημάτων σε FPGAs":
                return " ";
            case "ΧΕ-Προηγμένα Μικροποϋπολογιστικά Συστήματα":
                return " ";
            case "ΧΕ-Παράλληλα Συστήματα και Προγραμματισμός":
                return " ";
            case "ΧΕ-Ανάπτυξη Λογισμικού σε Φορητές Συσκευές":
                return "https://script.google.com/macros/s/AKfycbzN2qNkIEa74S3oQb411CAMZf129fyPKQeMSY5iCoL0QumsDN516_jYr-hGPagiVJFDSg/exec";
            case "ΧΕ-Γραφικά Υπολογιστών":
                return " ";
            case "ΧΕ-Αδόμητα και Ασύρματα Δίκτυα Αισθητήρων":
                return " ";
            case "ΧΕ-Βιομηχανικά Δίκτυα":
                return " ";
            case "ΕΕ-Ηλεκτρικά Κυκλώματα ΙΙ":
                return " ";
            case "ΕΕ-Εισαγωγή στις Βάσεις Δεδομένων":
                return " ";
            case "ΕΕ-Διαδικασιακός Προγραμματισμός":
                return " ";
            case "ΕΕ-Αρχές Τηλεπικοινωνιακών Συστημάτων":
                return " ";
            case "ΕΕ-Αντικειμενοστραφής Σχεδίαση και Προγραμματισμός":
                return " ";
            case "ΕΕ-Ψηφιακά Κυκλώματα και Συστήματα":
                return " ";
            case "ΕΕ-Ηλεκτρικές Μηχανές Ι":
                return " ";
            case "ΕΕ-Υπολογιστικές Μέθοδοι για Μηχανικούς":
                return " ";
            case "ΕΕ-Μικροϋπολογιστικά Συστήματα":
                return " ";
            case "ΕΕ-Εσωτερικές Ηλεκτρικές Εγκαταστάσεις & Αυτοματισμοί":
                return " ";
            case "ΕΕ-Ψηφιακή Επεξεργασία Σημάτων":
                return " ";
            case "ΕΕ-Ηλεκτρολογικό Σχέδιο":
                return " ";
            case "ΕΕ-Ηλεκτρονικά Ισχύος ΙΙ":
                return " ";
            case "ΕΕ-Τεχνολογία Φωτισμού":
                return " ";
            case "ΕΕ-Γλώσσες Περιγραφής Υλικού (HDL)":
                return " ";
            case "ΕΕ-Προηγμένες Τεχνολογίες Παγκοσμίου Ιστού":
                return " ";
            case "ΕΕ-Πληροφοριακά Συστήματα Εξόρυξης Δεδομένων και Επιχειρησιακή Ευφυία":
                return " ";
            case "ΕΕ-Ασύρματη Διάδοση και Κεραίες":
                return " ";
            case "ΕΕ-Προσομοίωση Δικτύων":
                return " ";
            case "ΕΕ-Θεωρία Πληροφορίας":
                return " ";
            case "ΕΕ-Επεξεργασία Ήχου και Μουσικής":
                return " ";
            default:
                return "https://script.google.com/macros/s/DefaultAppScript/exec";
        }
    }


    private void toggleTorch() {
        if (barcodeView != null && isInBuilding()) {
            isTorchOn = !isTorchOn;
            setTorchState(isTorchOn);
        } else {
            String toastMessage = "Ο σαρωτής QR δεν είναι διαθέσιμος.";
            Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void setTorchState(boolean state) {
        if (state) {
            barcodeView.setTorchOn();
        } else {
            barcodeView.setTorchOff();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        setTorchState(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean cameraPermissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean locationPermissionGranted = grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED;

            if (cameraPermissionGranted && locationPermissionGranted && isLocationEnabled() && isInBuilding()) {
                initializeScanner();
            } else {
                String toastMessage;
                if (!cameraPermissionGranted || !locationPermissionGranted) {
                    toastMessage = "Άδειες κάμερας και/ή τοποθεσίας απορρίφθηκαν. Ο σαρωτής QR δεν είναι διαθέσιμος.";
                    Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_SHORT).show();
                } else if (!isLocationEnabled()) {
                    toastMessage = "Οι υπηρεσίες τοποθεσίας δεν είναι ενεργοποιημένες. Παρακαλούμε ενεργοποιήστε τις για να χρησιμοποιήσετε το σαρωτή QR.";
                    showLocationSettingsDialog();
                    Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (checkPermissions() && isLocationEnabled()) {
            if (isInBuilding()) {
                barcodeView.resume();
            } else {
                String toastMessage = "Δεν βρίσκεστε εντός του κτιρίου. Παρακαλούμε μετακινηθείτε στη σωστή τοποθεσία.";
                Toast.makeText(getActivity(), toastMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        barcodeView.pause();
    }
}
