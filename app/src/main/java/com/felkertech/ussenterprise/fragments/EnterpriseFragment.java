
package com.felkertech.ussenterprise.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
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
import com.felkertech.ussenterprise.ui.EapSpinnerAdapter;
import com.felkertech.ussenterprise.ui.Phase2SpinnerAdapter;

import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EnterpriseFragment extends Fragment {

    private static final String TAG = "EnterpriseWifi";
    private static final String SSID = "eduroam";
    private static final String DOMAIN = "cqu.edu.au";
    private static final int LOCATION_REQUEST = 1201;

    private static final String CA_FINGERPRINT =
            "8FE4FB0AF93A4D0D67DB0BEBB23E37C7" +
            "1BF325DCBCDD240EA04DAF58B47E1840";

    private View rootView;
    private WifiManager wifiManager;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();

    private volatile boolean busy = false;

    private int selectedEap =
            WifiEnterpriseConfig.Eap.PEAP;

    private int selectedPhase2 =
            WifiEnterpriseConfig.Phase2.MSCHAPV2;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state) {

        rootView = inflater.inflate(
                R.layout.activity_main, container, false);

        Context context =
                requireActivity().getApplicationContext();

        wifiManager = (WifiManager)
                context.getSystemService(Context.WIFI_SERVICE);

        setupSpinners();

        rootView.findViewById(R.id.button_connect)
                .setOnClickListener(v -> connect());

        rootView.findViewById(R.id.button_disconnect)
                .setOnClickListener(v -> inspectSavedNetwork());

        rootView.findViewById(R.id.button_connect_preset)
                .setVisibility(View.GONE);

        EditText ssidField =
                rootView.findViewById(R.id.ssid_edit);

        ssidField.setText(SSID);

        log("Enterprise Wi-Fi Build #17 ready.");
        log("CONNECT: save or update eduroam.");
        log("DISCONNECT: inspect saved profile.");
        log("Background processing enabled.");
        log("No automatic network switching.");

        runBackground(() -> {
            X509Certificate ca = findCquCertificate();

            if (ca != null) {
                log("Certificate configuration ready.");
            } else {
                log("Connection requires valid CA.");
            }

            reportCurrentWifi();
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        checkLocationPermission();

        runBackground(() -> reportCurrentWifi());
    }

    private void runBackground(Runnable task) {
        worker.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log("Background operation failed: " +
                        e.getClass().getSimpleName());
                Log.e(TAG, "Background error", e);
            }
        });
    }

    private void log(String message) {
        Log.i(TAG, message);

        mainHandler.post(() -> {
            if (rootView == null) {
                return;
            }

            TextView logs =
                    rootView.findViewById(R.id.logs);

            if (logs != null) {
                logs.setText(
                        message + "\n\n" + logs.getText());
            }
        });
    }

    private boolean hasFineLocation() {
        return getActivity() != null &&
                getActivity().checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void checkLocationPermission() {
        if (hasFineLocation()) {
            log("Precise Location: GRANTED.");
        } else {
            log("Precise Location: NOT GRANTED.");

            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST) {
            log(hasFineLocation()
                    ? "Precise Location: GRANTED."
                    : "Precise Location: DENIED.");
        }
    }

    private void setupSpinners() {

        Spinner eap = rootView.findViewById(R.id.eap);
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

    private String fingerprint(X509Certificate certificate)
            throws Exception {

        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(certificate.getEncoded());

        StringBuilder result = new StringBuilder();

        for (byte b : hash) {
            result.append(
                    String.format("%02X", b & 0xff));
        }

        return result.toString();
    }

    private X509Certificate findCquCertificate() {

        try {
            KeyStore store =
                    KeyStore.getInstance("AndroidCAStore");

            store.load(null);

            Enumeration<String> aliases =
                    store.aliases();

            while (aliases.hasMoreElements()) {

                java.security.cert.Certificate item =
                        store.getCertificate(
                                aliases.nextElement());

                if (!(item instanceof X509Certificate)) {
                    continue;
                }

                X509Certificate certificate =
                        (X509Certificate) item;

                if (CA_FINGERPRINT.equals(
                        fingerprint(certificate))) {

                    certificate.checkValidity();

                    if (certificate.getBasicConstraints() < 0) {
                        throw new Exception(
                                "Certificate is not a CA");
                    }

                    log("MATCHING CQUniversity CA FOUND.");
                    log("CA: QuoVadis Root CA 2 G3");
                    log("SHA-256 fingerprint verified.");

                    return certificate;
                }
            }

            log("Required CA certificate not found.");

        } catch (Exception e) {
            log("CA error: " +
                    e.getClass().getSimpleName());

            Log.e(TAG, "Certificate lookup failed", e);
        }

        return null;
    }

    private void reportCurrentWifi() {

        try {
            if (wifiManager == null) {
                log("Wi-Fi service unavailable.");
                return;
            }

            WifiInfo info =
                    wifiManager.getConnectionInfo();

            if (info != null) {
                log("Current Wi-Fi SSID: " +
                        info.getSSID());

                log("Current Wi-Fi state: " +
                        info.getSupplicantState());
            }

        } catch (Exception e) {
            log("Wi-Fi status unavailable.");
            Log.e(TAG, "Wi-Fi status error", e);
        }
    }

    private List<WifiConfiguration> getSavedNetworks() {

        try {
            List<WifiConfiguration> saved =
                    wifiManager.getConfiguredNetworks();

            if (saved == null) {
                log("Saved-network API returned null.");
                return new ArrayList<>();
            }

            return saved;

        } catch (Exception e) {
            log("Saved-network lookup failed: " +
                    e.getClass().getSimpleName());

            Log.e(TAG, "Saved-network lookup error", e);

            return new ArrayList<>();
        }
    }

    private WifiConfiguration findExistingNetwork(
            List<WifiConfiguration> saved) {

        for (WifiConfiguration config : saved) {

            if (!("\"" + SSID + "\"").equals(config.SSID)) {
                continue;
            }

            boolean enterprise =
                    config.allowedKeyManagement.get(
                            WifiConfiguration.KeyMgmt.WPA_EAP)
                    ||
                    config.allowedKeyManagement.get(
                            WifiConfiguration.KeyMgmt.IEEE8021X);

            if (enterprise) {
                return config;
            }
        }

        return null;
    }

    private void inspectSavedNetwork() {

        if (busy) {
            log("Another operation is running.");
            return;
        }

        busy = true;

        runBackground(() -> {
            try {
                inspectSavedNetworkInternal();
            } finally {
                busy = false;
            }
        });
    }

    private void inspectSavedNetworkInternal() {

        log("========== SAVED PROFILE CHECK ==========");

        if (wifiManager == null) {
            log("Wi-Fi service unavailable.");
            return;
        }

        try {
            List<WifiConfiguration> saved =
                    getSavedNetworks();

            log("Visible saved networks: " +
                    saved.size());

            WifiConfiguration config =
                    findExistingNetwork(saved);

            if (config == null) {
                log("No visible saved enterprise eduroam profile.");
                log("Android may restrict profile visibility.");
                reportCurrentWifi();
                log("========== CHECK COMPLETE ==========");
                return;
            }

            log("Saved SSID: " + SSID);
            log("Network ID: " + config.networkId);
            log("Status: " + config.status);

            boolean wpaEap =
                    config.allowedKeyManagement.get(
                            WifiConfiguration.KeyMgmt.WPA_EAP);

            boolean ieee8021x =
                    config.allowedKeyManagement.get(
                            WifiConfiguration.KeyMgmt.IEEE8021X);

            log("WPA_EAP: " + wpaEap);
            log("IEEE8021X: " + ieee8021x);

            WifiEnterpriseConfig enterprise =
                    config.enterpriseConfig;

            if (enterprise == null) {
                log("ERROR: Enterprise config is NULL.");
                return;
            }

            log("EAP: " +
                    (enterprise.getEapMethod() ==
                            WifiEnterpriseConfig.Eap.PEAP
                            ? "PEAP (CORRECT)"
                            : "INCORRECT"));

            log("Phase 2: " +
                    (enterprise.getPhase2Method() ==
                            WifiEnterpriseConfig.Phase2.MSCHAPV2
                            ? "MSCHAPV2 (CORRECT)"
                            : "INCORRECT"));

            String domain =
                    enterprise.getDomainSuffixMatch();

            log("Domain: " +
                    (domain == null ? "MISSING" : domain));

            log("Domain matches CQU: " +
                    DOMAIN.equalsIgnoreCase(domain));

            String identity = enterprise.getIdentity();

            log("Identity present: " +
                    (identity != null &&
                            !identity.isEmpty()));

            log("Anonymous identity correct: " +
                    "anonymous@cqu.edu.au".equalsIgnoreCase(
                            enterprise.getAnonymousIdentity()));

            X509Certificate ca =
                    enterprise.getCaCertificate();

            if (ca != null) {
                log("Saved CA fingerprint correct: " +
                        CA_FINGERPRINT.equals(
                                fingerprint(ca)));
            } else {
                X509Certificate[] chain =
                        enterprise.getCaCertificates();

                if (chain != null && chain.length > 0) {
                    boolean matched = false;

                    for (X509Certificate cert : chain) {
                        if (CA_FINGERPRINT.equals(
                                fingerprint(cert))) {
                            matched = true;
                        }
                    }

                    log("Saved CA chain length: " +
                            chain.length);
                    log("Saved CA fingerprint correct: " +
                            matched);
                } else {
                    log("Saved CA: NOT EXPOSED.");
                    log("Android may redact certificate data.");
                }
            }

            log("Password: NOT DISPLAYED.");
            log("Readback does not prove authentication.");

        } catch (Exception e) {
            log("Profile inspection failed: " +
                    e.getClass().getSimpleName());

            Log.e(TAG, "Inspection failed", e);
        }

        reportCurrentWifi();
        log("========== CHECK COMPLETE ==========");
    }

    private void connect() {

        if (wifiManager == null) {
            log("Wi-Fi service unavailable.");
            return;
        }

        if (busy) {
            log("Another operation is running.");
            return;
        }

        if (!hasFineLocation()) {
            checkLocationPermission();
            return;
        }

        EditText ssidField =
                rootView.findViewById(R.id.ssid_edit);

        EditText identityField =
                rootView.findViewById(R.id.identity);

        EditText passwordField =
                rootView.findViewById(R.id.password);

        String ssid =
                ssidField.getText().toString().trim();

        String identity =
                identityField.getText().toString().trim();

        String password =
                passwordField.getText().toString();

        if (ssid.isEmpty() ||
                identity.isEmpty() ||
                password.isEmpty()) {
            log("Enter SSID, identity and password.");
            return;
        }

        if (!SSID.equals(ssid)) {
            log("This build only configures eduroam.");
            return;
        }

        if (selectedEap !=
                WifiEnterpriseConfig.Eap.PEAP ||
                selectedPhase2 !=
                WifiEnterpriseConfig.Phase2.MSCHAPV2) {
            log("Select PEAP and MSCHAPV2.");
            return;
        }

        busy = true;

        log("Preparing enterprise profile...");
        log("Wi-Fi operations running in background.");

        runBackground(() -> {
            try {
                saveEnterpriseNetwork(
                        ssid, identity, password);
            } finally {
                busy = false;
            }
        });
    }

    private void saveEnterpriseNetwork(
            String ssid,
            String identity,
            String password) {

        X509Certificate ca = findCquCertificate();

        if (ca == null) {
            log("Cannot save: CA unavailable.");
            return;
        }

        try {
            WifiEnterpriseConfig enterprise =
                    new WifiEnterpriseConfig();

            enterprise.setIdentity(identity);
            enterprise.setAnonymousIdentity(
                    "anonymous@cqu.edu.au");
            enterprise.setPassword(password);

            enterprise.setEapMethod(
                    WifiEnterpriseConfig.Eap.PEAP);

            enterprise.setPhase2Method(
                    WifiEnterpriseConfig.Phase2.MSCHAPV2);

            enterprise.setCaCertificate(ca);
            enterprise.setDomainSuffixMatch(DOMAIN);

            WifiConfiguration config =
                    new WifiConfiguration();

            config.SSID = "\"" + ssid + "\"";

            config.allowedKeyManagement.set(
                    WifiConfiguration.KeyMgmt.WPA_EAP);

            config.allowedKeyManagement.set(
                    WifiConfiguration.KeyMgmt.IEEE8021X);

            config.enterpriseConfig = enterprise;

            log("SSID: eduroam");
            log("EAP: PEAP");
            log("Phase 2: MSCHAPV2");
            log("Domain: " + DOMAIN);
            log("CA fingerprint: VERIFIED");

            List<WifiConfiguration> saved =
                    getSavedNetworks();

            WifiConfiguration existing =
                    findExistingNetwork(saved);

            int networkId = -1;

            if (existing != null) {

                log("Existing enterprise profile found.");
                log("Existing network ID: " +
                        existing.networkId);

                config.networkId = existing.networkId;

                log("Attempting updateNetwork...");

                networkId =
                        wifiManager.updateNetwork(config);

                if (networkId >= 0) {
                    log("EXISTING PROFILE UPDATED.");
                } else {
                    log("Update returned -1.");
                    log("Will attempt addNetwork.");
                }

            } else {
                log("No existing enterprise profile visible.");
                log("Will attempt addNetwork.");
            }

            if (networkId < 0) {
                config.networkId = -1;

                networkId =
                        wifiManager.addNetwork(config);

                if (networkId >= 0) {
                    log("NETWORK PROFILE SAVED.");
                }
            }

            if (networkId >= 0) {
                log("Network ID: " + networkId);
                log("Android accepted the configuration.");
                log("Authentication is NOT yet verified.");
                log("No network switch requested.");

                inspectSavedNetworkInternal();

                log("Next: connect through TV Wi-Fi settings.");
            } else {
                log("NETWORK SAVE FAILED.");
                log("Both update/add paths unsuccessful.");
                log("Check Android Wi-Fi permissions.");
            }

            reportCurrentWifi();

        } catch (SecurityException e) {
            log("WI-FI PERMISSION DENIED.");
            Log.e(TAG, "Wi-Fi permission error", e);

        } catch (Exception e) {
            log("Configuration failed: " +
                    e.getClass().getSimpleName());

            Log.e(TAG, "Configuration error", e);
        }
    }

    public void loadSsids() {

        if (rootView == null || wifiManager == null) {
            return;
        }

        if (!hasFineLocation()) {
            checkLocationPermission();
            return;
        }

        runBackground(() -> {
            try {
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
                            result.capabilities.contains("EAP") &&
                            !networks.contains(result.SSID)) {
                        networks.add(result.SSID);
                    }
                }

                if (networks.size() == 1) {
                    final String discovered =
                            networks.get(0);

                    mainHandler.post(() -> {
                        if (rootView == null) {
                            return;
                        }

                        EditText field =
                                rootView.findViewById(
                                        R.id.ssid_edit);

                        if (field != null) {
                            field.setText(discovered);
                        }
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "Scan failed", e);
            }
        });
    }

    @Override
    public void onDestroyView() {
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
