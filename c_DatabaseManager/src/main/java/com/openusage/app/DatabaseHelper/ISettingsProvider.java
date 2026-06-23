package com.openusage.app.DatabaseHelper;

/**
 * Interface for accessing user login and preference data.
 * Abstracts LogInPreference to enable unit testing with mock implementations.
 * 
 * Usage: Instead of `new LogInPreference(context).GetUserCode()`,
 * inject an ISettingsProvider and call `settingsProvider.getUserCode()`.
 */
public interface ISettingsProvider {

    String getUserSubjId();
    String getUserCode();
    String getUserNumber();
    String getUserEmail();
    String getUserPassword();
    String getInstallCode();

    void setUserSubjId(String subjId);
    void setUserCode(String code);
    void setUserNumber(String number);
    void setUserEmail(String email);
    void setUserPassword(String password);
    void setInstallCode(String installCode);

    void addDemographicData(String ageRange, String gender, String city,
                            String socialMediaUsage, String screenTimeEstimate);

    boolean isLoggedIn();
    void clear();
}
