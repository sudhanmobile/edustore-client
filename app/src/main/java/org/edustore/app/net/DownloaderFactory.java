package org.edustore.app.net;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.fdroid.IndexFile;
import org.fdroid.database.Repository;
import org.fdroid.download.DownloadRequest;
import org.fdroid.download.Downloader;
import org.fdroid.download.HttpDownloader;
import org.fdroid.download.HttpDownloaderV2;
import org.fdroid.download.HttpManager;
import org.fdroid.download.Mirror;
import org.edustore.app.FDroidApp;
import org.edustore.app.Preferences;
import org.edustore.app.Utils;
import org.fdroid.index.IndexFormatVersion;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import info.guardianproject.netcipher.NetCipher;

public class DownloaderFactory extends org.fdroid.download.DownloaderFactory {

    private static final String TAG = "DownloaderFactory";
    // TODO move to application object or inject where needed
    public static final DownloaderFactory INSTANCE = new DownloaderFactory();
    public static final HttpManager HTTP_MANAGER = new HttpManager(
            Utils.getUserAgent(),
            FDroidApp.queryString,
            NetCipher.getProxy(),
            new DnsWithCache(),
            new FDroidMirrorParameterManager());

    @NonNull
    @Override
    public Downloader create(Repository repo, @NonNull Uri uri, @NonNull IndexFile indexFile,
                             @NonNull File destFile) throws IOException {
        List<Mirror> mirrors = repo.getMirrors();
        return create(repo, mirrors, uri, indexFile, destFile, null);
    }

    @NonNull
    @Override
    @SuppressLint("MissingPermission") // we'd need to ask for Bluetooth permission, but code unmaintained
    protected Downloader create(@NonNull Repository repo, @NonNull List<Mirror> mirrors, @NonNull Uri uri,
                                @NonNull IndexFile indexFile, @NonNull File destFile,
                                @Nullable Mirror tryFirst) throws IOException {
        Downloader downloader;

        String scheme = uri.getScheme();
        if (BluetoothDownloader.SCHEME.equals(scheme)) {
            downloader = new BluetoothDownloader(uri, indexFile, destFile);
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            downloader = new TreeUriDownloader(uri, indexFile, destFile);
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            downloader = new LocalFileDownloader(uri, indexFile, destFile);
        } else {
            String repoAddress = Utils.getRepoAddress(repo);
            String path = uri.toString().replace(repoAddress, "");
            Utils.debugLog(TAG, "Using suffix " + path + " with mirrors " + mirrors);
            Proxy proxy = NetCipher.getProxy();
            DownloadRequest request = new DownloadRequest(indexFile, mirrors, proxy,
                    repo.getUsername(), repo.getPassword(), tryFirst);
            Preferences prefs = Preferences.get();
            boolean oldIndex = prefs.isForceOldIndexEnabled();
            boolean v1OrUnknown = repo.getFormatVersion() == null ||
                    repo.getFormatVersion() == IndexFormatVersion.ONE;
            if (oldIndex || v1OrUnknown) {
                //noinspection deprecation
                downloader = new HttpDownloader(HTTP_MANAGER, request, destFile);
            } else {
                DownloadRequest r;
                if (request.getIndexFile().getIpfsCidV1() == null || !prefs.isIpfsEnabled()) {
                    r = request;
                } else {
                    // add IPFS gateways to mirrors, because have have a CIDv1 and IPFS is enabled in preferences
                    List<Mirror> m = new ArrayList<>(mirrors);
                    m.addAll(loadIpfsGateways(prefs));
                    r = new DownloadRequest(request.getIndexFile(), m, proxy, repo.getUsername(),
                            repo.getPassword(), tryFirst);
                }
                downloader = new HttpDownloaderV2(HTTP_MANAGER, r, destFile);
            }
        }
        return downloader;
    }

    private static List<Mirror> loadIpfsGateways(Preferences prefs) {
        List<Mirror> mirrorList = new ArrayList<>();
        for (String gatewayUrl : prefs.getActiveIpfsGateways()) {
            mirrorList.add(new Mirror(gatewayUrl, null, true));
        }
        return mirrorList;
    }

}
