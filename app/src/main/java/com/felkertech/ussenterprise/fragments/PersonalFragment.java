
package com.felkertech.ussenterprise.fragments;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.felkertech.ussenterprise.R;
import com.felkertech.ussenterprise.activities.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class PersonalFragment extends Fragment {

    private static final String TAG = "PersonalFragment";

    private View rootView;

    public PersonalFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        rootView = inflater.inflate(
                R.layout.fragment_house_wifi,
                container,
                false
        );

        View button = rootView.findViewById(R.id.ssid_list);

        if (button != null) {
            button.setOnClickListener(v -> {
                if (getActivity() == null) {
                    return;
                }

                List<String> ssids = getSsids();

                new AlertDialog.Builder(
                        getActivity(),
                        R.style.MyDialogTheme
                )
                .setTitle(R.string.choose_wifi_ssid)
                .setItems(
                        ssids.toArray(new CharSequence[0]),
                        (dialog, which) -> {
                            if (which >= 0 && which < ssids.size()) {
                                EditText input = rootView.findViewById(
                                        R.id.ssid_edit
                                );

                                if (input != null) {
                                    input.setText(ssids.get(which));
                                }
                            }
                        }
                )
                .show();
            });
        }

        printSavedWifiNetworks();

        return rootView;
    }

    public void loadSsids() {
        if (rootView == null) {
            return;
        }

        List<String> ssids = getSsids();

        if (ssids.size() == 1) {
            EditText input = rootView.findViewById(R.id.ssid_edit);

            if (input != null) {
                input.setText(ssids.get(0));
            }
        }
    }

    public void printSavedWifiNetworks() {
        if (rootView == null || getActivity() == null) {
            return;
        }

        TextView wifiList = rootView.findViewById(R.id.wifi_list);

        if (wifiList == null) {
            Log.w(TAG, "Wi-Fi list view unavailable");
            return;
        }

        wifiList.setText("");

        WifiManager wifiManager =
                (WifiManager) getActivity()
                        .getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);

        if (wifiManager == null) {
            wifiList.setText("Wi-Fi service unavailable");
            return;
        }

        try {
            List<WifiConfiguration> networks =
                    wifiManager.getConfiguredNetworks();

            if (networks == null || networks.isEmpty()) {
                wifiList.setText(
                        "Saved networks are unavailable or restricted."
                );
                return;
            }

            StringBuilder result = new StringBuilder();

            for (WifiConfiguration network : networks) {
                if (network != null && network.SSID != null) {
                    result.append(network.SSID).append("\n");
                }
            }

            wifiList.setText(result.toString());

        } catch (SecurityException e) {
            Log.w(TAG, "Cannot access saved networks", e);
            wifiList.setText(
                    "Android restricts access to saved networks."
            );

        } catch (Exception e) {
            Log.e(TAG, "Error loading networks", e);
            wifiList.setText("Unable to load saved networks.");
        }
    }

    private List<String> getSsids() {
        List<String> ssids = new ArrayList<>();

        if (getActivity() == null) {
            return ssids;
        }

        MainActivity activity = (MainActivity) getActivity();

        if (!activity.grantedLocationPermission()) {
            activity.requestLocationPermission();
            return ssids;
        }

        WifiManager wifiManager =
                (WifiManager) activity
                        .getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);

        if (wifiManager == null) {
            return ssids;
        }

        try {
            List<ScanResult> results = wifiManager.getScanResults();

            if (results != null) {
                for (ScanResult result : results) {
                    if (result.SSID != null
                            && !result.SSID.isEmpty()
                            && !ssids.contains(result.SSID)) {

                        ssids.add(result.SSID);
                    }
                }
            }

        } catch (SecurityException e) {
            Log.w(TAG, "Wi-Fi scan permission denied", e);

        } catch (Exception e) {
            Log.e(TAG, "Wi-Fi scan failed", e);
        }

        return ssids;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rootView = null;
    }
}
