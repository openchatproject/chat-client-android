package com.openchat.secureim.preferences;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.openchat.secureim.PassphraseRequiredActionBarActivity;
import com.openchat.secureim.R;
import com.openchat.secureim.components.CustomDefaultPreference;
import com.openchat.secureim.database.ApnDatabase;
import com.openchat.secureim.mms.LegacyMmsConnection;
import com.openchat.secureim.util.TelephonyUtil;
import com.openchat.secureim.util.TextSecurePreferences;

import java.io.IOException;


public class MmsPreferencesFragment extends CorrectedPreferenceFragment {

  private static final String TAG = MmsPreferencesFragment.class.getSimpleName();

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    ((PassphraseRequiredActionBarActivity) getActivity()).getSupportActionBar()
        .setTitle(R.string.preferences__advanced_mms_access_point_names);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_manual_mms);
  }

  @Override
  public void onResume() {
    super.onResume();
    new LoadApnDefaultsTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private class LoadApnDefaultsTask extends AsyncTask<Void, Void, LegacyMmsConnection.Apn> {

    @Override
    protected LegacyMmsConnection.Apn doInBackground(Void... params) {
      try {
        Context context = getActivity();

        if (context != null) {
          return ApnDatabase.getInstance(context)
                            .getDefaultApnParameters(TelephonyUtil.getMccMnc(context),
                                                     TelephonyUtil.getApn(context));
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return null;
    }

    @Override
    protected void onPostExecute(LegacyMmsConnection.Apn apnDefaults) {
      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_HOST_PREF))
          .setValidator(new CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.UriValidator())
          .setDefaultValue(apnDefaults.getMmsc());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PROXY_HOST_PREF))
          .setValidator(new CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.HostnameValidator())
          .setDefaultValue(apnDefaults.getProxy());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PROXY_PORT_PREF))
          .setValidator(new CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.PortValidator())
          .setDefaultValue(apnDefaults.getPort());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_USERNAME_PREF))
          .setDefaultValue(apnDefaults.getPort());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMSC_PASSWORD_PREF))
          .setDefaultValue(apnDefaults.getPassword());

      ((CustomDefaultPreference)findPreference(TextSecurePreferences.MMS_USER_AGENT))
          .setDefaultValue(LegacyMmsConnection.USER_AGENT);
    }
  }

}
