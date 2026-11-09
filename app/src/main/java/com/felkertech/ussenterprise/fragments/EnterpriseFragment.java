
package com.felkertech.ussenterprise.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.felkertech.ussenterprise.R;
import com.felkertech.ussenterprise.activities.MainActivity;
import com.felkertech.ussenterprise.ui.EapSpinnerAdapter;
import com.felkertech.ussenterprise.ui.Phase2SpinnerAdapter;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class EnterpriseFragment extends Fragment {

    private static final String TAG = "EnterpriseWifi";
    private static final String SSID = "eduroam";
    private static final String DOMAIN = "cqu.edu.au";

    private static final String CA_FINGERPRINT =
            "8FE4FB0AF93A4D0D67DB0BEBB23E37C7" +
            "1BF325DCBCDD240EA04DAF58B47E1840";

    private View rootView;
    private WifiManager wifiManager;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean receiverRegistered = false;
    private boolean monitoring = false;
    private int monitorCount = 0;
    private String lastStatus = "";
    private boolean authenticationFailureSeen = false;

    private int selectedEap =
            WifiEnterpriseConfig.Eap.PEAP;

    private int selectedPhase2 =
            WifiEnterpriseConfig.Phase2.MSCHAPV2;

    private final BroadcastReceiver wifiReceiver =
            new BroadcastReceiver() {

        @Override
        public void onReceive(
                Context context, Intent intent) {

            if (intent == null) {
                return;
            }

            String action = intent.getAction();

            if (WifiManager
                    .SUPPLICANT_STATE_CHANGED_ACTION
                    .equals(action)) {

                int error = intent.getIntExtra(
                        WifiManager.EXTRA_SUPPLICANT_ERROR,
                        -1);

                if (error ==
                        WifiManager.ERROR_AUTHENTICATING) {

                    authenticationFailureSeen = true;

                    log("AUTHENTICATION ERROR reported.");
                    log("Check credentials, EAP settings");
                    log("and certificate validation.");

                    return;
                }

                SupplicantState state =
                        intent.getParcelableExtra(
                                WifiManager.EXTRA_NEW_STATE);

                if (state != null && monitoring) {
                    String value = state.toString();

                    if (!value.equals(lastStatus)) {
                        log("Supplicant: " + value);
                        lastStatus = value;
                    }
                }
            }

            if (WifiManager
                    .NETWORK_STATE_CHANGED_ACTION
                    .equals(action)) {

                NetworkInfo info =
                        intent.getParcelableExtra(
                                WifiManager.EXTRA_NETWORK_INFO);

                if (info != null) {

                    log("Network state: " +
                            info.getDetailedState());

                    if (info.isConnected()) {
                        reportConnection();
                    }
                }
            }
        }
    };

    private final Runnable monitorTask =
            new Runnable() {

        @Override
        public void run() {

            if (!monitoring || rootView == null) {
                return;
            }

            monitorCount++;

            log("Connection check " +
                    monitorCount + "/12");

            boolean connected = reportConnection();

            if (connected) {

                monitoring = false;
                log("EDUROAM CONNECTION CONFIRMED.");
                return;
            }

            if (monitorCount >= 12) {

                monitoring = false;

                log("Monitoring finished.");

                if (authenticationFailureSeen) {
                    log("Authentication error was detected.");
                } else {
                    log("No eduroam connection confirmed.");
                    log("This does not necessarily mean");
                    log("the credentials were rejected.");
                    log("Android may not have selected");
                    log("the suggested network.");
                }

                return;
            }

            handler.postDelayed(this, 5000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state) {

        rootView = inflater.inflate(
                R.layout.activity_main,
                container,
                false);

        wifiManager = (WifiManager)
                requireActivity()
                        .getApplicationContext()
                        .getSystemService(
                                Context.WIFI_SERVICE);

        setupSpinners();

        rootView.findViewById(
                R.id.button_connect)
                .setOnClickListener(
                        v -> connect());

        rootView.findViewById(
                R.id.button_disconnect)
                .setOnClickListener(
                        v -> disconnect());

        rootView.findViewById(
                R.id.button_connect_preset)
                .setVisibility(View.GONE);

        EditText ssidField =
                rootView.findViewById(
                        R.id.ssid_edit);

        ssidField.setText(SSID);

        log("Enterprise Wi-Fi Build #11 ready.");
        log("CQUniversity eduroam diagnostics.");
        log("Server domain: " + DOMAIN);

        checkCertificate();

        return rootView;
    }

    @Override
    public void onStart() {

        super.onStart();

        registerWifiReceiver();

        log("Wi-Fi diagnostics enabled.");

        reportConnection();
    }

    @Override
    public void onStop() {

        stopMonitoring();

        unregisterWifiReceiver();

        super.onStop();
    }

    private void registerWifiReceiver() {

        if (receiverRegistered) {
            return;
        }

        try {

            IntentFilter filter =
                    new IntentFilter();

            filter.addAction(
                    WifiManager
                            .NETWORK_STATE_CHANGED_ACTION);

            filter.addAction(
                    WifiManager
                            .SUPPLICANT_STATE_CHANGED_ACTION);

            requireActivity()
                    .registerReceiver(
                            wifiReceiver, filter);

            receiverRegistered = true;

        } catch (Exception e) {

            log("Receiver registration failed: " +
                    e.getClass().getSimpleName());
        }
    }

    private void unregisterWifiReceiver() {

        if (!receiverRegistered) {
            return;
        }

        try {

            requireActivity()
                    .unregisterReceiver(wifiReceiver);

        } catch (Exception e) {

            Log.w(TAG,
                    "Receiver removal failed", e);

        } finally {

            receiverRegistered = false;
        }
    }

    private void setupSpinners() {

        Spinner eap =
                rootView.findViewById(R.id.eap);

        Spinner phase2 =
                rootView.findViewById(R.id.phase2);

        eap.setAdapter(
                new EapSpinnerAdapter(getActivity()));

        phase2.setAdapter(
                new Phase2SpinnerAdapter(getActivity()));

        eap.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                selectedEap = (int) id;
            }

            @Override
            public void onNothingSelected(
                    AdapterView<?> parent) {
            }
        });

        phase2.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                selectedPhase2 = (int) id;
            }

            @Override
            public void onNothingSelected(
                    AdapterView<?> parent) {
            }
        });
    }

    private String fingerprint(
            X509Certificate certificate)
            throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hash =
                digest.digest(
                        certificate.getEncoded());

        StringBuilder result =
                new StringBuilder();

        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02X", b & 0xff));
        }

        return result.toString();
    }

    private X509Certificate findCquCertificate() {

        try {

            KeyStore store =
                    KeyStore.getInstance(
                            "AndroidCAStore");

            store.load(null);

            Enumeration<String> aliases =
                    store.aliases();

            int count = 0;

            while (aliases.hasMoreElements()) {

                String alias =
                        aliases.nextElement();

                java.security.cert.Certificate item =
                        store.getCertificate(alias);

                if (!(item instanceof
                        X509Certificate)) {
                    continue;
                }

                X509Certificate certificate =
                        (X509Certificate) item;

                count++;

                if (CA_FINGERPRINT.equals(
                        fingerprint(certificate))) {

                    certificate.checkValidity();

                    if (certificate
                            .getBasicConstraints() < 0) {

                        throw new Exception(
                                "Certificate is not a CA");
                    }

                    log("MATCHING CQUniversity CA FOUND.");
                    log("CA: QuoVadis Root CA 2 G3");
                    log("SHA-256 fingerprint verified.");

                    return certificate;
                }
            }

            log("Checked " + count +
                    " Android certificates.");

            log("Required CQUniversity CA not found.");

        } catch (Exception e) {

            Log.e(TAG,
                    "Certificate lookup failed", e);

            log("CA lookup error: " +
                    e.getClass().getSimpleName());
        }

        return null;
    }

    private void checkCertificate() {

        X509Certificate ca =
                findCquCertificate();

        if (ca == null) {

            log("Connection requires CA installation.");

        } else {

            log("Certificate configuration ready.");
        }
    }

    private boolean reportConnection() {

        if (wifiManager == null) {
            log("Wi-Fi manager unavailable.");
            return false;
        }

        try {

            WifiInfo info =
                    wifiManager.getConnectionInfo();

            if (info == null) {

                log("No Wi-Fi connection information.");
                return false;
            }

            String currentSsid =
                    info.getSSID();

            if (currentSsid == null) {
                currentSsid = "Unknown";
            }

            currentSsid =
                    currentSsid.replace("\"", "");

            SupplicantState state =
                    info.getSupplicantState();

            log("Current SSID: " + currentSsid);

            log("Wi-Fi state: " + state);

            if (SSID.equalsIgnoreCase(
                    currentSsid) &&
                    state ==
                    SupplicantState.COMPLETED) {

                log("Connected to eduroam.");
                log("Signal: " +
                        info.getRssi() + " dBm");

                return true;
            }

            if ("S26".equalsIgnoreCase(
                    currentSsid)) {

                log("Still connected to S26.");
                log("Android has not switched");
                log("to eduroam.");
            }

        } catch (SecurityException e) {

            log("Wi-Fi status permission denied.");

        } catch (Exception e) {

            log("Status error: " +
                    e.getClass().getSimpleName());
        }

        return false;
    }

    private void startMonitoring() {

        stopMonitoring();

        monitoring = true;
        monitorCount = 0;
        lastStatus = "";
        authenticationFailureSeen = false;

        log("Starting 60-second monitoring.");
        log("Watching Wi-Fi state and errors.");

        handler.postDelayed(
                monitorTask, 5000);
    }

    private void stopMonitoring() {

        monitoring = false;

        handler.removeCallbacks(monitorTask);
    }

    private void connect() {

        if (Build.VERSION.SDK_INT < 29) {

            log("Android 10 or newer required.");
            return;
        }

        if (wifiManager == null ||
                rootView == null) {

            log("Wi-Fi service unavailable.");
            return;
        }

        EditText ssidField =
                rootView.findViewById(
                        R.id.ssid_edit);

        EditText identityField =
                rootView.findViewById(
                        R.id.identity);

        EditText passwordField =
                rootView.findViewById(
                        R.id.password);

        String ssid =
                ssidField.getText()
                        .toString().trim();

        String identity =
                identityField.getText()
                        .toString().trim();

        String password =
                passwordField.getText()
                        .toString();

        if (ssid.isEmpty() ||
                identity.isEmpty() ||
                password.isEmpty()) {

            log("Enter SSID, identity and password.");
            return;
        }

        if (selectedEap !=
                WifiEnterpriseConfig.Eap.PEAP ||
                selectedPhase2 !=
                WifiEnterpriseConfig.Phase2.MSCHAPV2) {

            log("Select PEAP and MSCHAPV2.");
            return;
        }

        X509Certificate ca =
                findCquCertificate();

        if (ca == null) {

            log("Cannot configure: CA unavailable.");
            return;
        }

        try {

            WifiEnterpriseConfig config =
                    new WifiEnterpriseConfig();

            config.setIdentity(identity);

            config.setAnonymousIdentity(
                    "anonymous@cqu.edu.au");

            config.setPassword(password);

            config.setEapMethod(
                    WifiEnterpriseConfig.Eap.PEAP);

            config.setPhase2Method(
                    WifiEnterpriseConfig
                            .Phase2.MSCHAPV2);

            config.setCaCertificate(ca);

            config.setDomainSuffixMatch(DOMAIN);

            log("PEAP/MSCHAPV2 configured.");
            log("CA certificate verified.");
            log("Domain validation enabled.");

            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion.Builder()
                            .setSsid(ssid)
                            .setWpa2EnterpriseConfig(
                                    config)
                            .build();

            int result =
                    wifiManager.addNetworkSuggestions(
                            Collections.singletonList(
                                    suggestion));

            log("Suggestion result: " + result);

            if (result ==
                    WifiManager
                            .STATUS_NETWORK_SUGGESTIONS_SUCCESS) {

                log("Android accepted configuration.");
                log("This is NOT a connection yet.");

                log("Approve network suggestion");
                log("if Android asks.");

                startMonitoring();

            } else {

                log("Suggestion registration failed.");
                log("Android error code: " + result);

                log("If already registered, the");
                log("existing suggestion may still");
                log("be active in Android.");
            }

        } catch (Exception e) {

            Log.e(TAG,
                    "Connection configuration failed",
                    e);

            log("Connection configuration failed.");

            log(e.getClass().getSimpleName());

            log(String.valueOf(
                    e.getMessage()));
        }
    }

    private void disconnect() {

        stopMonitoring();

        if (Build.VERSION.SDK_INT < 29 ||
                wifiManager == null) {
            return;
        }

        try {

            int result =
                    wifiManager.removeNetworkSuggestions(
                            Collections.emptyList());

            log("Remove suggestions result: " +
                    result);

            log("This removes app suggestions,");
            log("not necessarily the active");
            log("Wi-Fi connection.");

        } catch (Exception e) {

            log("Removal failed: " +
                    e.getClass().getSimpleName());
        }
    }

    public void loadSsids() {

        if (rootView == null ||
                wifiManager == null) {
            return;
        }

        try {

            MainActivity activity =
                    (MainActivity) getActivity();

            if (activity == null) {
                return;
            }

            if (!activity
                    .grantedLocationPermission()) {

                activity.requestLocationPermission();
                return;
            }

            List<ScanResult> results =
                    wifiManager.getScanResults();

            if (results == null) {
                return;
            }

            List<String> networks =
                    new ArrayList<>();

            for (ScanResult result : results) {

                if (result.SSID != null &&
                        result.capabilities != null &&
                        result.capabilities
                                .contains("EAP") &&
                        !networks.contains(
                                result.SSID)) {

                    networks.add(result.SSID);
                }
            }

            if (networks.size() == 1) {

                EditText field =
                        rootView.findViewById(
                                R.id.ssid_edit);

                if (field != null) {

                    field.setText(
                            networks.get(0));
                }
            }

        } catch (Exception e) {

            Log.e(TAG, "Scan failed", e);
        }
    }

    private void log(String message) {

        Log.i(TAG, message);

        if (rootView == null) {
            return;
        }

        TextView logs =
                rootView.findViewById(
                        R.id.logs);

        if (logs != null) {

            logs.setText(
                    message + "\n\n" +
                            logs.getText());
        }
    }

    @Override
    public void onDestroyView() {

        stopMonitoring();

        rootView = null;

        super.onDestroyView();
    }
}
