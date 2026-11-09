
package com.felkertech.ussenterprise.fragments;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Bundle;
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
import com.felkertech.ussenterprise.model.EnterpriseWifiConnection;
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

        wifiManager = (WifiManager) requireActivity()
                .getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        setupSpinners();

        rootView.findViewById(R.id.button_connect)
                .setOnClickListener(v -> connect());

        rootView.findViewById(R.id.button_disconnect)
                .setOnClickListener(v -> disconnect());

        rootView.findViewById(R.id.button_connect_preset)
                .setVisibility(View.GONE);

        EditText ssidField =
                rootView.findViewById(R.id.ssid_edit);

        ssidField.setText(SSID);

        log("Enterprise Wi-Fi Build #10 ready.");
        log("CQUniversity certificate auto-detection.");
        log("Server domain: " + DOMAIN);

        checkCertificate();

        return rootView;
    }

    private void setupSpinners() {

        Spinner eap = rootView.findViewById(R.id.eap);
        Spinner phase2 = rootView.findViewById(R.id.phase2);

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
                            AdapterView<?> parent) {}
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
                            AdapterView<?> parent) {}
                });
    }

    private String fingerprint(
            X509Certificate certificate) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hash =
                digest.digest(certificate.getEncoded());

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

            int count = 0;

            while (aliases.hasMoreElements()) {

                String alias = aliases.nextElement();

                java.security.cert.Certificate item =
                        store.getCertificate(alias);

                if (!(item instanceof X509Certificate)) {
                    continue;
                }

                X509Certificate certificate =
                        (X509Certificate) item;

                count++;

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

            log("Checked " + count +
                    " Android certificates.");
            log("Required CQUniversity CA not found.");

        } catch (Exception e) {

            Log.e(TAG, "Certificate lookup failed", e);

            log("CA lookup error: " +
                    e.getClass().getSimpleName());
        }

        return null;
    }

    private void checkCertificate() {

        X509Certificate ca = findCquCertificate();

        if (ca == null) {
            log("Connection requires CA installation.");
        } else {
            log("Certificate configuration ready.");
        }
    }

    private void connect() {

        if (Build.VERSION.SDK_INT < 29) {
            log("Android 10 or newer required.");
            return;
        }

        if (wifiManager == null || rootView == null) {
            log("Wi-Fi service unavailable.");
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

        if (selectedEap !=
                WifiEnterpriseConfig.Eap.PEAP ||
                selectedPhase2 !=
                WifiEnterpriseConfig.Phase2.MSCHAPV2) {

            log("Select PEAP and MSCHAPV2.");
            return;
        }

        X509Certificate ca = findCquCertificate();

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
                    WifiEnterpriseConfig.Phase2.MSCHAPV2);

            config.setCaCertificate(ca);

            config.setDomainSuffixMatch(DOMAIN);

            log("PEAP/MSCHAPV2 configured.");
            log("CA certificate verified.");
            log("Domain validation enabled.");

            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion.Builder()
                            .setSsid(ssid)
                            .setWpa2EnterpriseConfig(config)
                            .build();

            int result =
                    wifiManager.addNetworkSuggestions(
                            Collections.singletonList(
                                    suggestion));

            log("Suggestion result: " + result);

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {

                log("SUCCESS: Android accepted suggestion.");
                log("Waiting for Wi-Fi selection.");
                log("Approve app network request if shown.");
                log("Check actual Wi-Fi connection status.");

            } else {

                log("Android rejected suggestion.");
                log("Error code: " + result);
            }

        } catch (Exception e) {

            Log.e(TAG, "Connection failed", e);

            log("Connection configuration failed.");
            log(e.getClass().getSimpleName());
            log(String.valueOf(e.getMessage()));
        }
    }

    private void disconnect() {

        if (Build.VERSION.SDK_INT < 29 ||
                wifiManager == null) {
            return;
        }

        try {
            int result =
                    wifiManager.removeNetworkSuggestions(
                            Collections.emptyList());

            log("Remove suggestions result: " + result);

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

            if (!activity.grantedLocationPermission()) {
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
                        result.capabilities.contains("EAP") &&
                        !networks.contains(result.SSID)) {

                    networks.add(result.SSID);
                }
            }

            if (networks.size() == 1) {

                EditText field =
                        rootView.findViewById(R.id.ssid_edit);

                if (field != null) {
                    field.setText(networks.get(0));
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
                rootView.findViewById(R.id.logs);

        if (logs != null) {
            logs.setText(
                    message + "\n\n" + logs.getText());
        }
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        rootView = null;
    }
}
