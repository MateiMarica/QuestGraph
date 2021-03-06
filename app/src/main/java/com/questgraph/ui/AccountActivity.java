package com.questgraph.ui;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import com.google.android.material.navigation.NavigationView;
import com.questgraph.R;
import com.questgraph.control.APIManager;
import com.questgraph.control.FileManager;
import com.questgraph.control.Tools;
import com.questgraph.database.Account;
import com.questgraph.database.DataManager;
import com.questgraph.exception.InvalidAccessTokenException;
import com.questgraph.exception.InvalidManualAuthTokenException;

import java.util.ArrayList;


public class AccountActivity extends AppCompatActivity {

    final static int darkThemeBackground = Color.rgb(19, 21, 22);
    final static int lightThemeBackground = Color.rgb(255, 255, 255);
    final static int darkThemeText = Color.rgb(160, 160, 160);
    final static int darkThemeElements = Color.rgb(40, 40, 40);
    final static int lightThemeText = Color.rgb(67, 67, 67);

    static boolean restartSettings;
    /**
     * Reference to the UI.
     */
    public static Context context;

    /**
     * Loading dialog.
     */
    private ProgressDialog loadingDialog;

    /**
     * Enum representing what is currently being accessed.
     */
    enum Section {ACCOUNTS, ACCESS_TOKEN_EXPIRED}

    /**
     * The navigation drawer.
     */
    private DrawerLayout drawer;

    /**
     * Contains all the submenus and items in the navigation drawer.
     */
    Menu menu;

    /**
     * This is a submenu in the drawer labelled "Accounts". It features an selectable item to navigate to an overview for each account
     * The items are created at runtime.
     */
    SubMenu accountGroup;

    /**
     * The toolbar at the top. Changes text depending on where you are in the app.
     */
    private Toolbar toolbar;

    /**
     * Reference to the current fragment that is at the front. Null if no fragment is at the top.
     */
    private Fragment currentFragment;

    /**
     * The view that contains the drawer (I think)
     */
    static NavigationView navigationView;

