package de.thecode.android.tazreader.job;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.squareup.picasso.Picasso;

import de.thecode.android.tazreader.BuildConfig;
import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.PaperRepository;
import de.thecode.android.tazreader.data.Publication;
import de.thecode.android.tazreader.data.PublicationRepository;
import de.thecode.android.tazreader.data.Resource;
import de.thecode.android.tazreader.data.ResourceRepository;
import de.thecode.android.tazreader.data.StoreRepository;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.download.DownloadManager;
import de.thecode.android.tazreader.okhttp3.OkHttp3Helper;
import de.thecode.android.tazreader.okhttp3.RequestHelper;
import de.thecode.android.tazreader.sync.SyncErrorEvent;
import de.thecode.android.tazreader.sync.SyncStateChangedEvent;
import de.thecode.android.tazreader.update.UpdateHelper;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import timber.log.Timber;

/**
 * Created by mate on 10.10.2017.
 */

public class SyncJob extends Job {

    public static final String TAG = BuildConfig.FLAVOR + "_sync_job";

    private static final String PLIST_KEY_ISSUES = "issues";

    public static final String ARG_START_DATE        = "startDate";
    public static final String ARG_END_DATE          = "endDate";
    public static final String ARG_INITIATED_BY_USER = "initiatedByUser";


    private PaperRepository       paperRepository;
    private ResourceRepository    resourceRepository;
    private PublicationRepository publicationRepository;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {

        EventBus.getDefault()
                .postSticky(new SyncStateChangedEvent(true));

        paperRepository = PaperRepository.getInstance(getContext());
        resourceRepository = ResourceRepository.getInstance(getContext());
        publicationRepository = PublicationRepository.getInstance(getContext());

        PersistableBundleCompat extras = params.getExtras();

        String startDate = extras.getString(ARG_START_DATE, null);
        String endDate = extras.getString(ARG_END_DATE, null);
        boolean initByUser = extras.getBoolean(ARG_INITIATED_BY_USER, false);


        NSDictionary plist = callPlist(startDate, endDate);

        if (plist == null) {
            if (initByUser) {
                EventBus.getDefault()
                        .post(new SyncErrorEvent(getContext().getString(R.string.sync_job_plist_empty)));
                return endJob(Result.SUCCESS);
            } else {
                return endJob(Result.RESCHEDULE);
            }
        } else {
            if (!initByUser) autoDeleteTask();
            handlePlist(plist);
        }

        if (startDate == null && endDate == null) {
            downloadLatestResource();
        }

        cleanUpResources();

        Paper latestPaper = paperRepository.getLatestPaper();
        if (latestPaper != null) AutoDownloadJob.scheduleJob(latestPaper);

        return endJob(Result.SUCCESS);
    }

    private Result endJob(Result result) {
        EventBus.getDefault()
                .postSticky(new SyncStateChangedEvent(false));
        return result;
    }


    private void cleanUpResources() {

        List<Paper> allPapers = paperRepository.getAllPapers();
        List<Resource> keepResources = new ArrayList<>();
        if (allPapers != null) {
            for (Paper paper : allPapers) {
                if (paper.isDownloaded() || paper.isDownloading()) {
                    Resource resource = resourceRepository.getResourceForPaper(paper);
                    if (resource != null && !keepResources.contains(resource)) keepResources.add(resource);
                }
            }
        }
        List<Resource> deleteResources = resourceRepository.getAllResources();
        Paper latestPaper = paperRepository.getLatestPaper();
        if (latestPaper != null) deleteResources.remove(resourceRepository.getWithKey(latestPaper.getResource()));
        for (Resource keepResource : keepResources) {
            if (deleteResources.contains(keepResource)) {
                deleteResources.remove(keepResource);
            }
        }
        for (Resource deleteResource : deleteResources) {
            resourceRepository.deleteResource(deleteResource);
        }
    }


