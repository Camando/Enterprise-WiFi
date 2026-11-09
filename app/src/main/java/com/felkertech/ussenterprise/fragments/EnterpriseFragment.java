
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
import android.support.v7.app.AlertDialog;
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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class EnterpriseFragment extends Fragment {

    private static final String TAG = "EnterpriseWifi";
    private static final String SSID = "eduroam";
    private static final String DOMAIN = "cqu.edu.au";

    private View rootView;
    private WifiManager wifiManager;

    private EnterpriseWifiConnection pendingConnection;
    private WifiNetworkSuggestion activeSuggestion;

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
                .setOnClickListener(v -> prepareConnection());

        rootView.findViewById(R.id.button_disconnect)
                .setOnClickListener(v -> removeSuggestions());

        rootView.findViewById(R.id.button_connect_preset)
                .setVisibility(View.GONE);

        EditText ssidField =
                rootView.findViewById(R.id.ssid_edit);

        ssidField.setText(SSID);

        showMessage("Enterprise Wi-Fi Build #9 ready.");
        showMessage("Domain: " + DOMAIN);

        if (Build.VERSION.SDK_INT >= 33) {
            showMessage("Android TOFU API available.");

            if (wifiManager.isTrustOnFirstUseSupported()) {
                showMessage("Device reports TOFU support.");
            } else {
                showMessage("Device does not support TOFU.");
            }
        }

        return rootView;
    }

    private void setupSpinners() {

        Spinner eap = rootView.findViewById(R.id.eap);
        Spinner phase2 = rootView.findViewById(R.id.phase2);

        eap.setAdapter(new EapSpinnerAdapter(getActivity()));

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

    private void prepareConnection() {

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

            showMessage("Enter SSID, identity and password.");
            return;
        }

        if (selectedEap != WifiEnterpriseConfig.Eap.PEAP ||
                selectedPhase2 !=
                WifiEnterpriseConfig.Phase2.MSCHAPV2) {

            showMessage("Select PEAP and MSCHAPV2.");
            return;
        }

        pendingConnection =
                new EnterpriseWifiConnection.Builder()
                        .setSsid(ssid)
                        .setIdentity(identity)
                        .setPassword(password)
                        .setEap(selectedEap)
                        .setPhase2(selectedPhase2)
                        .build();

        if (Build.VERSION.SDK_INT >= 33 &&
                wifiManager.isTrustOnFirstUseSupported()) {

            new AlertDialog.Builder(requireActivity())
                    .setTitle("Connect to eduroam")
                    .setMessage(
                            "Use Android Trust on First Use.\n\n" +
                            "Android may ask you to approve " +
                            "the network certificate.\n\n" +
                            "Only approve it if the certificate " +
                            "and server identity match " +
                            "CQUniversity's trusted details.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Configure",
                            (d, w) -> connectWithTofu())
                    .show();

        } else {
            showMessage("TOFU unavailable.");
            selectSystemCertificate();
        }
    }

    private WifiEnterpriseConfig makeConfig() {

        WifiEnterpriseConfig config =
                new WifiEnterpriseConfig();

        config.setIdentity(
                pendingConnection.getIdentity());

        config.setPassword(
                pendingConnection.getPassword());

        config.setEapMethod(
                WifiEnterpriseConfig.Eap.PEAP);

        config.setPhase2Method(
                WifiEnterpriseConfig.Phase2.MSCHAPV2);

        config.setDomainSuffixMatch(DOMAIN);

        return config;
    }

    private void connectWithTofu() {

        try {
            WifiEnterpriseConfig config = makeConfig();

            if (Build.VERSION.SDK_INT >= 33) {
                config.enableTrustOnFirstUse(true);
            }

            showMessage("TOFU enabled.");

            registerSuggestion(config);

        } catch (Exception e) {
            Log.e(TAG, "TOFU failed", e);
            showMessage("TOFU error: " + e.getMessage());
            selectSystemCertificate();
        }
    }

    private void selectSystemCertificate() {

        final List<X509Certificate> certificates =
                new ArrayList<>();

        final List<String> labels =
                new ArrayList<>();

        try {
            KeyStore store =
                    KeyStore.getInstance("AndroidCAStore");

            store.load(null);

            Enumeration<String> aliases = store.aliases();

            while (aliases.hasMoreElements()) {

                String alias = aliases.nextElement();

                java.security.cert.Certificate cert =
                        store.getCertificate(alias);

                if (cert instanceof X509Certificate) {

                    X509Certificate ca =
                            (X509Certificate) cert;

                    if (ca.getBasicConstraints() >= 0) {

                        certificates.add(ca);

                        labels.add(
                                ca.getSubjectX500Principal()
                                        .getName());
                    }
                }
            }

        } catch (Exception e) {
            showMessage("CA loading failed.");
            return;
        }

        if (certificates.isEmpty()) {
            showMessage("No CA certificates found.");
            return;
        }

        final int[] selected = {-1};

        AlertDialog dialog =
                new AlertDialog.Builder(requireActivity())
                        .setTitle("Select verified CA")
                        .setSingleChoiceItems(
                                labels.toArray(new String[0]),
                                -1,
                                (d, index) ->
                                        selected[0] = index)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Next", null)
                        .create();

        dialog.setOnShowListener(d -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        if (selected[0] < 0) {
                            showMessage("Select a CA.");
                            return;
                        }

                        X509Certificate ca =
                                certificates.get(selected[0]);

                        dialog.dismiss();

                        confirmCertificate(ca);
                    });
        });

        dialog.show();
    }

    private void confirmCertificate(X509Certificate ca) {

        new AlertDialog.Builder(requireActivity())
                .setTitle("Confirm trusted CA")
                .setMessage(
                        "Domain: " + DOMAIN +
                        "\n\nCertificate:\n" +
                        ca.getSubjectX500Principal().getName() +
                        "\n\nProceed only if this is the " +
                        "verified CQUniversity CA.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Configure", (d, w) -> {

                    try {
                        ca.checkValidity();

                        WifiEnterpriseConfig config =
                                makeConfig();

                        config.setCaCertificate(ca);

                        registerSuggestion(config);

                    } catch (Exception e) {
                        showMessage(
                                "Certificate error: " +
                                e.getMessage());
                    }
                })
                .show();
    }

    private void registerSuggestion(
            WifiEnterpriseConfig config) {

        if (Build.VERSION.SDK_INT < 29) {
            showMessage("Android 10+ required.");
            return;
        }

        try {
            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion.Builder()
                            .setSsid(
                                    pendingConnection.getSsid())
                            .setWpa2EnterpriseConfig(config)
                            .build();

            if (activeSuggestion != null) {
                wifiManager.removeNetworkSuggestions(
                        Collections.singletonList(
                                activeSuggestion));
                activeSuggestion = null;
            }

            int result =
                    wifiManager.addNetworkSuggestions(
                            Collections.singletonList(
                                    suggestion));

            showMessage("Suggestion result: " + result);

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {

                activeSuggestion = suggestion;

                showMessage(
                        "Network suggestion accepted.");
                showMessage(
                        "Watch for Android approval prompt.");
                showMessage(
                        "Check Wi-Fi settings for connection.");

            } else {
                showMessage(
                        "Suggestion rejected: " + result);
            }

        } catch (Exception e) {

            Log.e(TAG, "Suggestion error", e);

            showMessage(
                    "Connection configuration error: " +
                    e.getClass().getSimpleName() +
                    ": " + e.getMessage());
        }
    }

    private void removeSuggestions() {

        if (Build.VERSION.SDK_INT < 29) {
            return;
        }

        try {
            int result;

            if (activeSuggestion != null) {
                result = wifiManager.removeNetworkSuggestions(
                        Collections.singletonList(
                                activeSuggestion));
            } else {
                result = wifiManager.removeNetworkSuggestions(
                        Collections.emptyList());
            }

            showMessage("Remove result: " + result);

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                activeSuggestion = null;
            }

        } catch (Exception e) {
            showMessage("Removal error: " + e.getMessage());
        }
    }

    public void loadSsids() {

        if (rootView == null || wifiManager == null) {
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

            List<String> networks = new ArrayList<>();

            for (ScanResult result : results) {

                if (result.SSID != null &&
                        result.capabilities != null &&
                        result.capabilities.contains("EAP") &&
                        !networks.contains(result.SSID)) {

                    networks.add(result.SSID);
                }
            }

            if (networks.size() == 1) {

                EditText input =
                        rootView.findViewById(R.id.ssid_edit);

                if (input != null) {
                    input.setText(networks.get(0));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Scan error", e);
        }
    }

    private void showMessage(String message) {

        if (rootView != null) {

            TextView logs =
                    rootView.findViewById(R.id.logs);

            if (logs != null) {
                logs.setText(
                        message + "\n\n" + logs.getText());
            }
        }

        Log.i(TAG, message);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        rootView = null;
        pendingConnection = null;
    }
}
