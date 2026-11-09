
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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
    private int selectedEap = WifiEnterpriseConfig.Eap.PEAP;
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

        Spinner eap = rootView.findViewById(R.id.eap);
        Spinner phase2 = rootView.findViewById(R.id.phase2);

        eap.setAdapter(new EapSpinnerAdapter(getActivity()));
        phase2.setAdapter(new Phase2SpinnerAdapter(getActivity()));

        eap.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view, int position, long id) {
                        selectedEap = (int) id;
                    }

                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {}
                });

        phase2.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view, int position, long id) {
                        selectedPhase2 = (int) id;
                    }

                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent) {}
                });

        rootView.findViewById(R.id.button_connect)
                .setOnClickListener(v -> prepareConnection());

        rootView.findViewById(R.id.button_disconnect)
                .setOnClickListener(v -> removeSuggestions());

        rootView.findViewById(R.id.button_connect_preset)
                .setVisibility(View.GONE);

        EditText ssid = rootView.findViewById(R.id.ssid_edit);
        ssid.setText("eduroam");

        showMessage("Android TV enterprise Wi-Fi ready.");
        showMessage("Certificate validation is required.");

        return rootView;
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

        String ssid = ssidInput.getText().toString()
                .trim().replaceAll("^\"|\"$", "");
        String identity = identityInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (ssid.isEmpty() || identity.isEmpty()
                || password.isEmpty()) {
            showMessage("Enter SSID, identity and password.");
            return;
        }

        if (selectedEap != WifiEnterpriseConfig.Eap.PEAP ||
                selectedPhase2 !=
                        WifiEnterpriseConfig.Phase2.MSCHAPV2) {
            showMessage("This test build supports PEAP/MSCHAPV2.");
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showMessage("Android 10 or newer is required.");
            return;
        }

        EnterpriseWifiConnection connection =
                new EnterpriseWifiConnection.Builder()
                        .setSsid(ssid)
                        .setIdentity(identity)
                        .setPassword(password)
                        .setEap(selectedEap)
                        .setPhase2(selectedPhase2)
                        .build();

        requestServerDomain(connection);
    }

    private void requestServerDomain(
            EnterpriseWifiConnection connection) {

        EditText input = new EditText(requireActivity());
        input.setSingleLine(true);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("Authentication server domain");

        new AlertDialog.Builder(requireActivity())
                .setTitle("Server certificate domain")
                .setMessage(
                        "Enter the authentication server domain " +
                        "supplied by your institution. " +
                        "This is not necessarily your email domain.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (dialog, which) -> {
                    String domain = input.getText()
                            .toString().trim();

                    if (domain.isEmpty() ||
                            domain.contains(" ") ||
                            !domain.contains(".")) {
                        showMessage("A valid server domain is required.");
                        return;
                    }

                    selectCertificate(connection, domain);
                })
                .show();
    }

    private void selectCertificate(
            EnterpriseWifiConnection connection,
            String domain) {

        List<String> aliases = new ArrayList<>();
        List<X509Certificate> certificates = new ArrayList<>();

        try {
            KeyStore store =
                    KeyStore.getInstance("AndroidCAStore");
            store.load(null);

            Enumeration<String> names = store.aliases();

            while (names.hasMoreElements()) {
                String alias = names.nextElement();

                java.security.cert.Certificate cert =
                        store.getCertificate(alias);

                if (cert instanceof X509Certificate) {
                    aliases.add(alias);
                    certificates.add((X509Certificate) cert);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Certificate store unavailable", e);
            showMessage("Unable to read trusted certificates.");
            return;
        }

        if (certificates.isEmpty()) {
            showMessage("No trusted CA certificates available.");
            return;
        }

        String[] labels = new String[certificates.size()];

        for (int i = 0; i < certificates.size(); i++) {
            labels[i] = aliases.get(i) + "\n" +
                    certificates.get(i).getSubjectX500Principal()
                            .getName();
        }

        new AlertDialog.Builder(requireActivity())
                .setTitle("Select trusted CA certificate")
                .setMessage(
                        "Only choose the CA confirmed by your " +
                        "institution. Do not guess.")
                .setItems(labels, (dialog, index) -> {
                    confirmConfiguration(
                            connection,
                            domain,
                            certificates.get(index));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmConfiguration(
            EnterpriseWifiConnection connection,
            String domain,
            X509Certificate certificate) {

        new AlertDialog.Builder(requireActivity())
                .setTitle("Confirm enterprise Wi-Fi")
                .setMessage(
                        "Network: " + connection.getSsid() +
                        "\nServer domain: " + domain +
                        "\nCA issuer: " +
                        certificate.getIssuerX500Principal().getName() +
                        "\n\nOnly continue if both certificate " +
                        "and domain match the official configuration.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Configure", (dialog, which) ->
                        addSuggestion(connection, domain, certificate))
                .show();
    }

    private void addSuggestion(
            EnterpriseWifiConnection connection,
            String domain,
            X509Certificate certificate) {

        try {
            WifiEnterpriseConfig config =
                    new WifiEnterpriseConfig();

            config.setIdentity(connection.getIdentity());
            config.setPassword(connection.getPassword());
            config.setEapMethod(WifiEnterpriseConfig.Eap.PEAP);
            config.setPhase2Method(
                    WifiEnterpriseConfig.Phase2.MSCHAPV2);
            config.setDomainSuffixMatch(domain);
            config.setCaCertificate(certificate);

            WifiNetworkSuggestion suggestion =
                    new WifiNetworkSuggestion.Builder()
                            .setSsid(connection.getSsid())
                            .setWpa2EnterpriseConfig(config)
                            .build();

            int result = wifiManager.addNetworkSuggestions(
                    Collections.singletonList(suggestion));

            if (result ==
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                showMessage("Network suggestion added.");
                showMessage(
                        "Approve the connection if Android prompts.");
            } else {
                showMessage("Wi-Fi suggestion failed: " + result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Enterprise Wi-Fi setup failed", e);
            showMessage(
                    "Configuration failed. Check the certificate " +
                    "and Android permissions.");
        }
    }

    private void removeSuggestions() {
        if (wifiManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }

        try {
            int result = wifiManager.removeNetworkSuggestions(
                    Collections.emptyList());

            showMessage(
                    result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
                            ? "This app's suggestions removed."
                            : "Removal failed: " + result);
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
            MainActivity activity = (MainActivity) getActivity();

            if (activity == null) {
                return;
            }

            if (!activity.grantedLocationPermission()) {
                activity.requestLocationPermission();
                return;
            }

            List<ScanResult> results = wifiManager.getScanResults();

            if (results == null) {
                return;
            }

            List<String> networks = new ArrayList<>();

            for (ScanResult result : results) {
                if (result.SSID != null &&
                        result.capabilities.contains("EAP") &&
                        !networks.contains(result.SSID)) {
                    networks.add(result.SSID);
                }
            }

            if (networks.size() == 1) {
                EditText input =
                        rootView.findViewById(R.id.ssid_edit);
                input.setText(networks.get(0));
            }

        } catch (SecurityException e) {
            showMessage("Wi-Fi scanning permission denied.");
        } catch (Exception e) {
            Log.e(TAG, "Network scan failed", e);
        }
    }

    private void showMessage(String message) {
        if (rootView != null) {
            TextView logs = rootView.findViewById(R.id.logs);

            if (logs != null) {
                logs.setText(message + "\n\n" + logs.getText());
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
