package com.tau.nexus_note.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tau.nexus_note.utils.getHomeDirectoryPath
import java.io.File

// JVM implementation that saves the settings file in the user's home directory
actual fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create {
        // --- THIS IS THE FIX ---
        // Just return the file, don't use 'produceFile'
        File(getHomeDirectoryPath(), "nexus_note_settings.preferences_pb")
        // --- END FIX ---
    }
}