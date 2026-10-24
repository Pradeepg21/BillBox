// Top-level build file. Cloud sync (Supabase or Firebase) is added in a later phase; v1/v2 run fully offline.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
