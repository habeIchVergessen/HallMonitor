package org.durka.hallmonitor_framework_test;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.preference.Preference;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.DigestInputStream;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Created by habeIchVergessen on 02.10.2014.
 */
public class Logcat {

    public static File writeOutput(Context context) {
        return writeOutput(context, null);
    }

    public static File writeOutput(Context context, SharedPreferences prefs) {
        String[] mPackageNames = context.getPackageName().split("(\\.|_)");

        String mOutputName = "Logcat_";
        for (int i = 0; i < mPackageNames.length; i++)
            mOutputName += mPackageNames[i].substring(0, 1).toUpperCase() + mPackageNames[i].substring(1);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        mOutputName += "_" + sdf.format(new Date()) + ".log";

        ArrayList<String> log = getOutput();

        // TODO: get path from env (not working yet for cyanogenmod)
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

            // apk
            String apkInfo = getApkInfo(context);
            if ((apkInfo != null))
                out.write("apk:\n" + apkInfo + "\n");

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

    private static String getApkInfo(Context context) {
        String apkInfo = null, md5Hash, certInfo;

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        File apkFile = new File(applicationInfo.sourceDir);

        String prefix = "   ";
        apkInfo  = prefix + "file:    " + apkFile.getName() + " (" + apkFile.getParentFile().getPath() + ")\n";
        apkInfo += prefix + "package: " + applicationInfo.packageName + "\n";

        // md5
        if ((md5Hash = getMd5(apkFile)) != null)
            apkInfo += prefix + "md5:     " + md5Hash + "\n";

        try {
            ZipFile zf = new ZipFile(applicationInfo.sourceDir);
            ZipEntry ze = zf.getEntry("AndroidManifest.xml");

            // build date
            if (ze != null)
                apkInfo += prefix + "build:   " + new Date(ze.getTime()) + "\n";

            // signer
            ze = zf.getEntry("META-INF/CERT.RSA");
            if (ze != null && (certInfo = getCertificateInfo(zf.getInputStream(ze))) != null)
                apkInfo += prefix + "cert:    " + certInfo + "\n";

            // version
            try {
                PackageInfo packageInfo = context.getApplicationContext().getPackageManager().getPackageInfo(applicationInfo.packageName, 0);
                apkInfo += prefix + "version: " + packageInfo.versionName + " (" + packageInfo.versionCode + ")\n";
                apkInfo += prefix + "install: " + new Date(packageInfo.firstInstallTime) + " (" + new Date(packageInfo.lastUpdateTime) + ")\n";
            } catch (PackageManager.NameNotFoundException e) {
            }
        } catch (IOException e) {
        }

        return apkInfo;
    }

    private static String getMd5(File inputFile) {
        String returnVal = null;

        try
        {
            InputStream   input   = new FileInputStream(inputFile);
            byte[]        buffer  = new byte[1024];
            MessageDigest md5Hash = MessageDigest.getInstance("MD5");
            int           numRead = 0;
            while (numRead != -1)
            {
                numRead = input.read(buffer);
                if (numRead > 0)
                {
                    md5Hash.update(buffer, 0, numRead);
                }
            }
            input.close();

            byte [] md5Bytes = md5Hash.digest();
            for (int i=0; i < md5Bytes.length; i++) {
                if (i == 0)
                    returnVal = "";
                returnVal += Integer.toString( ( md5Bytes[i] & 0xff ) + 0x100, 16).substring( 1 );
            }
        } catch(Throwable t) {
        }

        return returnVal;
    }

    private static String getCertificateInfo(InputStream inputStream) {
        String certificateInfo = null;

        try {
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
                X509Certificate x509Certificate = (X509Certificate)certificateFactory.generateCertificate(inputStream);

                certificateInfo  = x509Certificate.getSubjectDN().getName();
                certificateInfo += " (serial: " + x509Certificate.getSerialNumber() + ")";
            } catch (CertificateException ce) {
                certificateInfo = "CertificateException: " + ce.getMessage();
            }

            inputStream.close();
        } catch (IOException e) {
            certificateInfo = "IOException: " + e.getMessage();
        }

        return certificateInfo;
    }
}
