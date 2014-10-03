package org.durka.hallmonitor_framework_test;

import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.preference.Preference;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

/**
 * Created by habeIchVergessen on 02.10.2014.
 */
public class Logcat {

    public static File writeOutput(String packageName) {
        return writeOutput(packageName, null);
    }

    public static File writeOutput(String packageName, SharedPreferences prefs) {
        String[] mPackageNames = packageName.split("(\\.|_)");

        String mOutputName = "Logcat_";
        for (int i = 0; i < mPackageNames.length; i++)
            mOutputName += mPackageNames[i].substring(0, 1).toUpperCase() + mPackageNames[i].substring(1);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        mOutputName += "_" + sdf.format(new Date()) + ".log";

        ArrayList<String> log = getOutput();

        String outDirName = "/storage/sdcard0/Download";
        File outFile = null;
        try {
            File outDir = new File(outDirName);

            if (!outDir.getParentFile().isDirectory()) {
                Log.e("Logcat", "writeOutput: directory doesn't exists! '" + outDir.getAbsolutePath() + "'");
                return null;
            }

            if (!outDir.isDirectory() && !outDir.mkdirs()) {
                Log.e("Logcat", "writeOutput: can't create directory '" + outDir.getAbsolutePath() + "'");
                return null;
            }

            outFile = new File(outDir, mOutputName);
            outFile.createNewFile();

            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outFile));

            // build info's
            out.write("hardware: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n");
            out.write("build:    " + getBuildInfo() + "\n");
            out.write("os:       " + Build.DISPLAY + " (" + System.getProperty("java.vm.name") + ")\n");
            out.write("kernel:   " + System.getProperty("os.version") + "\n");
            out.write("\n");

            // prefs
            if (prefs != null) {
                Map<String,?> keys = prefs.getAll();

                out.write("preferences:\n");
                for(Map.Entry<String,?> entry : keys.entrySet()) {
                    out.write("   " + entry.getKey() + " = '" + entry.getValue().toString() + "'\n");
                }
                out.write("\n");
            }

            for (int i=0; i < log.size(); i++)
                if (!log.get(i).isEmpty())
                    out.write(log.get(i));
            out.flush();
            out.close();

            mOutputName = outFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("Logcat", "writeOutput: exception occurred: '" + outDirName + File.pathSeparator + mOutputName + "', " + e.getMessage());
            return null;
        }

        return outFile;
    }

    public static ArrayList<String> getOutput() {
        ArrayList<String> log = new ArrayList<String>();

        try {
            ArrayList<String> commandLine = new ArrayList<String>();
            commandLine.add( "logcat");
            commandLine.add( "-d");
            commandLine.add( "-v");
            commandLine.add( "time");
            Process process = Runtime.getRuntime().exec( commandLine.toArray( new String[commandLine.size()]));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);

            String line;
            String matchAnyPid = ".*\\(\\ {0,9}\\d{1,}\\):.*";
            //String matchPid = ".*\\(\\ {0,9}" + android.os.Process.myPid() + "\\):.*";
            String matchPid = ".*\\(" + String.format("%5s", android.os.Process.myPid()) + "\\):.*";
            boolean pidMatched = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (!pidMatched && line.matches(matchPid))
                    pidMatched = true;
                else if (pidMatched && line.matches(matchAnyPid) && !line.matches(matchPid))
                    pidMatched = false;

                if (pidMatched)
                    log.add(line + "\n");
            }
        } catch (IOException e) {
            Log.e("Logcat", "getOutput: execption occurred: " + e.getMessage());
        }

        return log;
    }

    private static String getBuildInfo() {
        String buildInfo = null;

        try {
            ArrayList<String> commandLine = new ArrayList<String>();
            commandLine.add("getprop");
            commandLine.add("ro.cm.display.version");
            Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[commandLine.size()]));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);

            buildInfo = bufferedReader.readLine();
        } catch (IOException e) {
        } finally {
            if (buildInfo == null)
                buildInfo = Build.DEVICE + " " + new Date(Build.TIME);
        }

        return buildInfo;
    }
}
