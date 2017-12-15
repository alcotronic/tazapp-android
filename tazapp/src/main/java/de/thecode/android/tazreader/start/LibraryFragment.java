package de.thecode.android.tazreader.start;


import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import de.thecode.android.tazreader.R;
import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.TazSettings;
import de.thecode.android.tazreader.job.SyncJob;
import de.thecode.android.tazreader.start.viewmodel.LibraryViewModel;
import de.thecode.android.tazreader.start.viewmodel.StartViewModel;
import de.thecode.android.tazreader.sync.SyncStateChangedEvent;
import de.thecode.android.tazreader.utils.BaseFragment;
import de.thecode.android.tazreader.widget.AutofitRecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import timber.log.Timber;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends BaseFragment<StartActivity> {


    LibraryAdapterNew newAdapter;
    LibraryViewModel  libraryViewModel;
    StartViewModel    activityViewModel;

    SwipeRefreshLayout swipeRefresh;

    ActionMode actionMode;

    boolean isSyncing;

    private AutofitRecyclerView  recyclerView;
    private FloatingActionButton fabArchive;

    private TazSettings.OnPreferenceChangeListener demoModeChangedListener = new TazSettings.OnPreferenceChangeListener<Boolean>() {
        @Override
        public void onPreferenceChanged(Boolean value) {
            onDemoModeChanged(value);
        }
    };

    public LibraryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activityViewModel = ViewModelProviders.of(getMyActivity())
                                              .get(StartViewModel.class);
        activityViewModel.getCurrentFragment().setValue(this.getClass());
        activityViewModel.getActionMode()
                         .observe(this, actionModeBoolean -> {
                             if (actionModeBoolean == null) actionModeBoolean = false;
                             if (actionModeBoolean && actionMode == null) {
                                 getActivity().startActionMode(new ActionModeCallback());
                             }
                         });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        libraryViewModel = ViewModelProviders.of(this)
                                             .get(LibraryViewModel.class);
        newAdapter = new LibraryAdapterNew(new LibraryAdapterNew.LibraryInteractionListener() {
            @Override
            public void onClick(Paper paper) {
                Timber.d("clicked on Paper %s", paper);
                if (actionMode != null) {
                    onLongClick(paper);
                } else {

                }
            }

            @Override
            public void onLongClick(Paper paper) {
                Timber.d("long clicked on Paper %s", paper);
                newAdapter.changeSelection(paper);
                if (actionMode != null) actionMode.invalidate();
                activityViewModel.getActionMode()
                                 .setValue(true);
            }
        });
        libraryViewModel.getPapers()
                        .observe(this, new Observer<List<Paper>>() {
                            @Override
                            public void onChanged(@Nullable List<Paper> papers) {
                                newAdapter.update(papers);
                                newAdapter.select(libraryViewModel.getSelectedPapers()
                                                                  .getValue());
                            }
                        });

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.start_library, container, false);

        swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefreshLayout);
        swipeRefresh.setColorSchemeResources(R.color.refresh_color_1, R.color.refresh_color_2);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                hideFab();
                SyncJob.scheduleJobImmediately(true);
                //SyncHelper.requestSync(getContext());
            }
        });


        recyclerView = (AutofitRecyclerView) view.findViewById(R.id.recycler);
        recyclerView.setHasFixedSize(true);

        fabArchive = (FloatingActionButton) view.findViewById(R.id.fabArchive);
        fabArchive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() != null) getMyActivity().showArchiveYearPicker();
            }
        });

        showFab();

        recyclerView.setAdapter(newAdapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    AutofitRecyclerView.AutoFitGridLayoutManager lm = ((AutofitRecyclerView) recyclerView).getLayoutManager();
                    int lastCompletlyVisible = lm.findLastCompletelyVisibleItemPosition();
                    int itemCount = lm.getItemCount();
                    if (lastCompletlyVisible == itemCount - 1) {
                        showFab();
                    }
                }

            }
        });


//        if (hasCallback()) getCallback().onUpdateDrawer(this);
//        getLoaderManager().initLoader(0, null, this);


        //ActionMode enabling after view because of theme bugs
//        view.post(new Runnable() {
//            @Override
//            public void run() {
//                if (hasCallback()) {
//
//                    if (getCallback().getRetainData()
//                                     .isActionMode()) setActionMode();
//                }
//            }
//        });

        setHasOptionsMenu(true);

        return view;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.start_library, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.ic_action_edit:
                activityViewModel.getActionMode()
                                 .setValue(true);
