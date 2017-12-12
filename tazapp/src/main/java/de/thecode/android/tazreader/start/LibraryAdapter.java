package de.thecode.android.tazreader.start;

import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.download.DownloadManager;
import de.thecode.android.tazreader.download.DownloadProgressEvent;
import de.thecode.android.tazreader.download.PaperDownloadFailedEvent;
import de.thecode.android.tazreader.download.UnzipProgressEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.List;

import timber.log.Timber;

/**
 * Created by mate on 10.02.2015.
 */
public class LibraryAdapter extends CursorRecyclerViewAdapter<LibraryAdapter.ViewHolder> {

    int                           mCoverImageHeight;
    int                           mCoverImageWidth;
    //Bitmap mPlaceHolderBitmap;
    Context                       mContext;
    WeakReference<IStartCallback> callback;
    OnItemClickListener           mClickListener;
    OnItemLongClickListener       mLongClickListener;

    DownloadManager downloadHelper;

    public LibraryAdapter(Context context, Cursor cursor, IStartCallback callback) {
        super(context, cursor);

        EventBus.getDefault()
                .register(this);

        mContext = context;
        this.callback = new WeakReference<IStartCallback>(callback);

        downloadHelper = DownloadManager.getInstance(context);

        mCoverImageHeight = context.getResources()
                                   .getDimensionPixelSize(R.dimen.cover_image_height);
        mCoverImageWidth = context.getResources()
                                  .getDimensionPixelSize(R.dimen.cover_image_width);
        // mPlaceHolderBitmap = bitmapFromResource(context, R.drawable.dummy, mCoverImageWidth, mCoverImageHeight);
    }

    private boolean hasCallback() {
        return callback.get() != null;
    }

    public IStartCallback getCallback() {
        return callback.get();
    }

    public void destroy() {
        EventBus.getDefault()
                .unregister(this);
    }

    public List<Long> getSelected() {
        if (hasCallback()) return getCallback().getRetainData()
                                               .getSelectedInLibrary();
        return null;
    }

    public void select(long paperId) {
        select(getItemPosition(paperId));
    }

    public void deselect(long paperId) {
        deselect(getItemPosition(paperId));
    }

    public void select(int position) {

        long paperId = getItemId(position);
        //Log.v(position, paperId);

        if (!isSelected(paperId)) getSelected().add(paperId);

        notifyItemChanged(position);
    }

    public void deselect(int position) {
        long paperId = getItemId(position);
        //Log.v(position, paperId);
        if (isSelected(paperId)) getSelected().remove(paperId);
        notifyItemChanged(position);
    }

    public boolean isSelected(long paperId) {
        return getSelected().contains(paperId);
    }

    public void selectAll() {
        for (int i = 0; i < getItemCount(); i++) {
            select(i);
        }
    }

    public void deselectAll() {
        for (int i = 0; i < getItemCount(); i++) {
            deselect(i);
        }
    }


    public void selectionInvert() {
        for (int i = 0; i < getItemCount(); i++) {
            long paperId = getItemId(i);
            if (isSelected(paperId)) deselect(i);
            else select(i);
        }
    }

    public void selectNotLoaded() {
        Cursor cursor = getCursor();

        if (cursor != null) {
            for (int i = 0; i < getItemCount(); i++) {
                if (cursor.moveToPosition(i)) {
                    Paper paper = new Paper(cursor);
                    if (!(paper.isDownloaded() || paper.isDownloading())) {
                        if (!isSelected(paper.getId())) select(paper.getId());
                    }
                }
            }
        }
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.mClickListener = onItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener onItemLongClickListener) {
        this.mLongClickListener = onItemLongClickListener;
    }

