package com.slensky.focussis.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.slensky.focussis.util.CourseAssignmentFileHandler;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import com.slensky.focussis.R;
import com.slensky.focussis.activities.MainActivity;
import com.slensky.focussis.views.PasswordChangePreference;
import com.slensky.focussis.views.PasswordChangePreferenceDialogFragmentCompat;

/**
 * Created by slensky on 4/5/18.
 */

public class SettingsFragment extends PreferenceFragmentCompat implements PageFragment {
    private static final String TAG = "SettingsFragment";

    public SettingsFragment() {
        // required empty constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        Preference clearSavedAssignments = getPreferenceManager().findPreference("clear_all_saved_assignments");
        clearSavedAssignments.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.settings_clear_all_saved_assignments_warning)
                        .setPositiveButton(R.string.settings_clear_all_saved_assignemnts_positive, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Log.d(TAG, "Clearing all saved assignments");
                                CourseAssignmentFileHandler.clearAllSavedAssignments(getContext());
                                dialogInterface.dismiss();
                            }
                        }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        }).create()
                        .show();
                return true;
            }
        });

        Preference usernameDisplay = getPreferenceManager().findPreference("username_display");
        usernameDisplay.setSummary(((MainActivity) getActivity()).getUsername());
        usernameDisplay.setSelectable(false);

        return view;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // Try if the preference is one of our custom Preferences
        DialogFragment dialogFragment = null;
        if (preference instanceof PasswordChangePreference) {
            dialogFragment = PasswordChangePreferenceDialogFragmentCompat
                    .newInstance(preference.getKey());
        }

        // If it was one of our custom Preferences, show its dialog
        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(this.getFragmentManager(),
                    "android.support.v7.preference" +
                            ".PreferenceFragment.DIALOG");
        }
        // Could not be handled here. Try with the super method.
        else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public String getTitle() {
        return getString(R.string.settings_label);
    }

}