//                setActionMode();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
//        Timber.d("%s", TazSettings.getInstance(getActivity())
//                                   .getPrefBoolean(TazSettings.PREFKEY.FORCESYNC, false));
//        if (TazSettings.getInstance(getActivity())
//                       .getPrefBoolean(TazSettings.PREFKEY.FORCESYNC, false)) {
//
//            SyncHelper.requestSync(getContext());
//        }
    }

    @Override
    public void onPause() {
        libraryViewModel.getSelectedPapers()
                        .setValue(newAdapter.getSelectedPapers());
//        if (hasCallback()) getCallback().getRetainData()
//                                        .removeOpenPaperIdAfterDownload();
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        TazSettings.getInstance(getContext())
                   .addOnPreferenceChangeListener(TazSettings.PREFKEY.DEMOMODE, demoModeChangedListener);
        EventBus.getDefault()
                .register(this);
    }

    @Override
    public void onStop() {
        TazSettings.getInstance(getContext())
                   .removeOnPreferenceChangeListener(demoModeChangedListener);
        EventBus.getDefault()
                .unregister(this);
        super.onStop();
    }

    private void onDemoModeChanged(boolean demoMode) {
        showFab();
//        getLoaderManager().restartLoader(0, null, this);
    }

//    @Override
//    public void onDestroyView() {
//
//        int firstVisible = recyclerView.findFirstVisibleItemPosition();
//        int lastVisible = recyclerView.findLastVisibleItemPosition();
//        for (int i = firstVisible; i <= lastVisible; i++) {
//            LibraryAdapter.ViewHolder vh = (LibraryAdapter.ViewHolder) recyclerView.findViewHolderForLayoutPosition(i);
//            if (vh != null) {
//                EventBus.getDefault()
//                        .unregister(vh);
//            }
//        }
//        adapter.destroy();
//        super.onDestroyView();
//    }

//    @Override
//    public void onDestroy() {
//        adapter.removeClickLIstener();
//        super.onDestroy();
//    }

//    @Override
//    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
//
//        super.onActivityCreated(savedInstanceState);
//    }