    private View view;

    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        view = findViewById(android.R.id.content).getRootView();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("QuestGraph");

        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, drawer, toolbar, 0, 0);
        drawer.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        loadingDialog = new ProgressDialog(this);
        loadingDialog.show();
        loadingDialog.setCancelable(false);
        loadingDialog.setTitle("Loading...");



        menu = navigationView.getMenu();
        new HomeMenuItemUI(this, currentFragment, drawer, toolbar);
        new SettingsMenuItemUI(this, currentFragment, drawer, toolbar);

        //Adds the accounts submenu after adding the home and settings items to have it appear at the end of the menu.
        accountGroup = menu.addSubMenu("Accounts");

        //Begins the start that gets the information about the accounts (eg: creates a menu item for each account)
        new AccessTokenTask().execute(Section.ACCOUNTS);

        //New main fragment
        //If settings were just closed due to night mode switch, restart settings
        if (restartSettings == true) {
            restartSettings = false;
            menu.getItem(1).setChecked(true); //Settings menu item is at index 1 in the menu
            toolbar.setTitle("Settings");


            SettingsFragment newFragment = new SettingsFragment();
            currentFragment = newFragment;
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, newFragment).addToBackStack("").commit();
        } else {
            MainFragment newFragment = new MainFragment();
            currentFragment = newFragment;
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, newFragment).addToBackStack("").commit();
        }

        //Sets context reference
        context = this;

        //Creates a unique periodic work request that will run every 30 minutes and get balance info on all the accounts to create visuals.
        /*PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                BalanceRecordingService.class, 20, TimeUnit.MINUTES
        ).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("BalanceGetter", ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest);*/
    }

    @Override
    public void onBackPressed() {

        //If drawer open
        if(drawer.isDrawerOpen(GravityCompat.START)) {
           drawer.closeDrawers();

       //Goes back to the home screen if the home page is selected when back is pressed.
        } else if(menu.getItem(0).isChecked()) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);


        //On any fragment, back press send you to home page
        } else {
            super.onBackPressed();

            menu.getItem(0).setChecked(true);
            toolbar.setTitle("QuestGraph");

            MainFragment newFragment = new MainFragment();
            currentFragment = newFragment;
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, newFragment).addToBackStack("").commit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if(FileManager.darkThemeEnabled()) {
            navigationView.setBackgroundColor(darkThemeBackground);
            //navigationView.setItemTextColor(ColorStateList.valueOf(darkThemeText));
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        //Placeholder method
    }

    //Background task that uses the access token to get information from the API
    class AccessTokenTask extends AsyncTask<Section, Object, Boolean> {
        @Override
        protected void onPreExecute () {
            loadingDialog.setCancelable(false);
            loadingDialog.setTitle("Loading...");
            loadingDialog.setMessage("Retrieving accounts...");
        }

        @Override
        protected Boolean doInBackground(Section... section) {
            if(section[0].equals(Section.ACCOUNTS)) {

                try {
                    //Should retrieve accounts during every load because new accounts may have created
                    //Probably crashes if no internet though...
                    APIManager.retrieveAccounts();
                    System.out.println("Getting accounts..");
                    publishProgress(new Object[]{DataManager.getAccounts(), Section.ACCOUNTS});
                    System.out.println("Got accounts.");
                } catch (InvalidAccessTokenException e) {
                    //null is given when access token doesn't work, assumedly expired
                    publishProgress(new Object[]{DataManager.getRefreshToken(), Section.ACCESS_TOKEN_EXPIRED});
                    return false;
                }

            }

            return true;
        }

        @Override
        protected void onProgressUpdate(Object... obj) {
            //obj[0] = the info being passed through
            //obj[1] = the current Section
            Section currentSection = (Section) obj[1];

            switch (currentSection) {
                case ACCOUNTS:
                    ArrayList<Account> accounts =  (ArrayList<Account>) obj[0];
                    System.out.println(accounts.get(0).number);
                    for(int i = 0; i < accounts.size(); i++) {
                        Account account = new Account(accounts.get(i).type, accounts.get(i).number);
                        new AccountMenuItemUI(AccountActivity.this, currentFragment, drawer, toolbar, account);
                    }

                    loadingDialog.dismiss();
                    break;

                case ACCESS_TOKEN_EXPIRED:

                    //Tries to use the refresh token if the access token doesn't work
                    new RefreshTokenTask().execute((String) obj[0]);

            }
        }

        protected void onPostExecute(Boolean... result) {
            //placeholder
        }
    }

    //Enum that represents the current state of using the refresh token
    enum RefreshCurrentState {SUCCESSFUL, FAIL}

    /*void startRefreshTokenTask() {
        new RefreshTokenTask().execute(DataManager.getRefreshToken());
    }*/

    //Background task that uses the refresh token if the access token doesn't work at any time
    class RefreshTokenTask extends AsyncTask<String, RefreshCurrentState, Boolean> {

        protected void onPreExecute () {
            loadingDialog.setMessage("Access token expired, trying refresh token...");
        }

        protected Boolean doInBackground(String... refreshToken) {

            try {
                //Tries to get a new access token using the refresh token
                APIManager.retrieveAuthorization(refreshToken[0]);

            } catch (InvalidManualAuthTokenException e) {

                DataManager.deleteAuthorization();
                publishProgress(RefreshCurrentState.FAIL);
                return false;

            }

            publishProgress (RefreshCurrentState.SUCCESSFUL);
            return true;
        }

        protected void onProgressUpdate(RefreshCurrentState... state) {

            switch(state[0]) {
                case SUCCESSFUL:
                    loadingDialog.dismiss();
                    ((Activity) context).recreate();
                    break;

                case FAIL:

                    //If the refresh token fails, nothing more can be done. Sends the user back to the home screen
                    loadingDialog.dismiss();
                    startActivity(new Intent(context, AuthLoginActivity.class).putExtra("refresh_token","invalid"));
                    finish();
                    break;

                default:
                    System.err.println("Issue in refreshTokenTask");
                    break;
            }
        }


        protected void onPostExecute(Boolean... result) {
            //placeholder
        }

    }

}