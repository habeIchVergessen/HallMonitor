package org.durka.hallmonitor_framework_test;

import android.media.MediaScannerConnection;
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

/**
 * Created by habeIchVergessen on 02.10.2014.
 */
public class Logcat {

    public static File writeOutput(String packageName) {
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

            String line, match = ".*\\(\\ {0,9}" + android.os.Process.myPid() + "\\):.*";
            while ((line = bufferedReader.readLine()) != null) {
                if (line.matches(match))
                    log.add(line + "\n");
            }
        } catch ( IOException e) {
            Log.e("Logcat", "getOutput: execption occurred: " + e.getMessage());
        }

        return log;
    }
}