    public void handlePlist(NSDictionary root) {
        Publication publication = new Publication(root);

//        Cursor pubCursor = getContext().getContentResolver()
//                                       .query(Publication.CONTENT_URI,
//                                              null,
//                                              Publication.Columns.ISSUENAME + " LIKE '" + publication.getIssueName() + "'",
//                                              null,
//                                              null);

        String publicationTitle = publication.getName();
        long validUntil = publication.getValidUntil();
        SyncJob.scheduleJobIn(TimeUnit.SECONDS.toMillis(validUntil) - System.currentTimeMillis());
        //minDataValidUntil = Math.min(minDataValidUntil, validUntil * 1000);

        publicationRepository.savePublication(publication);
        UpdateHelper.getInstance(getContext()).setLatestVersion(publication.getAppAndroidVersion());


//        getContext().getContentResolver().insert(Publication.CONTENT_URI,publication.getContentValues());

//        try {
//            if (pubCursor.moveToNext()) {
//                Publication oldPupdata = new Publication(pubCursor);
//                publicationId = oldPupdata.getId();
//
//                oldPupdata.setCreated(publication.getCreated());
//                oldPupdata.setImage(publication.getImage());
//                oldPupdata.setIssueName(publication.getIssueName());
//                oldPupdata.setName(publication.getName());
//                oldPupdata.setTypeName(publication.getTypeName());
//                oldPupdata.setUrl(publication.getUrl());
//                oldPupdata.setValidUntil(publication.getValidUntil());
//
//                Uri updateUri = ContentUris.withAppendedId(Publication.CONTENT_URI, publicationId);
//                getContext().getContentResolver()
//                            .update(updateUri, oldPupdata.getContentValues(), null, null);
//            } else {
//                Uri newPublicationUri = getContext().getContentResolver()
//                                                    .insert(Publication.CONTENT_URI, publication.getContentValues());
//                publicationId = ContentUris.parseId(newPublicationUri);
//            }
//        } finally {
//            pubCursor.close();
//        }

        NSObject[] issues = ((NSArray) root.objectForKey(PLIST_KEY_ISSUES)).getArray();
        List<Paper> newPapers = new ArrayList<>();
        for (NSObject issue : issues) {
            Paper newPaper = new Paper((NSDictionary) issue);
            newPaper.setPublication(publication.getIssueName());
            newPaper.setTitle(publicationTitle);
            newPaper.setValidUntil(validUntil);

//            Uri bookIdUri = Paper.CONTENT_URI.buildUpon()
//                                             .appendPath(newPaper.getBookId())
//                                             .build();
//            Cursor cursor = getContext().getContentResolver()
//                                        .query(bookIdUri,
//                                               null, /*Paper.Columns.IMPORTED + "=0 AND " + Paper.Columns.KIOSK + "=0"*/
//                                               null,
//                                               null,
//                                               null);

            boolean loadImage = true;


            Paper oldPaper = paperRepository.getPaperWithBookId(newPaper.getBookId());
            if (oldPaper != null) {
                newPaper.setDownloaded(oldPaper.isDownloaded());
                newPaper.setDownloadId(oldPaper.getDownloadId());
                newPaper.setImported(oldPaper.isImported());
                newPaper.setKiosk(oldPaper.isKiosk());
                loadImage = !new EqualsBuilder().append(oldPaper.getImageHash(), newPaper.getImageHash())
                                                .isEquals();
                if (!oldPaper.isImported() && !oldPaper.isKiosk()) {
                    if (!new EqualsBuilder().append(oldPaper.getFileHash(), newPaper.getFileHash())
                                            .isEquals() && (oldPaper.isDownloaded() || oldPaper.isDownloading()))
                        newPaper.setHasUpdate(true);
                }
            }
            if (loadImage) preLoadImage(newPaper);
            newPapers.add(newPaper);
//            getContext().getContentResolver()
//                        .insert(Paper.CONTENT_URI, newPaper.getContentValues());
            Resource resource = resourceRepository.getWithKey(newPaper.getResource());
            if (resource == null) {
                resourceRepository.saveResource(new Resource((NSDictionary) issue));
            }

//            Resource resource = ResourceRepository.getInstance(getContext())
//                                                  .getWithKey(newPaper.getResource());
//            if (resource == null) {
//                resource = new Resource((NSDictionary) issue);
//                getContext().getContentResolver()
//                            .insert(Resource.CONTENT_URI, resource.getContentValues());
//            }


//                    if (!newPaper.equals(oldPaper)) {
//                        Timber.d("found difference in paper");
//                        oldPaper.setImage(newPaper.getImage());
//                        boolean reloadImage = !new EqualsBuilder().append(oldPaper.getImageHash(), newPaper.getImageHash())
//                                                                  .isEquals();
//                        oldPaper.setImageHash(newPaper.getImageHash());
//                        if (!oldPaper.isImported() && !oldPaper.isKiosk()) {
//
//                            if (!new EqualsBuilder().append(oldPaper.getLastModified(), newPaper.getLastModified())
//                                                    .isEquals()) {
//                                oldPaper.setLastModified(newPaper.getLastModified());
//                                if (!new EqualsBuilder().append(oldPaper.getFileHash(), newPaper.getFileHash())
//                                                        .isEquals() && (oldPaper.isDownloaded() || oldPaper.isDownloading()))
//                                    oldPaper.setHasUpdate(true);
//                            }
//                            oldPaper.setLink(newPaper.getLink());
//                            oldPaper.setLen(newPaper.getLen());
//                            oldPaper.setFileHash(newPaper.getFileHash());
//                            oldPaper.setResource(newPaper.getResource());
////                            oldPaper.setResourceFileHash(newPaper.getResourceFileHash());
////                            oldPaper.setResourceUrl(newPaper.getResourceUrl());
////                            oldPaper.setResourceLen(newPaper.getResourceLen());
//                            oldPaper.setDemo(newPaper.isDemo());
//                            oldPaper.setValidUntil(newPaper.getValidUntil());
//                        }
//                        if (TextUtils.isEmpty(oldPaper.getPublication())) {
//                            oldPaper.setPublication(publication.getIssueName());
//                        }
//                        getContext().getContentResolver()
//                                    .update(oldPaper.getContentUri(), oldPaper.getContentValues(), null, null);
//                        if (reloadImage) preLoadImage(oldPaper);
//                        //setMoveToPaperAtEnd(oldPaper);
//                    }
//                    newPaper = oldPaper;
//                } else {
//                    Timber.d("notfound");
//
//                    long newPaperId = ContentUris.parseId(getContext().getContentResolver()
//                                                                      .insert(Paper.CONTENT_URI, newPaper.getContentValues()));
//                    newPaper.setId(newPaperId);
//                    setMoveToPaperAtEnd(newPaper);
//                    preLoadImage(newPaper);
//                }
//                Resource resource = ResourceRepository.getInstance(getContext())
//                                                      .getWithKey(newPaper.getResource());
//                if (resource == null) {
//                    resource = new Resource((NSDictionary) issue);
//                    getContext().getContentResolver()
//                                .insert(Resource.CONTENT_URI, resource.getContentValues());
//                }
//            } finally {
//                cursor.close();
//            }


        }
        paperRepository.savePapers(newPapers);

    }

