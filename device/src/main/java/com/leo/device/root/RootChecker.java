package com.leo.device.root;

import android.content.Context;
import android.content.pm.PackageManager;

import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import static com.leo.device.root.Const.BINARY_BUSYBOX;
import static com.leo.device.root.Const.BINARY_SU;

/**
 * 检查设备是否被root
 */
public class RootChecker {

    private final Context mContext;
    private boolean loggingEnabled = true;

    private static RootChecker mInstance;

    public static RootChecker getInstance(Context context) {
        if (mInstance == null) {
            synchronized (RootChecker.class){
                if (mInstance == null){
                    mInstance = new RootChecker(context);
                }
            }
        }
        return mInstance;
    }

    private RootChecker(Context context) {
        mContext = context.getApplicationContext();
    }


    public boolean isRooted() {

        return detectRootManagementApps() || detectPotentiallyDangerousApps() || checkForBinary(BINARY_SU)
                || checkForDangerousProps() || checkForRWPaths()
                || detectTestKeys() || checkSuExists() || checkForRootNative() || checkForMagiskBinary();
    }




    /**
     * Release-Keys and Test-Keys has to do with how the kernel is signed when it is compiled.
     * Test-Keys means it was signed with a custom key generated by a third-party developer.
     *
     * @return true if signed with Test-keys
     */
    public boolean detectTestKeys() {
        String buildTags = android.os.Build.TAGS;

        return buildTags != null && buildTags.contains("test-keys");
    }

