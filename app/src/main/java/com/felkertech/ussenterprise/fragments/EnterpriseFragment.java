
package com.felkertech.ussenterprise.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
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

public class EnterpriseFragment extends Fragment {

    private static final String TAG = "EnterpriseWifi";
    private static final String DOMAIN = "cqu.edu.au";
    private static final String SSID = "eduroam";
    private static final int LOCATION_REQUEST = 1201;

    private static final String CA_FINGERPRINT =
            "8FE4FB0AF93A4D0D67DB0BEBB23E37C7" +
            "1BF325DCBCDD240EA04DAF58B47E1840";

    private View rootView;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int selectedEap = WifiEnterpriseConfig.Eap.PEAP;
    private int selectedPhase2 =
            WifiEnterpriseConfig.Phase2.MSCHAPV2;

    private boolean requestActive = false;

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

        Context appContext =
                requireActivity().getApplicationContext();

        wifiManager = (WifiManager)
                appContext.getSystemService(
                        Context.WIFI_SERVICE);

        connectivityManager = (ConnectivityManager)
                appContext.getSystemService(
                        Context.CONNECTIVITY_SERVICE);

        setupSpinners();

        rootView.findViewById(R.id.button_connect)
                .setOnClickListener(v -> connect());

        rootView.findViewById(R.id.button_disconnect)
                .setOnClickListener(v -> disconnect());

        rootView.findViewById(
                R.id.button_connect_preset)
                .setVisibility(View.GONE);

        EditText ssidField =
                rootView.findViewById(R.id.ssid_edit);

        ssidField.setText(SSID);

        log("Enterprise Wi-Fi Build #13 ready.");
        log("Direct eduroam connection test.");
        log("Server domain: " + DOMAIN);

        checkCertificate();

        return rootView;
    }

    @Override
    public void onStart() {

        super.onStart();

        checkLocationPermission();
        reportCurrentWifi();
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
                requestCode,
                permissions,
                grantResults);

        if (requestCode == LOCATION_REQUEST) {

            log(hasFineLocation()
                    ? "Precise Location: GRANTED."
                    : "Precise Location: DENIED.");

            reportCurrentWifi();
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

        byte[] hash =
                MessageDigest.getInstance("SHA-256")
                        .digest(certificate.getEncoded());

        StringBuilder result =
                new StringBuilder();

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

            Log.e(TAG, "Certificate lookup failed", e);

            log("CA error: " +
                    e.getClass().getSimpleName());
        }

        return null;
    }

    private void checkCertificate() {

        if (findCquCertificate() != null) {

            log("Certificate configuration ready.");

        } else {

            log("Connection requires valid CA.");
        }
    }

    private void reportCurrentWifi() {

        try {

            WifiInfo info = wifiManager.getConnectionInfo();

            if (info != null) {

                log("Default Wi-Fi SSID: " +
                        info.getSSID());

                log("Default Wi-Fi state: " +
                        info.getSupplicantState());
            }

        } catch (Exception e) {

            log("Wi-Fi status unavailable.");
        }
    }

    private void connect() {

        if (Build.VERSION.SDK_INT < 29) {

            log("Android 10 or newer required.");
            return;
        }

        if (!hasFineLocation()) {

            checkLocationPermission();
            return;
        }

        if (connectivityManager == null) {

            log("Connectivity service unavailable.");
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

        if (selectedEap != WifiEnterpriseConfig.Eap.PEAP ||
                selectedPhase2 !=
                        WifiEnterpriseConfig.Phase2.MSCHAPV2) {

            log("Select PEAP and MSCHAPV2.");
            return;
        }

        X509Certificate ca = findCquCertificate();

        if (ca == null) {

            log("Cannot connect: CA unavailable.");
            return;
        }

        disconnectRequest();

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

            WifiNetworkSpecifier specifier =
                    new WifiNetworkSpecifier.Builder()
                            .setSsid(ssid)
                            .setWpa2EnterpriseConfig(
                                    enterprise)
                            .build();

            NetworkRequest request =
                    new NetworkRequest.Builder()
                            .addTransportType(
                                    NetworkCapabilities.TRANSPORT_WIFI)
                            .removeCapability(
                                    NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .setNetworkSpecifier(specifier)
                            .build();

            networkCallback =
                    new ConnectivityManager.NetworkCallback() {

                @Override
                public void onAvailable(Network network) {

                    handler.post(() -> {

                        log("DIRECT CONNECTION AVAILABLE.");
                        log("Android supplied a Wi-Fi network.");
                        log("Network handle: " +
                                network.getNetworkHandle());

                        log("This is an app-specific network.");
                        log("It may not replace S26.");

                        reportCurrentWifi();
                    });
                }

                @Override
                public void onCapabilitiesChanged(
                        Network network,
                        NetworkCapabilities capabilities) {

                    handler.post(() -> {

                        if (Build.VERSION.SDK_INT >= 29) {

                            Object transport =
                                    capabilities.getTransportInfo();

                            if (transport instanceof WifiInfo) {

                                WifiInfo info =
                                        (WifiInfo) transport;

                                log("Requested network SSID: " +
                                        info.getSSID());

                                log("Requested network RSSI: " +
                                        info.getRssi() + " dBm");
                            }
                        }
                    });
                }

                @Override
                public void onUnavailable() {

                    handler.post(() -> {

                        requestActive = false;

                        log("DIRECT CONNECTION UNAVAILABLE.");
                        log("Android did not establish");
                        log("the requested connection.");
                        log("This alone does not prove");
                        log("the password is incorrect.");
                    });
                }

                @Override
                public void onLost(Network network) {

                    handler.post(() -> {

                        log("Requested Wi-Fi network lost.");
                    });
                }
            };

            log("Requesting direct eduroam connection.");
            log("PEAP/MSCHAPV2 configured.");
            log("CA and domain validation enabled.");
            log("Watch for an Android approval prompt.");

            requestActive = true;

            connectivityManager.requestNetwork(
                    request,
                    networkCallback,
                    60000);

        } catch (Exception e) {

            log("Direct request failed: " +
                    e.getClass().getSimpleName());

            log(String.valueOf(e.getMessage()));

            Log.e(TAG, "Direct connection error", e);

            disconnectRequest();
        }
    }

    private void disconnectRequest() {

        if (networkCallback == null) {
            return;
        }

        try {

            connectivityManager.unregisterNetworkCallback(
                    networkCallback);

        } catch (Exception e) {

            Log.w(TAG, "Callback removal failed", e);
        }

        networkCallback = null;
        requestActive = false;
    }

    private void disconnect() {

        disconnectRequest();

        log("Direct connection request released.");
        log("This does not remove saved networks.");

        reportCurrentWifi();
    }

    public void loadSsids() {

        if (rootView == null ||
                wifiManager == null) {
            return;
        }

        try {

            if (!hasFineLocation()) {

                checkLocationPermission();
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
                        rootView.findViewById(
                                R.id.ssid_edit);

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

        disconnectRequest();

        rootView = null;

        super.onDestroyView();
    }
}
