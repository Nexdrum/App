# Easiest phone-only build path

You do not need Android Studio just to get the first APK.

Use the included GitHub Actions workflow:

1. Extract this project ZIP on your phone.
2. Put the extracted project in a GitHub repository, preserving the folders exactly.
3. Make sure `.github/workflows/build-apk.yml` is present in the repository.
4. Open the repository's **Actions** tab.
5. Choose **Build Spectra APK** and run it.
6. Download the `Spectra-Android-debug` artifact after the green check mark appears.
7. Extract `app-debug.apk` from that artifact and install it.

If the repository is public or private, the workflow is the same. The app itself does not need network access to run.