    /**
     * Using the PackageManager, check for a list of well known root apps. @link {Const.knownRootAppsPackages}
     *
     * @return true if one of the apps it's installed
     */
    public boolean detectRootManagementApps() {
        return detectRootManagementApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known root apps. @link {Const.knownRootAppsPackages}
     *
     * @param additionalRootManagementApps - array of additional packagenames to search for
     * @return true if one of the apps it's installed
     */
    public boolean detectRootManagementApps(String[] additionalRootManagementApps) {

        // Create a list of package names to iterate over from constants any others provided
        ArrayList<String> packages = new ArrayList<>(Arrays.asList(Const.knownRootAppsPackages));
        if (additionalRootManagementApps != null && additionalRootManagementApps.length > 0) {
            packages.addAll(Arrays.asList(additionalRootManagementApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Using the PackageManager, check for a list of well known apps that require root. @link {Const.knownRootAppsPackages}
     *
     * @return true if one of the apps it's installed
     */
    public boolean detectPotentiallyDangerousApps() {
        return detectPotentiallyDangerousApps(null);
    }

    /**
     * Using the PackageManager, check for a list of well known apps that require root. @link {Const.knownRootAppsPackages}
     *
     * @param additionalDangerousApps - array of additional packagenames to search for
     * @return true if one of the apps it's installed
     */
    public boolean detectPotentiallyDangerousApps(String[] additionalDangerousApps) {

        // Create a list of package names to iterate over from constants any others provided
        ArrayList<String> packages = new ArrayList<>();
        packages.addAll(Arrays.asList(Const.knownDangerousAppsPackages));
        if (additionalDangerousApps != null && additionalDangerousApps.length > 0) {
            packages.addAll(Arrays.asList(additionalDangerousApps));
        }

        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Using the PackageManager, check for a list of well known root cloak apps. @link {Const.knownRootAppsPackages}
     * and checks for native library read access
     *
     * @return true if one of the apps it's installed
     */
    public boolean detectRootCloakingApps() {
        return detectRootCloakingApps(null) || canLoadNativeLibrary() && !checkForNativeLibraryReadAccess();
    }

    /**
     * Using the PackageManager, check for a list of well known root cloak apps. @link {Const.knownRootAppsPackages}
     *
     * @param additionalRootCloakingApps - array of additional packagenames to search for
     * @return true if one of the apps it's installed
     */
    public boolean detectRootCloakingApps(String[] additionalRootCloakingApps) {

        // Create a list of package names to iterate over from constants any others provided
        ArrayList<String> packages = new ArrayList<>(Arrays.asList(Const.knownRootCloakingPackages));
        if (additionalRootCloakingApps != null && additionalRootCloakingApps.length > 0) {
            packages.addAll(Arrays.asList(additionalRootCloakingApps));
        }
        return isAnyPackageFromListInstalled(packages);
    }

    /**
     * Checks various (Const.suPaths) common locations for the SU binary
     *
     * @return true if found
     */
    public boolean checkForSuBinary() {
        return checkForBinary(BINARY_SU);
    }

    /**
     * Checks various (Const.suPaths) common locations for the magisk binary (a well know root level program)
     *
     * @return true if found
     */
    public boolean checkForMagiskBinary() {
        return checkForBinary("magisk");
    }

    /**
     * Checks various (Const.suPaths) common locations for the busybox binary (a well know root level program)
     *
     * @return true if found
     */
    public boolean checkForBusyBoxBinary() {
        return checkForBinary(BINARY_BUSYBOX);
    }

    /**
     * @param filename - check for this existence of this file
     * @return true if found
     */
    public boolean checkForBinary(String filename) {

        String[] pathsArray = Const.getPaths();

        boolean result = false;

        for (String path : pathsArray) {
            String completePath = path + filename;
            File f = new File(path, filename);
            boolean fileExists = f.exists();
            if (fileExists) {
                Logger.v(completePath + " binary detected!");
                result = true;
            }
        }

        return result;
    }


    private String[] propsReader() {
        try {
            InputStream inputstream = Runtime.getRuntime().exec("getprop").getInputStream();
            if (inputstream == null) return null;
            String propVal = new Scanner(inputstream).useDelimiter("\\A").next();
            return propVal.split("\n");
        } catch (IOException | NoSuchElementException e) {
            Logger.e(e,"","");
            return null;
        }
    }

    private String[] mountReader() {
        try {
            InputStream inputstream = Runtime.getRuntime().exec("mount").getInputStream();
            if (inputstream == null) return null;
            String propVal = new Scanner(inputstream).useDelimiter("\\A").next();
            return propVal.split("\n");
        } catch (IOException | NoSuchElementException e) {
            Logger.e(e,"","");
            return null;
        }
    }

    /**
     * Check if any package in the list is installed
     *
     * @param packages - list of packages to search for
     * @return true if any of the packages are installed
     */
    private boolean isAnyPackageFromListInstalled(List<String> packages) {
        boolean result = false;

        PackageManager pm = mContext.getPackageManager();

        for (String packageName : packages) {
            try {
                // Root app detected
                pm.getPackageInfo(packageName, 0);
                Logger.e(packageName + " ROOT management app detected!");
                result = true;
            } catch (PackageManager.NameNotFoundException e) {
                // Exception thrown, package is not installed into the system
            }
        }

        return result;
    }

    /**
     * Checks for several system properties for
     *
     * @return - true if dangerous props are found
     */
    public boolean checkForDangerousProps() {

        final Map<String, String> dangerousProps = new HashMap<>();
        dangerousProps.put("ro.debuggable", "1");
        dangerousProps.put("ro.secure", "0");

        boolean result = false;

        String[] lines = propsReader();

        if (lines == null) {
            // Could not read, assume false;
            return false;
        }

        for (String line : lines) {
            for (String key : dangerousProps.keySet()) {
                if (line.contains(key)) {
                    String badValue = dangerousProps.get(key);
                    badValue = "[" + badValue + "]";
                    if (line.contains(badValue)) {
                        Logger.v(key + " = " + badValue + " detected!");
                        result = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * When you're root you can change the permissions on common system directories, this method checks if any of these patha Const.pathsThatShouldNotBeWritable are writable.
     *
     * @return true if one of the dir is writable
     */
    public boolean checkForRWPaths() {

        boolean result = false;

        String[] lines = mountReader();

        if (lines == null) {
            // Could not read, assume false;
            return false;
        }

        for (String line : lines) {

            // Split lines into parts
            String[] args = line.split(" ");

            if (args.length < 4) {
                // If we don't have enough options per line, skip this and log an error
                Logger.e("Error formatting mount line: " + line);
                continue;
            }

            String mountPoint = args[1];
            String mountOptions = args[3];

            for (String pathToCheck : Const.pathsThatShouldNotBeWritable) {
                if (mountPoint.equalsIgnoreCase(pathToCheck)) {

                    // Split options out and compare against "rw" to avoid false positives
                    for (String option : mountOptions.split(",")) {

                        if (option.equalsIgnoreCase("rw")) {
                            Logger.v(pathToCheck + " path is mounted with rw permissions! " + line);
                            result = true;
                            break;
                        }
                    }
                }
            }
        }

        return result;
    }


    /**
     * A variation on the checking for SU, this attempts a 'which su'
     *
     * @return true if su found
     */
    public boolean checkSuExists() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"which", BINARY_SU});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return in.readLine() != null;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }


    /**
     * Checks if device has ReadAccess to the Native Library
     * Precondition: canLoadNativeLibrary() ran before this and returned true
     * <p>
     * Description: RootCloak automatically blocks read access to the Native Libraries, however
     * allows for them to be loaded into memory. This check is an indication that RootCloak is
     * installed onto the device.
     *
     * @return true if device has Read Access | false if UnsatisfiedLinkError Occurs
     */
    public boolean checkForNativeLibraryReadAccess() {
        RootNative rootNative = new RootNative();
        try {
            rootNative.setLogDebugMessages(loggingEnabled);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Checks if it is possible to load our native library
     *
     * @return true if we can | false if not
     */
    public boolean canLoadNativeLibrary() {
        return new RootNative().wasNativeLibraryLoaded();
    }

    /**
     * Native checks are often harder to cloak/trick so here we call through to our native root checker
     *
     * @return true if we found su | false if not, or the native library could not be loaded / accessed
     */
    public boolean checkForRootNative() {

        if (!canLoadNativeLibrary()) {
            Logger.e("We could not load the native library to test for root");
            return false;
        }

        String[] paths = Const.getPaths();

        String[] checkPaths = new String[paths.length];
        for (int i = 0; i < checkPaths.length; i++) {
            checkPaths[i] = paths[i] + BINARY_SU;
        }

        RootNative rootNative = new RootNative();
        try {
            rootNative.setLogDebugMessages(loggingEnabled);
            return rootNative.checkForRoot(checkPaths) > 0;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }


}