    public void removeClickLIstener() {
        this.mLongClickListener = null;
        this.mClickListener = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPaperDownloadFailed(PaperDownloadFailedEvent event) {
        try {
            notifyItemChanged(getItemPosition(event.getPaperId()));
        } catch (IllegalStateException e) {
            Timber.w(e);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Cursor cursor) {

        Paper paper = new Paper(cursor);
        //Log.d(paper.getBookId());

        //        inititializeProgress(paper);

        viewHolder.setPaper(paper);
        try {
            viewHolder.date.setText(paper.getDate(DateFormat.MEDIUM));
        } catch (ParseException e) {
            viewHolder.date.setText(e.getMessage());
        }

        //TODO insert Picasso here, Policy only memory and disk

        Picasso.with(viewHolder.image.getContext())
               .load(paper.getImage())
               .placeholder(R.drawable.dummy)
               .networkPolicy(NetworkPolicy.OFFLINE)
               .into(viewHolder.image, new MissingCoverCallback(viewHolder.image, paper) {
                   @Override
                   public void onError(ImageView imageView, Paper paper) {
                       Picasso.with(imageView.getContext())
                              .load(paper.getImage())
                              .placeholder(R.drawable.dummy)
                              .into(imageView);
                   }

                   @Override
                   public void onSuccess(Paper paper) {

                   }
               });

//        String hash = paper.getImageHash();
//        if (!Strings.isNullOrEmpty(hash)) {
//            if (!hash.equals(viewHolder.image.getTag())) {
//                loadBitmap(hash, viewHolder.image);
//
//                viewHolder.image.setTag(hash);
//            }
//        } else {
//            viewHolder.image.setImageResource(R.drawable.dummy);
//        }

        //        viewHolder.progress.setProgress(100 - progressMap.get(paper.getId()));

        if (paper.isDownloading()) {
            viewHolder.wait.setVisibility(View.VISIBLE);
        } else {
            viewHolder.wait.setVisibility(View.GONE);
        }

//        if (paper.hasUpdate() /*&& paper.isDownloaded()*/) {
//            viewHolder.badge.setText(R.string.string_badge_update);
//            viewHolder.badge.setVisibility(View.VISIBLE);
//        }

        //        else if (paper.isImported()) {
        //            viewHolder.badge.setText(R.string.string_badge_import);
        //            viewHolder.badge.setVisibility(View.VISIBLE);
        //        }
        if (paper.isKiosk()) {
            viewHolder.badge.setText(R.string.string_badge_kiosk);
            viewHolder.badge.setVisibility(View.VISIBLE);
        } else viewHolder.badge.setVisibility(View.GONE);

        if (getSelected().contains(paper.getId())) viewHolder.selected.setVisibility(View.VISIBLE);
        else viewHolder.selected.setVisibility(View.INVISIBLE);

        try {
            viewHolder.card.setContentDescription(paper.getDate(DateFormat.LONG));
        } catch (ParseException e) {
            Timber.e(e);
        }

    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                                            .inflate(R.layout.start_library_item, parent, false));
    }

    @Override
    public void onViewAttachedToWindow(ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        EventBus.getDefault()
                .register(holder);
    }

    @Override
    public void onViewDetachedFromWindow(ViewHolder holder) {
        EventBus.getDefault()
                .unregister(holder);
        super.onViewDetachedFromWindow(holder);
    }


//    private void loadBitmap(String hash, ImageView imageView) {
//        if (cancelPotentialWork(hash, imageView)) {
//
//            final Bitmap bitmap = getBitmapFromMemCache(hash);
//
//            if (bitmap != null) {
//                imageView.setImageBitmap(bitmap);
//            } else {
//                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
//                final AsyncDrawable asyncDrawable = new AsyncDrawable(mContext.getResources(), mPlaceHolderBitmap, task);
//                imageView.setImageDrawable(asyncDrawable);
//                task.execute(hash);
//            }
//        }
//    }

//    public boolean cancelPotentialWork(String hash, ImageView imageView) {
//        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
//
//        if (bitmapWorkerTask != null) {
//            final String bitmapData = bitmapWorkerTask.hash;
//            // If bitmapData is not yet set or it differs from the new data
//            if (!hash.equals(bitmapData)) {
//                // Cancel previous task
//                bitmapWorkerTask.cancel(true);
//            } else {
//                // The same work is already in progress
//                return false;
//            }
//        }
//        // No task associated with the ImageView, or an existing task was cancelled
//        return true;
//    }

//    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
//        if (imageView != null) {
//            final Drawable drawable = imageView.getDrawable();
//            if (drawable instanceof AsyncDrawable) {
//                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
//                return asyncDrawable.getBitmapWorkerTask();
//            }
//        }
//        return null;
//    }


//    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
//        // Raw height and width of image
//        final int height = options.outHeight;
//        final int width = options.outWidth;
//        int inSampleSize = 1;
//
//        if (height > reqHeight || width > reqWidth) {
//
//            final int halfHeight = height / 2;
//            final int halfWidth = width / 2;
//
//            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
//            // height and width larger than the requested height and width.
//            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
//                inSampleSize *= 2;
//            }
//        }
//        //Log.d(reqWidth, width, reqHeight, height, inSampleSize);
//        return inSampleSize;
//    }

//    private Bitmap bitmapFromResource(Context context, int resId, int width, int height) {
//        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inJustDecodeBounds = true;
//        BitmapFactory.decodeResource(context.getResources(), resId, options);
//
//        // Calculate inSampleSize
//        options.inSampleSize = calculateInSampleSize(options, width, height);
//
//        // Decode bitmap with inSampleSize set
//        options.inJustDecodeBounds = false;
//        return BitmapFactory.decodeResource(context.getResources(), resId, options);
//    }

//    public Bitmap getBitmap(String hash, int width, int height) {
//        File imageFile = mCoverHelper.getFile(hash);
//        //Log.d(imageFile.getName());
//        if (imageFile.exists()) {
//            final BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inJustDecodeBounds = true;
//            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
//
//            // Calculate inSampleSize
//            options.inSampleSize = calculateInSampleSize(options, width, height);
//
//            // Decode bitmap with inSampleSize set
//            options.inJustDecodeBounds = false;
//            return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
//        }
//        return null;
//    }

//    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
//        if (getBitmapFromMemCache(key) == null) {
//            if (hasCallback()) getCallback().getRetainData()
//                                            .getCache()
//                                            .put(key, bitmap);
//        }
//    }

//    public Bitmap getBitmapFromMemCache(String key) {
//        if (hasCallback()) return getCallback().getRetainData()
//                                               .getCache()
//                                               .get(key);
//        return null;
//    }


    //    public void removeAllProgress() {
    //        progressMap.clear();
    //    }

//    class AsyncDrawable extends BitmapDrawable {
//
//        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;
//
//        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
//            super(res, bitmap);
//            bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
//        }
//
//        public BitmapWorkerTask getBitmapWorkerTask() {
//            return bitmapWorkerTaskReference.get();
//        }
//    }

//    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
//
//        private final WeakReference<ImageView> imageViewReference;
//        private String hash;
//
//        public BitmapWorkerTask(ImageView imageView) {
//            // Use a WeakReference to ensure the ImageView can be garbage collected
//            imageViewReference = new WeakReference<>(imageView);
//        }
//
//        // Decode image in background.
//        @Override
//        protected Bitmap doInBackground(String... params) {
//            hash = params[0];
//            return getBitmap(hash, mCoverImageWidth, mCoverImageHeight);
//        }
//
//        @Override
//        protected void onPostExecute(Bitmap bitmap) {
//            if (isCancelled()) {
//                bitmap = null;
//            }
//
//            if (bitmap != null) {
//                final ImageView imageView = imageViewReference.get();
//                final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
//                if (this == bitmapWorkerTask && imageView != null) {
//                    imageView.setImageBitmap(bitmap);
//                    addBitmapToMemoryCache(hash, bitmap);
//                }
//            }
//        }
//    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        CardView    card;
        TextView    date;
        TextView    badge;
        ImageView   image;
        ProgressBar wait;
        ProgressBar progress;
        FrameLayout selected;
        private Paper _paper;
        DownloadManager.DownloadProgressThread downloadProgressThread;

        public ViewHolder(View itemView) {
            super(itemView);
            card = (CardView) itemView.findViewById(R.id.lib_item_card);
            date = (TextView) itemView.findViewById(R.id.lib_item_date);
            badge = (TextView) itemView.findViewById(R.id.lib_item_badge);
            image = (ImageView) itemView.findViewById(R.id.lib_item_facsimile);
            wait = (ProgressBar) itemView.findViewById(R.id.lib_item_wait);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

                Drawable wrapDrawable = DrawableCompat.wrap(wait.getIndeterminateDrawable());
                DrawableCompat.setTint(wrapDrawable, ContextCompat.getColor(itemView.getContext(), R.color.library_item_text));
                wait.setIndeterminateDrawable(DrawableCompat.unwrap(wrapDrawable));
            } else {
                wait.getIndeterminateDrawable().setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.library_item_text), PorterDuff.Mode.SRC_IN);
            }
            progress = (ProgressBar) itemView.findViewById(R.id.lib_item_progress);
            selected = (FrameLayout) itemView.findViewById(R.id.lib_item_selected_overlay);
            card.setOnLongClickListener(this);
            card.setOnClickListener(this);
            ViewCompat.setImportantForAccessibility(badge, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ViewCompat.setImportantForAccessibility(date, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);


            //            if (Build.VERSION.SDK_INT >= 11) {
            //                int[] attrs = new int[] { android.R.attr.selectableItemBackground /* index 0 */};
            //                TypedArray ta = itemView.getContext().obtainStyledAttributes(attrs);
            //                Drawable drawableFromTheme = ta.getDrawable(0 /* index */);
            //                ta.recycle();
            //                card.setForeground(drawableFromTheme);
            //            }

        }


        @Override
        public void onClick(View v) {
            //Log.v();
            if (mClickListener != null) mClickListener.onItemClick(v, getPosition(), _paper);
        }

        @Override
        public boolean onLongClick(View v) {
            //Log.v();
            if (mLongClickListener != null) return mLongClickListener.onItemLongClick(v, getPosition(), _paper);
            return false;
        }

        public void setPaper(Paper paper) {
            if (paper != _paper) {
                if (downloadProgressThread != null) downloadProgressThread.interrupt();
                this._paper = paper;
                if (paper.isDownloaded()) setProgress(100);
                else {
                    if (paper.isDownloading()) {
                        DownloadManager.DownloadState downloadState = downloadHelper.getDownloadState(paper.getDownloadId());
                        setDownloadProgress(downloadState.getDownloadProgress());
                        downloadProgressThread = downloadHelper.new DownloadProgressThread(paper.getDownloadId(), paper.getId());
                        downloadProgressThread.start();
                    } else setProgress(0);
                }

            }
        }


        private void setProgress(int value) {
            progress.setProgress(100 - value);
        }

        private void setDownloadProgress(int value) {
            setProgress((value * 50) / 100);
        }

        private void setUnzipProgress(int value) {
            setProgress(((value * 50) / 100) + 50);
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onDownloadProgress(DownloadProgressEvent event) {
            if (_paper != null) {
                if (_paper.getId() == event.getPaperId()) {
                    setDownloadProgress(event.getProgress());
                }
            }
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onUnzipProgress(UnzipProgressEvent event) {
            if (_paper != null) {
                if (_paper.getId() == event.getPaperId()) {
                    setUnzipProgress(event.getProgress());
                }
            }
        }
    }

    public interface OnItemClickListener {
        public void onItemClick(View v, int position, Paper paper);
    }

    public interface OnItemLongClickListener {
        public boolean onItemLongClick(View v, int position, Paper paper);
    }

    private static abstract class MissingCoverCallback implements Callback {

        final ImageView imageView;
        final Paper paper;

        protected MissingCoverCallback(ImageView imageView, Paper paper) {
            this.paper = paper;
            this.imageView = imageView;
        }

        @Override
        public void onError() {
            onError(imageView, paper);
        }
        @Override
        public void onSuccess() {
            onSuccess(paper);
        }

        public abstract void onSuccess(Paper paper);

        public abstract void onError(ImageView imageView, Paper paper);
    }

}




