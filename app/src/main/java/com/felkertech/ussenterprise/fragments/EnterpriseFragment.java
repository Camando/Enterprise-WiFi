
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
import android.text.InputType;
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
                R.layout.activity_main,
                container,
                false);

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

        EditText ssid =
                rootView.findViewById(R.id.ssid_edit);

        ssid.setText("eduroam");

        showMessage("Enterprise Wi-Fi Build #6 ready.");
        showMessage("Certificate validation required.");

        return rootView;
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

    private void prepareConnection() {

        if (rootView == null || wifiManager == null) {
            return;
        }

        EditText ssidInput =
                rootView.findViewById(R.id.ssid_edit);

        EditText identityInput =
                rootView.findViewById(R.id.identity);

        EditText passwordInput =
                rootView.findViewById(R.id.password);

        String ssid = ssidInput.getText()
                .toString().trim();

        if (ssid.length() >= 2 &&
                ssid.startsWith("\"") &&
                ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        String identity = identityInput.getText()
                .toString().trim();

        String password = passwordInput.getText()
                .toString();

        if (ssid.isEmpty() || identity.isEmpty()
                || password.isEmpty()) {

            showMessage(
                    "Enter SSID, identity and password.");
            return;
        }

        if (selectedEap != WifiEnterpriseConfig.Eap.PEAP
                || selectedPhase2 !=
                WifiEnterpriseConfig.Phase2.MSCHAPV2) {

            showMessage(
                    "Only PEAP/MSCHAPV2 is supported.");
            return;
        }

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.Q) {

            showMessage("Android 10+ required.");
            return;
        }

        try {
            EnterpriseWifiConnection connection =
                    new EnterpriseWifiConnection.Builder()
                            .setSsid(ssid)
                            .setIdentity(identity)
                            .setPassword(password)
                            .setEap(selectedEap)
                            .setPhase2(selectedPhase2)
                            .build();

            requestServerDomain(connection);

        } catch (Exception e) {
            showMessage("Invalid connection settings.");
        }
    }

    private void requestServerDomain(
            EnterpriseWifiConnection connection) {

        EditText input =
                new EditText(requireActivity());

        input.setSingleLine(true);

        input.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI);

        input.setHint("Server domain");

        new AlertDialog.Builder(requireActivity())
                .setTitle("Authentication server domain")
                .setMessage(
                        "Enter the official authentication " +
                        "server domain supplied by your institution.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Next",
                        (dialog, which) -> {

                            String domain = input.getText()
                                    .toString().trim();

                            if (domain.isEmpty()
                                    || domain.contains(" ")
                                    || !domain.contains(".")) {

                                showMessage(
                                        "Valid server domain required.");
                                return;
                            }

                            selectCertificate(
                                    connection, domain);
                        })
                .show();
    }

    private void selectCertificate(
            EnterpriseWifiConnection connection,
            String domain) {

        final List<X509Certificate> certificates =
                new ArrayList<>();

        final List<String> labels =
                new ArrayList<>();

        try {
            KeyStore store =
                    KeyStore.getInstance("AndroidCAStore");

            store.load(null);

            Enumeration<String> aliases =
                    store.aliases();

            while (aliases.hasMoreElements()) {

                String alias = aliases.nextElement();

                java.security.cert.Certificate cert =
                        store.getCertificate(alias);

                if (cert instanceof X509Certificate) {

                    X509Certificate x509 =
                            (X509Certificate) cert;

                    certificates.add(x509);

                    String label =
                            x509.getSubjectX500Principal()
                                    .getName();

                    labels.add(label);
                }
            }

        } catch (Exception e) {

            Log.e(TAG, "Certificate loading failed", e);

            showMessage(
                    "Unable to load trusted certificates.");
            return;
        }

        if (certificates.isEmpty()) {

            showMessage(
                    "No system CA certificates available.");
            return;
        }

        // Android AlertDialog may hide list items
        // when setMessage() and setItems() are combined.
        // Build #6 uses a dedicated single-choice list.

        String[] items =
                labels.toArray(new String[0]);

        final int[] selected = {-1};

        AlertDialog dialog =
                new AlertDialog.Builder(requireActivity())
                        .setTitle("Select trusted CA certificate")
                        .setSingleChoiceItems(
                                items,
                                -1,
                                (d, which) -> {
                                    selected[0] = which;
                                })
                        .setNegativeButton(
                                "Cancel", null)
                        .setPositiveButton(
                                "Next", null)
                        .create();

        dialog.setOnShowListener(d -> {

            if (dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE) == null) {
                return;
            }

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        int index = selected[0];

                        if (index < 0 ||
                                index >= certificates.size()) {

                            showMessage(
                                    "Select a certificate first.");
                            return;
                        }

                        X509Certificate certificate =
                                certificates.get(index);

                        dialog.dismiss();

                        confirmConfiguration(
                                connection,
                                domain,
                                certificate);
                    });
        });

        dialog.show();
    }

    private void confirmConfiguration(
            EnterpriseWifiConnection connection,
            String domain,
            X509Certificate certificate) {

        String subject = certificate
                .getSubjectX500Principal()
                .getName();

        String issuer = certificate
                .getIssuerX500Principal()
                .getName();

        String message =
                "Network: " + connection.getSsid() +
                "\nServer domain: " + domain +
                "\n\nCertificate subject:\n" + subject +
                "\n\nCertificate issuer:\n" + issuer +
                "\n\nOnly configure if the domain " +
                "and CA are confirmed by your institution.";

        new AlertDialog.Builder(requireActivity())
                .setTitle("Confirm enterprise configuration")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Configure",
                        (dialog, which) ->
                                addSuggestion(
                                        connection,
                                        domain,
                                        certificate))
                .show();
    }

    private void addSuggestion(
            EnterpriseWifiConnection connection,
            String domain,
            X509Certificate certificate) {

        if (wifiManager == null) {
            showMessage("Wi-Fi service unavailable.");
            return;
        }

        try {
            WifiEnterpriseConfig config =
                    new WifiEnterpriseConfig();

            config.setIdentity(
                    connection.getIdentity());

            config.setPassword(
                    connection.getPassword());

            config.setEapMethod(
                    WifiEnterpriseConfig.Eap.PEAP);

            config.setPhase2Method(
                    WifiEnterpriseConfig.Phase2.MSCHAPV2);

            config.setDomainSuffixMatch(domain);

            config.setCaCertificate(certificate);

            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion.Builder()
                            .setSsid(connection.getSsid())
                            .setWpa2EnterpriseConfig(config)
                            .build();

            int status =
                    wifiManager.addNetworkSuggestions(
                            Collections.singletonList(
                                    suggestion));

            if (status ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {

                showMessage(
                        "Network suggestion registered.");
                showMessage(
                        "Android approval may be required.");

            } else {

                showMessage(
                        "Suggestion rejected. Code: " + status);
            }

        } catch (Exception e) {

            Log.e(TAG, "Wi-Fi configuration failed", e);

            showMessage(
                    "Configuration failed. Check permissions, " +
                    "certificate and server domain.");
        }
    }

    private void removeSuggestions() {

        if (wifiManager == null ||
                Build.VERSION.SDK_INT <
                Build.VERSION_CODES.Q) {
            return;
        }

        try {
            int result =
                    wifiManager.removeNetworkSuggestions(
                            Collections.emptyList());

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {

                showMessage(
                        "This app's network suggestions removed.");

            } else {

                showMessage(
                        "Removal failed: " + result);
            }

        } catch (Exception e) {

            Log.e(TAG, "Removal error", e);

            showMessage(
                    "Unable to remove network suggestions.");
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

            List<String> networks =
                    new ArrayList<>();

            for (ScanResult result : results) {

                if (result.SSID != null
                        && result.capabilities != null
                        && result.capabilities.contains("EAP")
                        && !networks.contains(result.SSID)) {

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

        } catch (SecurityException e) {

            showMessage(
                    "Wi-Fi scanning permission denied.");

        } catch (Exception e) {

            Log.e(TAG, "Wi-Fi scan error", e);
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
    }
}
