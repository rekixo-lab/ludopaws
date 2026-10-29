# Android's FragmentManager restores this popup by class name and public no-arg
# constructor after process death. Preserve that reflection entry in release.
-keep class com.rekixo.ludopaws.feature.safety.FamilyPrivacyDialog {
    public <init>();
}