//    @Override
//    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
//
//
//        StringBuilder selection = new StringBuilder();
//        boolean demo = true;
//        if (hasCallback()) {
//            demo = TazSettings.getInstance(getContext())
//                              .isDemoMode();
//        }
//
//        if (demo) selection.append("(");
//        selection.append(Paper.Columns.FULL_VALIDUNTIL)
//                 .append(" > ")
//                 .append(System.currentTimeMillis() / 1000);
//
//        if (demo) {
//            selection.append(" AND ");
//            selection.append(Paper.Columns.ISDEMO)
//                     .append("=1");
//            selection.append(")");
//        }
//        selection.append(" OR ")
//                 .append(Paper.Columns.ISDOWNLOADED)
//                 .append("=1");
//        selection.append(" OR ")
//                 .append(Paper.Columns.DOWNLOADID)
//                 .append("!=0");
//        selection.append(" OR ")
//                 .append(Paper.Columns.IMPORTED)
//                 .append("=1");
//        selection.append(" OR ")
//                 .append(Paper.Columns.HASUPDATE)
//                 .append("=1");
//        selection.append(" OR ")
//                 .append(Paper.Columns.KIOSK)
//                 .append("=1");
//
//        return new CursorLoader(getActivity(), Paper.CONTENT_URI, null, selection.toString(), null, Paper.Columns.DATE + " DESC");
//    }
//
//    @Override
//    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
//        Timber.i("loader: %s, data: %s", loader, data);
//        adapter.swapCursor(data);
//    }
//
//    @Override
//    public void onLoaderReset(Loader<Cursor> loader) {
//        Timber.d("loader: %s", loader);
//    }


    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onSyncStateChanged(SyncStateChangedEvent event) {
        isSyncing = event.isRunning();
        Timber.d("SyncStateChanged running: %s", isSyncing);
        if (swipeRefresh.isRefreshing() != event.isRunning()) swipeRefresh.setRefreshing(event.isRunning());
        if (isSyncing) hideFab();
        else showFab();
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onCoverDowloaded(CoverDownloadedEvent event) {
//        try {
//            LibraryAdapter.ViewHolder viewHolder = (LibraryAdapter.ViewHolder) recyclerView.findViewHolderForItemId(event.getBookId());
//            if (viewHolder != null) viewHolder.image.setTag(null);
//            newAdapter.notifyItemChanged(newAdapter.getItemPosition(event.getBookId()));
//        } catch (IllegalStateException e) {
//            Timber.w(e);
//        }
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScrollToPaper(ScrollToPaperEvent event) {
        Timber.d("event: %s", event);
        if (recyclerView != null && newAdapter != null) {
            recyclerView.smoothScrollToPosition(newAdapter.getPositionForBookId(event.getBookId()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDrawerStateChanged(DrawerStateChangedEvent event) {
        Timber.d("event: %s", event.getNewState());
        if (event.getNewState() == DrawerLayout.STATE_IDLE) swipeRefresh.setEnabled(true);
        else swipeRefresh.setEnabled(false);
    }


//    @Override
//    public void onItemClick(View v, int position, Paper paper) {
//        Timber.d("v: %s, position: %s, paper: %s", v, position, paper);
//        if (actionMode != null) onItemLongClick(v, position, paper);
//        else {
//            switch (paper.getState()) {
//                case Paper.DOWNLOADED_READABLE:
//                case Paper.DOWNLOADED_BUT_UPDATE:
//                    //openPlayer(paper.getId());
//                    if (hasCallback()) getCallback().openReader(paper.getId());
//                    break;
//                case Paper.IS_DOWNLOADING:
//
//                    break;
//
//                case Paper.NOT_DOWNLOADED:
//                case Paper.NOT_DOWNLOADED_IMPORT:
//                    try {
//                        if (hasCallback()) getCallback().startDownload(paper.getId());
//                    } catch (Paper.PaperNotFoundException e) {
//                        Timber.e(e);
//                    }
//                    break;
//
//            }
//
//        }
//    }


//    @Override
//    public boolean onItemLongClick(View v, int position, Paper paper) {
//        setActionMode();
//        Timber.d("v: %s, position: %s, paper: %s", v, position, paper);
//        ;
//        if (!adapter.isSelected(paper.getId())) selectPaper(paper.getId());
//        else deselectPaper(paper.getId());
//        return true;
//    }


    private void deleteSelected() {
        //TODO deleteSelected
//        if (adapter.getSelected() != null && adapter.getSelected()
//                                                    .size() > 0) {
//            Long[] ids = adapter.getSelected()
//                                .toArray(new Long[adapter.getSelected()
//                                                         .size()]);
//            if (hasCallback()) getCallback().getRetainData()
//                                            .deletePaper(ids);
//        }
    }

    private void downloadSelected() {
        //TODO downloadSelected

//        for (Long bookId : adapter.getSelected()) {
//            try {
//                Paper paper = Paper.getPaperWithId(getContext(), bookId);
//                if (paper == null) throw new Paper.PaperNotFoundException();
//                if (hasCallback()) getCallback().startDownload(paper.getId());
//            } catch (Paper.PaperNotFoundException e) {
//                Timber.e(e);
//            }
//        }
//        adapter.deselectAll();
    }


    private void showFab() {
        if (!TazSettings.getInstance(getContext())
                        .isDemoMode()) {
            if (!isSyncing) {
                fabArchive.show();
            }
        } else {
            hideFab();
        }
    }

    private void hideFab() {
        fabArchive.hide();
    }


//    public void setActionMode() {
//        if (actionMode == null) getActivity().startActionMode(new ActionModeCallback());
//    }

//    public void selectPaper(long bookId) {
//        if (adapter != null) adapter.select(bookId);
//        if (actionMode != null) actionMode.invalidate();
//    }
//
//    public void deselectPaper(long bookId) {
//        if (adapter != null) adapter.deselect(bookId);
//        if (actionMode != null) actionMode.invalidate();
//    }

    class ActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            Timber.d("mode: %s, menu: %s", mode, menu);
            activityViewModel.getActionMode()
                             .setValue(true);
            actionMode = mode;
            swipeRefresh.setEnabled(false);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            Timber.d("mode: %s, menu: %s", mode, menu);
            menu.clear();
            int countSelected = newAdapter.getSelectedPapers()
                                          .size();
            mode.setTitle(getString(R.string.string_library_selected, countSelected));
            mode.getMenuInflater()
                .inflate(R.menu.start_library_selectmode, menu);
            if (countSelected == 0) {
                menu.findItem(R.id.ic_action_download)
                    .setEnabled(false);
                menu.findItem(R.id.ic_action_delete)
                    .setEnabled(false);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Timber.d("mode: %s, item: %s", mode, item);
            switch (item.getItemId()) {
                case R.id.ic_action_download:
                    downloadSelected();
                    mode.finish();
                    return true;
                case R.id.ic_action_delete:
                    deleteSelected();
                    mode.finish();
                    return true;
                case R.id.ic_action_selectnone:
                    newAdapter.deselectAll();
                    mode.invalidate();
                    return true;
                case R.id.ic_action_selectall:
                    newAdapter.selectAll();
                    mode.invalidate();
                    return true;
                case R.id.ic_action_selectinvert:
                    newAdapter.invertSelection();
                    mode.invalidate();
                    return true;
                case R.id.ic_action_selectnotloaded:
                    newAdapter.selectNotDownloaded();
                    mode.invalidate();
                    return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            Timber.d("mode: %s", mode);
            newAdapter.deselectAll();
            activityViewModel.getActionMode()
                             .setValue(false);
            swipeRefresh.setEnabled(true);
            actionMode = null;
        }

    }

}
