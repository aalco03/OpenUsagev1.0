package com.openusage.app;

import java.util.List;

public interface FirebaseSettingsObserver{

    void onSettingsChanged(List <String> changedSettings);
}
