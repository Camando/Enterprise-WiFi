
package com.felkertech.ussenterprise.fragments;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class EnterpriseFragment extends Fragment {

    private static final String TAG = "EnterpriseWifi";
    private static final int PICK_CA_FILE = 7101;
    private static final String DEFAULT_SSID = "eduroam";
    private static final String DEFAULT_DOMAIN = "cqu.edu.au";

    private View rootView;
    private WifiManager wifiManager;

    private EnterpriseWifiConnection pendingConnection;
    private String pendingDomain;

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

        EditText ssid =
                rootView.findViewById(R.id.ssid_edit);

        ssid.setText(DEFAULT_SSID);

        showMessage("Enterprise Wi-Fi Build #8 ready.");
        showMessage("Default domain: " + DEFAULT_DOMAIN);
        showMessage("CA certificate validation required.");

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

        if (rootView == null || wifiManager == null) {
            return;
        }

        EditText ssidField =
                rootView.findViewById(R.id.ssid_edit);
        EditText identityField =
                rootView.findViewById(R.id.identity);
        EditText passwordField =
                rootView.findViewById(R.id.password);

        String ssid = ssidField.getText().toString().trim();

        if (ssid.length() >= 2 &&
                ssid.startsWith("\"") &&
                ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }

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
            showMessage("Only PEAP/MSCHAPV2 supported.");
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showMessage("Android 10 or newer required.");
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

        requestServerDomain();
    }

    private void requestServerDomain() {

        EditText input = new EditText(requireActivity());
        input.setSingleLine(true);

        input.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI);

        input.setText(DEFAULT_DOMAIN);
        input.setSelection(input.length());

        new AlertDialog.Builder(requireActivity())
                .setTitle("Server certificate domain")
                .setMessage(
                        "Confirm the authentication server domain. " +
                        "For CQUniversity, the default is " +
                        DEFAULT_DOMAIN + ".")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (dialog, which) -> {

                    String domain =
                            input.getText().toString().trim();

                    if (domain.isEmpty() ||
                            domain.contains(" ") ||
                            !domain.contains(".")) {
                        showMessage("Valid server domain required.");
                        return;
                    }

                    pendingDomain = domain;
                    chooseCertificateSource();
                })
                .show();
    }

    private void chooseCertificateSource() {

        String[] options = {
                "Import CA certificate file",
                "Choose Android trusted CA"
        };

        new AlertDialog.Builder(requireActivity())
                .setTitle("Certificate source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        importCertificate();
                    } else {
                        selectSystemCertificate();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importCertificate() {

        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        try {
            startActivityForResult(intent, PICK_CA_FILE);
        } catch (ActivityNotFoundException e) {
            showMessage(
                    "No file picker installed on this TV.");
        }
    }

    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_CA_FILE ||
                resultCode != Activity.RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        if (pendingConnection == null ||
                pendingDomain == null) {
            showMessage("Start configuration again.");
            return;
        }

        try {
            Uri uri = data.getData();

            CertificateFactory factory =
                    CertificateFactory.getInstance("X.509");

            X509Certificate certificate;

            try (InputStream stream =
                    requireActivity()
                            .getContentResolver()
                            .openInputStream(uri)) {

                if (stream == null) {
                    showMessage("Unable to open certificate file.");
                    return;
                }

                certificate = (X509Certificate)
                        factory.generateCertificate(stream);
            }

            validateCertificate(certificate);
            confirmConfiguration(certificate);

        } catch (Exception e) {
            Log.e(TAG, "Certificate import failed", e);
            showMessage(
                    "Unable to import a valid CA certificate.");
        }
    }

    private void validateCertificate(
            X509Certificate certificate) throws Exception {

        certificate.checkValidity();

        if (certificate.getBasicConstraints() < 0) {
            throw new IllegalArgumentException(
                    "The selected file is not a CA certificate.");
        }
    }

    private void selectSystemCertificate() {

        final List<X509Certificate> certificates =
                new ArrayList<>();

        final List<String> labels = new ArrayList<>();

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

                    X509Certificate x509 =
                            (X509Certificate) cert;

                    if (x509.getBasicConstraints() >= 0) {
                        certificates.add(x509);

                        labels.add(
                                x509.getSubjectX500Principal()
                                        .getName());
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "CA loading failed", e);
            showMessage("Unable to load CA certificates.");
            return;
        }

        if (certificates.isEmpty()) {
            showMessage("No trusted CA certificates found.");
            return;
        }

        final int[] selected = {-1};

        AlertDialog dialog =
                new AlertDialog.Builder(requireActivity())
                        .setTitle("Select trusted CA certificate")
                        .setSingleChoiceItems(
                                labels.toArray(new String[0]),
                                -1,
                                (d, index) -> selected[0] = index)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Next", null)
                        .create();

        dialog.setOnShowListener(d -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        if (selected[0] < 0) {
                            showMessage("Select a certificate.");
                            return;
                        }

                        X509Certificate certificate =
                                certificates.get(selected[0]);

                        dialog.dismiss();

                        try {
                            validateCertificate(certificate);
                            confirmConfiguration(certificate);
                        } catch (Exception e) {
                            showMessage(
                                    "Certificate expired or invalid.");
                        }
                    });
        });

        dialog.show();
    }

    private void confirmConfiguration(
            X509Certificate certificate) {

        if (pendingConnection == null ||
                pendingDomain == null) {
            showMessage("Configuration session expired.");
            return;
        }

        String subject =
                certificate.getSubjectX500Principal().getName();

        String issuer =
                certificate.getIssuerX500Principal().getName();

        new AlertDialog.Builder(requireActivity())
                .setTitle("Confirm configuration")
                .setMessage(
                        "Network: " + pendingConnection.getSsid() +
                        "\n\nServer domain: " + pendingDomain +
                        "\n\nCA subject:\n" + subject +
                        "\n\nCA issuer:\n" + issuer +
                        "\n\nOnly continue if these match " +
                        "your institution's official settings.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Configure", (d, which) ->
                        addSuggestion(certificate))
                .show();
    }

    private void addSuggestion(
            X509Certificate certificate) {

        if (wifiManager == null ||
                pendingConnection == null ||
                pendingDomain == null) {
            showMessage("Configuration unavailable.");
            return;
        }

        try {
            validateCertificate(certificate);

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

            config.setDomainSuffixMatch(pendingDomain);
            config.setCaCertificate(certificate);

            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion.Builder()
                            .setSsid(pendingConnection.getSsid())
                            .setWpa2EnterpriseConfig(config)
                            .build();

            int result = wifiManager.addNetworkSuggestions(
                    Collections.singletonList(suggestion));

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                showMessage("Network suggestion registered.");
                showMessage(
                        "Approve network if Android prompts.");
            } else {
                showMessage(
                        "Network suggestion failed: " + result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Configuration failed", e);
            showMessage(
                    "Unable to configure enterprise Wi-Fi.");
        }
    }

    private void removeSuggestions() {

        if (wifiManager == null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        try {
            int result = wifiManager.removeNetworkSuggestions(
                    Collections.emptyList());

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                showMessage(
                        "App network suggestions removed.");
            } else {
                showMessage(
                        "Removal failed: " + result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Removal failed", e);
            showMessage("Unable to remove suggestions.");
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

        } catch (SecurityException e) {
            showMessage("Wi-Fi scan permission denied.");
        } catch (Exception e) {
            Log.e(TAG, "Scan failed", e);
        }
    }

    private void showMessage(String message) {

        if (rootView != null) {

            TextView logs = rootView.findViewById(R.id.logs);

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
        pendingDomain = null;
    }
}
