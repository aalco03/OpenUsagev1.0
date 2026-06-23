package com.openusage.app.Activity;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.util.HashMap;
import java.util.Map;

import com.openusage.app.Alarm.Pref;
import com.openusage.app.DatabaseHelper.LogInPreference;
import com.openusage.app.FirebaseSettings.FirebaseManagerSingleton;
import com.openusage.app.TextBasedEventData.EventDatabaseHelper;
import com.openusage.app.TextBasedEventData.EventMapBuilder;
import com.openusage.app.PermissionScreens.PermissionChecker;
import com.openusage.app.PermissionScreens.PermissionParentActivity;
import com.openusage.app.R;
import com.openusage.app.Services.CaptureUploadService;
import com.openusage.app.TextBasedEventData.EventUploaderToFireStore;
import com.openusage.app.Utils;
import com.openusage.app.specs.SpecsInfo;
import eventreporter.EventTimestamp;

/**
 * @author Jack Boffa
 * Login screen where the user can create an account or login with code/email/password.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = LoginActivity.class.getName();

    // Instance variable to hold the password restriction
    private String PASSWORD_RESTRICTION;

    // UI references.
    private EditText mCodeView;
    private View mProgressView;
    private View mLoginFormView;

    Button signin;
    public static boolean IsUserWantLoginOrRegister = false;

    // Login async task.
    Task<AuthResult> mLoginTask;

    PermissionChecker checker;

    // Firebase.
    private FirebaseAuth mAuth;

    private Pref sharedPref;
    private LogInPreference LoginPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize the password restriction
        PASSWORD_RESTRICTION = getString(R.string.password_restriction);

        checker = new PermissionChecker(LoginActivity.this);
        SetStatusBarColor();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Set up the login form.
        mCodeView = (EditText) findViewById(R.id.login_code);
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);

        // Make the code EditText all-caps.
        InputFilter[] editFilters = mCodeView.getFilters();
        InputFilter[] newFilters = new InputFilter[editFilters.length + 1];
        System.arraycopy(editFilters, 0, newFilters, 0, editFilters.length);
        newFilters[editFilters.length] = new InputFilter.AllCaps();
        mCodeView.setFilters(newFilters);

        // Make the Privacy Policy link active.
        TextView priv = findViewById(R.id.login_text_privacy);
        priv.setMovementMethod(LinkMovementMethod.getInstance());

        androidx.constraintlayout.widget.ConstraintLayout layout = findViewById(R.id.linearLayout);

        sharedPref = new Pref(LoginActivity.this);
        LoginPref = new LogInPreference(LoginActivity.this);

        // Consent is now handled on the enrollment website.

        // Add listeners for login.

        signin = (Button) findViewById(R.id.login_sign_in);
        signin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                EventDatabaseHelper.getInstance(LoginActivity.this).deleteDatabase(LoginActivity.this);

                IsUserWantLoginOrRegister = true;

                // Sign in with pre-created account from enrollment website
                attemptLogin(false);
            }
        });

        // Initialize Firebase authentication.
        mAuth = FirebaseAuth.getInstance();

    }

    private void SetStatusBarColor(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        WindowInsetsController controller = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controller = getWindow().getInsetsController();


            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());

                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

            }

            // Create a fake status bar (Scrim View) to add color
            View statusBarScrim = new View(this);
            statusBarScrim.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, getStatusBarHeight() + 20));
            statusBarScrim.setBackgroundColor(Color.parseColor("#BFDAE2")); // Open Usage brand color

            // Add the scrim to your root layout
            FrameLayout rootLayout = findViewById(android.R.id.content);
            rootLayout.addView(statusBarScrim);
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!Utils.isInstallCodeSet(this))
        {
            onFirstRun();
        }
    }

    private void onFirstRun()
    {

        // Generate random install code and set it in sharedprefs.
        String installcode = Utils.setRandomInstallCode(this);
        String timestamp = new EventTimestamp().getTimestring();

        // Enter and Record this in firebase.
        Map<String, Object> data = new HashMap<>();
        data.put(timestamp, installcode);



        FirebaseManagerSingleton.getFirestore().collection("install").document(timestamp).set(data);



    }

    /**
     * Attempts to sign in with a pre-created account from the enrollment website.
     * The website creates the Firebase Auth account and Firestore profile.
     * The app only needs to sign in and pull the profile.
     */
    private void attemptLogin(boolean register)
    {
        // Do nothing if a login is already in progress.
        if (mLoginTask != null) return;

        // Reset errors.
        mCodeView.setError(null);

        // Store values at the time of the login attempt.
        String studyId = mCodeView.getText().toString().trim();

        // Check for a valid study ID.
        if (TextUtils.isEmpty(studyId))
        {
            mCodeView.setError(getString(R.string.error_field_required));
            mCodeView.requestFocus();
            return;
        }

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        showProgress(true);

        // Generate email and password from study ID (must match website generation)
        String generatedEmail = studyId.toUpperCase() + "@openusage.study";
        String generatedPassword = "study_" + studyId.toLowerCase() + "_2026";

        if (register)
        {
            createAccount(generatedEmail, generatedPassword);
        }
        else
        {
            signIn(generatedEmail, generatedPassword);
        }
    }

    private void createAccount(String subjId, String password)
    {
        mLoginTask = mAuth.createUserWithEmailAndPassword(subjId, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            onUserAcquired(user, true);

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Registration failed.",
                                    Toast.LENGTH_SHORT).show();

                            showProgress(false);
                        }

                        mLoginTask = null;
                    }
                });
    }

    private void signIn(String subjId, String password)
    {



        mLoginTask = mAuth.signInWithEmailAndPassword(subjId, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            onUserAcquired(user, false);

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Participant ID not found. Please enroll on the study website first.",
                                    Toast.LENGTH_LONG).show();
                            showProgress(false);
                        }

                        mLoginTask = null;
                    }
                });
    }

    private void onUserAcquired(final FirebaseUser user, boolean register)
    {



        // This flag will avoid automatic login attempts
        if (user == null || user.getEmail() == null)
        {
            showProgress(false);
            return;
        }

        // Get study ID as data subject ID.
        final String studyId = mCodeView.getText().toString().toUpperCase();
        String dataSubjectId = studyId;


        DocumentReference collectionReference = FirebaseFirestore.getInstance().collection("users").document(dataSubjectId).collection("specs").document("specs");


        // If registering, do some initialization of the user in the database.
        if (register)
        {

            String userId = user.getEmail();

            HashMap<String, Object> data = new HashMap<>();
            data.put("email", userId);
            data.put("study_id", studyId);

            FirebaseFirestore.getInstance().collection("user_directory").document(dataSubjectId).set(data).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {

                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {

                }
            });


            collectionReference.set(SpecsInfo.createPhoneSpecMap());
        }

        // On first run, we want to copy group-specific settings in to the user's profile.

        // First, check if the user has settings set for them in the database.
        Log.d(TAG, "Checking if user settings have been populated yet...");

        collectionReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error == null){
                    loginComplete(user);
                }else {
                    loginComplete(user);
                }
            }
        });

    }

    private void loginComplete(FirebaseUser user)
    {

        if (!IsUserWantLoginOrRegister){
            user = null;
            return;
        }

        if (user != null && !mCodeView.getText().toString().isEmpty()){
            final LogInPreference loginPref = new LogInPreference(getApplicationContext());

            // Populate shared pref values so the login is official.
            final String studyId = mCodeView.getText().toString().toUpperCase();
            String pref_id = user.getEmail(); // Generated email
            String password = "study_" + studyId.toLowerCase() + "_2026"; // Generated password
            
            loginPref.AddUserSubjId(pref_id);
            loginPref.AddUserPassword(password);
            loginPref.AddUserCode(studyId);
            loginPref.AddUserEmail(pref_id);
            loginPref.AddUserNumber("000");

            // Fetch demographic data from Firestore (written by enrollment website)
            FirebaseFirestore.getInstance()
                .collection("users").document(studyId)
                .collection("profile").document("demographics")
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            DocumentSnapshot doc = task.getResult();
                            String ageRange = doc.getString("age_range");
                            String gender = doc.getString("gender");
                            String location = doc.getString("location");
                            String incomeLevel = doc.getString("income_level");
                            String usageLevel = doc.getString("usage_level");
                            loginPref.AddDemographicData(
                                ageRange != null ? ageRange : "",
                                gender != null ? gender : "",
                                location != null ? location : "",
                                incomeLevel != null ? incomeLevel : "",
                                usageLevel != null ? usageLevel : ""
                            );
                            // Read study group assigned via enrollment URL parameter
                            String studyGroup = doc.getString("study_group");
                            loginPref.AddStudyGroup(studyGroup != null ? studyGroup : "DEFAULT");

                            // Pre-save screenshots-enabled to SharedPrefs so
                            // SettingsManager.loadFromDisk() has the correct value
                            // before the async Firestore group settings fetch completes.
                            // Use explicit field if present, otherwise derive from study_group.
                            Object screenshotsObj = doc.get("screenshots_enabled");
                            int screenshotsEnabled;
                            if (screenshotsObj != null) {
                                screenshotsEnabled = Integer.parseInt(screenshotsObj.toString());
                            } else {
                                screenshotsEnabled = "TEXT_ONLY".equalsIgnoreCase(studyGroup) ? 0 : 1;
                            }
                            loginPref.AddSHARED_PREF_PREFIXSetting("setting_screenshots-enabled", screenshotsEnabled);

                            Log.d(TAG, "Demographics loaded for " + studyId + " (group: " + studyGroup + ", screenshots: " + screenshotsEnabled + ")");
                        } else {
                            Log.w(TAG, "No web enrollment demographics found for " + studyId + ", using defaults");
                            loginPref.AddDemographicData("", "", "", "", "");
                            loginPref.AddStudyGroup("DEFAULT");
                            loginPref.AddSHARED_PREF_PREFIXSetting("setting_screenshots-enabled", 1);
                        }

                        // Proceed to permissions regardless
                        proceedAfterLogin(studyId);
                    }
                });
        }
    }

    private void proceedAfterLogin(String studyId) {
        Intent intent = new Intent(this, PermissionParentActivity.class);
        IsUserWantLoginOrRegister = false;
        startActivity(intent);

        EventUploaderToFireStore uploaderToFireStore = EventUploaderToFireStore.getInstance(LoginActivity.this);
        uploaderToFireStore.uploadSingleEvent("LogInOutEvent", EventMapBuilder.buildCompleteMap(null, "login"), getApplicationContext());

        finish();
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {

        if (show) {
            Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show();
        }

        // Disable controls during login.
        findViewById(R.id.login_sign_in).setEnabled(!show);
        mCodeView.setEnabled(!show);
    }

    public static String calcSubjectId(String code, String number, String email)
    {
        return code.toUpperCase() + "_" + number + "_" + email;
    }

    /**
     * Helper function that logs out the user, called from other activities.
     * @param context App context
     * @param showLoginActivity Whether to open the LoginActivity after logging out.
     */
    public static void userLogOut(Context context, boolean showLoginActivity)
    {

        // New code
        LogInPreference sharedPref = new LogInPreference(context);
        sharedPref.AddUserSubjId("");
        sharedPref.AddUserCode("");
        sharedPref.AddUserEmail("");
        sharedPref.AddUserNumber("");
        sharedPref.AddUserPassword("");

        sharedPref.clearAllPreferences();

        // Log out of Firebase.
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signOut();

        // Stop the CaptureUploadService.
        Intent stopIntent = new Intent(context, CaptureUploadService.class);
        context.stopService(stopIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM){
            context.stopService(new Intent(context, CaptureUploadService.class));
        }


        // Start LoginActivity if desired.
        if (showLoginActivity)
        {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);

        }
    }

    /**
     * Static utility method that makes sure the user is fully logged in. If the user is not logged
     * in at all, this method will do nothing other than returning -1. If the user is logged in in
     * shared prefs but not in FirebaseAuth, this method will do a Firebase login.
     * @param context App context
     * @param listener If the user is logged in in sharedprefs but not Firebase, this listener will
     * be invoked during the Firebase login.
     * @return -1 if logged out; 0 if not logged in in Firebase (in which case a Firebase login is
     * now being attempted); 1 if fully logged in already.
     */
    public static int ensureCompleteLogin(Context context, OnCompleteListener<AuthResult> listener)
    {

        // New Code

        LogInPreference sharedPref = new LogInPreference(context);
        String subj_id = sharedPref.GetUserSubjId();
        String password = sharedPref.GetUserPassword();

        // Check for a shared prefs login.
        if (subj_id.isEmpty()) {
            return -1;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();

        // If not logged in with Firebase, do a login there (the user doesn't have to know).
        if (auth.getCurrentUser() == null)
        {
            Task<AuthResult> task = auth.signInWithEmailAndPassword(subj_id, password);
            if (listener != null) task.addOnCompleteListener(listener);
            return 0;
        }

        // We were fully logged in.
        return 1;
    }


}

