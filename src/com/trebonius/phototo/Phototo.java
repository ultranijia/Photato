package com.trebonius.phototo;

import java.nio.file.FileSystem;
import com.trebonius.phototo.core.PhototoFilesManager;
import com.trebonius.phototo.core.metadata.MetadataAggregator;
import com.trebonius.phototo.core.thumbnails.ThumbnailGenerator;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import com.trebonius.phototo.core.fullscreen.FullScreenImageEntityGetter;
import com.trebonius.phototo.core.metadata.gps.IGpsCoordinatesDescriptionGetter;
import com.trebonius.phototo.controllers.CssHandler;
import com.trebonius.phototo.controllers.DefaultHandler;
import com.trebonius.phototo.controllers.FolderListHandler;
import com.trebonius.phototo.controllers.ImageHandler;
import com.trebonius.phototo.controllers.JsHandler;
import com.trebonius.phototo.core.metadata.exif.ExifToolDownloader;
import com.trebonius.phototo.core.metadata.gps.GpsCoordinatesDescriptionCache;
import com.trebonius.phototo.core.metadata.gps.OSMGpsCoordinatesDescriptionGetter;
import java.nio.file.Path;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public class Phototo {

    public static final String[] supportedPictureExtensions = new String[]{"jpg", "jpeg", "png", "bmp"};
    private static final String serverName = "Phototo/1.0";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: <picturesRootFolder>");
            System.exit(-1);
        }

        boolean prefixModeOnly = true;
        boolean indexFolderName = false;
        boolean useParallelThumbnailGeneration = true;
        boolean forceExifToolsDownload = false;

        FileSystem fileSystem = FileSystems.getDefault();
        Path rootFolder = fileSystem.getPath(args[0]);

        System.out.println("Starting exploration of folder " + rootFolder + "...");
        if (!Files.exists(fileSystem.getPath("cache"))) {
            Files.createDirectory(fileSystem.getPath("cache"));
        }

        HttpClient httpClient = HttpClientBuilder.create().setUserAgent(serverName).build();

        ExifToolDownloader.run(httpClient, fileSystem, forceExifToolsDownload);

        ThumbnailGenerator thumbnailGenerator = new ThumbnailGenerator(fileSystem, rootFolder, "cache/thumbnails");
        IGpsCoordinatesDescriptionGetter gpsCoordinatesDescriptionGetter = new OSMGpsCoordinatesDescriptionGetter(new GpsCoordinatesDescriptionCache("cache/gps.cache"), httpClient);
        MetadataAggregator metadataGetter = new MetadataAggregator(fileSystem, "cache/metadata.cache", gpsCoordinatesDescriptionGetter);
        FullScreenImageEntityGetter fullScreenResizeGenerator = new FullScreenImageEntityGetter(fileSystem, rootFolder, "cache/fullsize");

        PhototoFilesManager phototoFilesManager = new PhototoFilesManager(rootFolder, fileSystem, metadataGetter, thumbnailGenerator, prefixModeOnly, indexFolderName, useParallelThumbnailGeneration);

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(60000)
                .setTcpNoDelay(true)
                .build();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(8186)
                .setServerInfo(serverName)
                .setSocketConfig(socketConfig)
                .setExceptionLogger(new StdErrorExceptionLogger())
                .registerHandler(Routes.fullSizePicturesRootUrl + "/*", new ImageHandler(rootFolder, Routes.fullSizePicturesRootUrl, fullScreenResizeGenerator))
                .registerHandler(Routes.thumbnailRootUrl + "/*", new ImageHandler(fileSystem.getPath("cache/thumbnails"), Routes.thumbnailRootUrl, null))
                .registerHandler(Routes.listItemsApiUrl, new FolderListHandler(Routes.listItemsApiUrl, rootFolder, phototoFilesManager))
                .registerHandler("/img/*", new ImageHandler(fileSystem.getPath("www/img"), "/img", null))
                .registerHandler("/js/*", new JsHandler(fileSystem.getPath("www/js"), "/js"))
                .registerHandler("/css/*", new CssHandler(fileSystem.getPath("www/css"), "/css"))
                .registerHandler("*", new DefaultHandler(fileSystem.getPath("www")))
                .create();
        server.start();
        System.out.println("Server started on port " + server.getLocalPort());
        server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown(5, TimeUnit.SECONDS);
            }
        });
    }

}