    private NSDictionary callPlist(String startDate, String endDate) {
        HttpUrl url;
        if (!TextUtils.isEmpty(startDate) && !TextUtils.isEmpty(endDate)) {
            url = HttpUrl.parse(String.format(BuildConfig.PLISTARCHIVURL, startDate, endDate));
        } else {
            url = HttpUrl.parse(BuildConfig.PLISTURL);
        }
        okhttp3.Call call = OkHttp3Helper.getInstance(getContext())
                                         .getCall(url,
                                                  RequestHelper.getInstance(getContext())
                                                               .getOkhttp3RequestBody());
        try {
            okhttp3.Response response = call.execute();
            if (response.isSuccessful()) {
                return (NSDictionary) PropertyListParser.parse(response.body()
                                                                       .bytes());
            }
            throw new IOException(response.body()
                                          .string());
        } catch (Exception e) {
            Timber.e(e);
        }
        return null;
    }


    private void preLoadImage(Paper paper) {
//        //Timber.v("preloading image %s", url);
//        SyncService.PreloadImageCallback callback = new SyncService.PreloadImageCallback(paper) {
//            @Override
//            public void onSuccess(Paper paper) {
//                EventBus.getDefault()
//                        .post(new CoverDownloadedEvent(paper.getId()));
//                imagePreloadingQueue.remove(this);
//            }
//
//            @Override
//            public void onError(Paper paper) {
//                imagePreloadingQueue.remove(this);
//            }
//        };
//        imagePreloadingQueue.add(callback);
//        Picasso.with(this)
//               .load(paper.getImage())
//               .fetch(callback);
        Picasso.with(getContext())
               .load(paper.getImage())
               .fetch();
    }

//    private void setMoveToPaperAtEnd(Paper paper) {
//        try {
//            if (moveToPaperAtEnd == null || paper.getDateInMillis() > moveToPaperAtEnd.getDateInMillis()) {
//                moveToPaperAtEnd = paper;
//            }
//        } catch (ParseException e) {
//            Timber.e(e);
//            moveToPaperAtEnd = paper;
//        }
//    }

