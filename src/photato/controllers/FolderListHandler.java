package photato.controllers;

import photato.controllers.entities.FolderListResponse;
import photato.helpers.SerialisationGsonBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import photato.core.PhotatoFilesManager;
import photato.core.entities.PhotatoFolder;
import photato.core.entities.PhotatoPicture;
import java.util.Arrays;
import java.util.Map;

public class FolderListHandler extends PhotatoHandler {

    private final Path rootFolder;
    private final PhotatoFilesManager photatoFilesManager;

    public FolderListHandler(String prefix, Path rootFolder, PhotatoFilesManager photatoFilesManager) {
        super(prefix, new String[]{"GET"});
        this.rootFolder = rootFolder;
        this.photatoFilesManager = photatoFilesManager;
    }

    @Override
    protected Response getResponse(String path, Map<String, String> queryStringMap) throws Exception {
        if (queryStringMap.keySet().containsAll(Arrays.asList(new String[]{"folder", "beginIndex", "endIndex"}))) {
            String folderTmp = queryStringMap.get("folder");
            while (!folderTmp.isEmpty() && folderTmp.startsWith("/")) { // Remove the leading slashes if needed
                folderTmp = folderTmp.substring(1);
            }

            String folder = this.rootFolder.resolve(folderTmp).toString();

            String query = queryStringMap.get("query"); // Can be null

            int beginIndex = Integer.parseInt(queryStringMap.get("beginIndex"));
            int endIndex = Integer.parseInt(queryStringMap.get("endIndex"));

            List<PhotatoFolder> folders = beginIndex == 0 ? (query == null ? this.photatoFilesManager.getFoldersInFolder(folder) : this.photatoFilesManager.searchFoldersInFolder(folder, query)) : new ArrayList<>();
            List<PhotatoPicture> pictures = query == null ? this.photatoFilesManager.getPicturesInFolder(folder) : this.photatoFilesManager.searchPicturesInFolder(folder, query);

            folders.sort((PhotatoFolder f1, PhotatoFolder f2) -> f1.filename.compareTo(f2.filename));
            pictures.sort((PhotatoPicture p1, PhotatoPicture p2) -> {
                int c = Long.compare(p1.pictureDate, p2.pictureDate);
                if (c == 0) {
                    return p1.filename.compareTo(p2.filename);
                } else {
                    return c;
                }
            }
            );

            boolean hasMore;

            if (pictures.size() > beginIndex) {
                hasMore = Math.min(endIndex, pictures.size()) == endIndex;
                pictures = pictures.subList(beginIndex, Math.min(endIndex, pictures.size()));
            } else {
                hasMore = false;
                pictures = new ArrayList<>();
            }
            FolderListResponse result = new FolderListResponse(folders, pictures, beginIndex, endIndex, hasMore);

            return new Response(HttpStatus.SC_OK, new StringEntity(SerialisationGsonBuilder.getGson().toJson(result), ContentType.create("application/json", "UTF-8")));
        } else {
            return PhotatoHandler.http404;
        }
    }
}