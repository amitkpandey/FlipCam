package com.flipcam;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.flipcam.constants.Constants;
import com.flipcam.preferences.CustomCheckboxPreference;
import com.flipcam.preferences.ResolutionListPreference;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class VideoSettingsActivity extends AppCompatActivity {

    public static final String TAG = "VideoSettingsActivity";
    static boolean VERBOSE = true;
    static Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(VERBOSE)Log.d(TAG, "onCreate");
        mContext = getApplicationContext();
        getFragmentManager().beginTransaction().replace(android.R.id.content, new VideoSettingFragment()).commit();
    }

    public static class VideoSettingFragment extends PreferenceFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View rootView = super.onCreateView(inflater, container, savedInstanceState);
            rootView.setBackgroundColor(getResources().getColor(R.color.settingsBarColor));
            return rootView;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (VERBOSE) Log.d(TAG, "VideoSettingFragment onCreate");
            addPreferencesFromResource(R.xml.preferences);
            Resources resources = getActivity().getResources();
            ListPreference listPreference = new ResolutionListPreference(getActivity(), true);
            Set<String> resSizes = new LinkedHashSet<>();
            resSizes.add(resources.getString(R.string.videoResHigh));
            resSizes.add(resources.getString(R.string.videoResMedium));
            resSizes.add(resources.getString(R.string.videoResLow));
            CharSequence[] resEntries = new CharSequence[resSizes.size()];
            int index = 0;
            Iterator<String> resolIter = resSizes.iterator();
            while (resolIter.hasNext()) {
                String resol = resolIter.next();
                resEntries[index++] = resol;
            }
            SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
            listPreference.setEntries(resEntries);
            listPreference.setEntryValues(resEntries);
            listPreference.setTitle(resources.getString(R.string.videoResolutionHeading));
            listPreference.setSummary(resources.getString(R.string.videoResolutionSummary));
            listPreference.setKey(Constants.SELECT_VIDEO_RESOLUTION);
            listPreference.setValue(settingsPrefs.getString(Constants.SELECT_VIDEO_RESOLUTION, null));
            listPreference.setDialogTitle(getResources().getString(R.string.videoResolutionHeading));
            listPreference.setLayoutResource(R.layout.custom_photo_setting);
            getPreferenceScreen().addPreference(listPreference);
            listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String newRes = (String) newValue;
                    Log.d(TAG, "onPreferenceChange = " + newRes);
                    Log.d(TAG, "onPreferenceChange pref = " + preference.getKey());
                    return true;
                }
            });
            final CheckBoxPreference memoryConsumedPref = new CustomCheckboxPreference(getActivity(), true, Constants.SHOW_MEMORY_CONSUMED_MSG);
            memoryConsumedPref.setTitle(resources.getString(R.string.showMemConsumed));
            memoryConsumedPref.setSummary(resources.getString(R.string.showMemConsumedMsg));
            memoryConsumedPref.setKey(Constants.SHOW_MEMORY_CONSUMED_MSG);
            memoryConsumedPref.setLayoutResource(R.layout.custom_checkbox_setting);
            boolean memCon = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(Constants.SHOW_MEMORY_CONSUMED_MSG, false);
            if(VERBOSE)Log.d(TAG, "MEMORY CONSUMED PREF MGR = "+memCon);
            getPreferenceScreen().addPreference(memoryConsumedPref);
        }
    }
}