    private void downloadLatestResource() {
        Paper latestPaper = paperRepository.getLatestPaper();
        if (latestPaper != null) {
            Resource latestResource = resourceRepository.getWithKey(latestPaper.getResource());
            if (latestResource != null && !latestResource.isDownloaded() && !latestResource.isDownloading()) {
                try {
                    DownloadManager.getInstance(getContext())
                                   .enqueResource(latestResource, false);
                } catch (DownloadManager.NotEnoughSpaceException e) {
                    Timber.e(e);
                }
            }
        }
    }


    private void autoDeleteTask() {
        if (TazSettings.getInstance(getContext())
                       .getPrefBoolean(TazSettings.PREFKEY.AUTODELETE, false)) {

//            long currentOpenPaperId = TazSettings.getInstance(getContext())
//                                                 .getPrefLong(TazSettings.PREFKEY.LASTOPENPAPER, -1L);
//            Timber.d("+++++++ TazSettings: Current Paper SyncAdapter View: %s", currentOpenPaperId);

            //TODO Get BookId from Setting, an set it in Reader
            String currentOpenPaperBookId = null;

            int papersToKeep = TazSettings.getInstance(getContext())
                                          .getPrefInt(TazSettings.PREFKEY.AUTODELETE_VALUE, 0);
            if (papersToKeep > 0) {
                List<Paper> allPapers = paperRepository.getAllPapers();
                int counter = 0;
                for (Paper paper : allPapers) {
                    if (paper.isDownloaded() && !paper.isImported() && !paper.isKiosk()) {
                        if (counter >= papersToKeep) {
                            Timber.d("PaperId: %s (currentOpen:%s)", paper.getBookId(), currentOpenPaperBookId);
                            if (!paper.getBookId()
                                      .equals(currentOpenPaperBookId)) {
                                boolean safeToDelete = true;
                                String bookmarksJsonString = StoreRepository.getInstance(getContext())
                                                                            .getStore(paper.getBookId(),
                                                                                      Paper.STORE_KEY_BOOKMARKS)
                                                                            .getValue();
                                if (!TextUtils.isEmpty(bookmarksJsonString)) {
                                    try {
                                        JSONArray bookmarks = new JSONArray(bookmarksJsonString);
                                        if (bookmarks.length() > 0) safeToDelete = false;
                                    } catch (JSONException e) {
                                        // JSON Error, better don't delete
                                        safeToDelete = false;
                                    }
                                }
                                if (safeToDelete) {
                                    PaperRepository.getInstance(getContext())
                                                   .deletePaper(paper);
                                }
                            }
                        }
                        counter++;
                    }
                }
            }
        }
    }


    public static void scheduleJobImmediately(boolean byUser) {
        scheduleJobImmediately(byUser, null, null);
    }

    public static void scheduleJobImmediately(boolean byUser, Calendar start, Calendar end) {

        PersistableBundleCompat extras = new PersistableBundleCompat();

        extras.putBoolean(ARG_INITIATED_BY_USER, byUser);

        if (start != null && end != null) {
            extras.putString(SyncJob.ARG_START_DATE, new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(start.getTime()));
            extras.putString(SyncJob.ARG_END_DATE, new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(end.getTime()));
        }

        new JobRequest.Builder(TAG).startNow()
                                   .setExtras(extras)
                                   .build()
                                   .schedule();
    }

    private static int scheduleJobIn(long latestMillis) {
        return new JobRequest.Builder(SyncJob.TAG).setExecutionWindow(Math.max(0, latestMillis - TimeUnit.MINUTES.toMillis(30)),
                                                                      Math.max(60000, latestMillis))
                                                  .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                                                  .setRequirementsEnforced(true)
                                                  .setUpdateCurrent(true)
                                                  .build()
                                                  .schedule();
    }
}
