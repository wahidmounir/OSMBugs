package org.gittner.osmbugs.activities;

import android.Manifest;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.greysonparrelli.permiso.Permiso;
import com.tmtron.greenannotations.EventBusGreenRobot;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.OnActivityResult.Extra;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.ViewById;
import org.gittner.osmbugs.Helpers.EmailFeedbackStarter;
import org.gittner.osmbugs.R;
import org.gittner.osmbugs.bugs.Bug;
import org.gittner.osmbugs.bugs.BugOverlayItem;
import org.gittner.osmbugs.bugs.KeeprightBug;
import org.gittner.osmbugs.bugs.MapdustBug;
import org.gittner.osmbugs.bugs.OsmNote;
import org.gittner.osmbugs.bugs.OsmoseBug;
import org.gittner.osmbugs.common.MapScrollWatcher;
import org.gittner.osmbugs.common.MyLocationOverlay;
import org.gittner.osmbugs.events.BugsChangedEvent;
import org.gittner.osmbugs.loader.Loader;
import org.gittner.osmbugs.platforms.Platforms;
import org.gittner.osmbugs.statics.Images;
import org.gittner.osmbugs.statics.Settings;
import org.gittner.osmbugs.statics.TileSources;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;

@EActivity(R.layout.activity_bug_map)
@OptionsMenu(R.menu.bug_map)
public class BugMapActivity extends AppCompatActivity
{
    private static final String TAG = "OsmBugsActivity";

    /* Request Codes for activities */
    private static final int REQUEST_CODE_KEEPRIGHT_EDIT_ACTIVITY = 1;
    private static final int REQUEST_CODE_OSMOSE_EDIT_ACTIVITY = 2;
    private static final int REQUEST_CODE_MAPDUST_EDIT_ACTIVITY = 3;
    private static final int REQUEST_CODE_OSM_NOTE_EDIT_ACTIVITY = 4;
    private static final int REQUEST_CODE_SETTINGS_ACTIVITY = 5;
    private static final int REQUEST_CODE_BUG_LIST_ACTIVITY = 6;
    private static final int REQUEST_CODE_ADD_MAPDUST_BUG_ACTIVITY = 7;
    private static final int REQUEST_CODE_ADD_OSM_NOTE_BUG_ACTIVITY = 8;

    @ViewById(R.id.mapview)
    MapView mMap;
    @ViewById(R.id.progressBar)
    ContentLoadingProgressBar mProgressBar;
    @ViewById(R.id.btnRefreshBugs)
    FloatingActionButton mRefreshButton;
    @ViewById(R.id.toolbar)
    Toolbar mActionBar;

    @OptionsMenuItem(R.id.add_bug)
    MenuItem mMenuAddBug;
    @OptionsMenuItem(R.id.enable_gps)
    MenuItem mMenuEnableGps;
    @OptionsMenuItem(R.id.list)
    MenuItem mMenuList;

    @EventBusGreenRobot
    EventBus mEventBus;

    /* The next touch event on the map opens the add Bug Prompt */
    private boolean mAddNewBugOnNextClick = false;
    /* The Overlay for Bugs displayed on the map */
    private ItemizedIconOverlay<BugOverlayItem> mKeeprightOverlay;
    private ItemizedIconOverlay<BugOverlayItem> mOsmoseOverlay;
    private ItemizedIconOverlay<BugOverlayItem> mMapdustOverlay;
    private ItemizedIconOverlay<BugOverlayItem> mOsmNotesOverlay;

    /* The Location Marker Overlay */
    private MyLocationOverlay mLocationOverlay = null;

    private static GeoPoint mNewBugLocation;

    private MapScrollWatcher mMapScrollWatcher = null;

    private final MyLocationOverlay.FollowModeListener mFollowModeListener = () ->
    {
        Settings.setFollowGps(false);
        invalidateOptionsMenu();
    };

    @AfterViews
    void init()
    {
        setSupportActionBar(mActionBar);

        /* Create Bug Overlays */
        mKeeprightOverlay = new ItemizedIconOverlay<>(
                new ArrayList<>(),
                Images.get(R.drawable.keepright_zap),
                new LaunchEditorListener(REQUEST_CODE_KEEPRIGHT_EDIT_ACTIVITY),
                this);

        mOsmoseOverlay = new ItemizedIconOverlay<>(
                new ArrayList<>(),
                Images.get(R.drawable.osmose_marker_b_0),
                new LaunchEditorListener(REQUEST_CODE_OSMOSE_EDIT_ACTIVITY),
                this);

        mMapdustOverlay = new ItemizedIconOverlay<>(
                new ArrayList<>(),
                Images.get(R.drawable.mapdust_other),
                new LaunchEditorListener(REQUEST_CODE_MAPDUST_EDIT_ACTIVITY),
                this);

        mOsmNotesOverlay = new ItemizedIconOverlay<>(
                new ArrayList<>(),
                Images.get(R.drawable.osm_notes_open_bug),
                new LaunchEditorListener(REQUEST_CODE_OSM_NOTE_EDIT_ACTIVITY),
                this);

        /* Add all bugs to the Map */
        for (KeeprightBug bug : Platforms.KEEPRIGHT.getBugs())
        {
            mKeeprightOverlay.addItem(new BugOverlayItem(bug));
        }
        for (OsmoseBug bug : Platforms.OSMOSE.getBugs())
        {
            mOsmoseOverlay.addItem(new BugOverlayItem(bug));
        }
        for (MapdustBug bug : Platforms.MAPDUST.getBugs())
        {
            mMapdustOverlay.addItem(new BugOverlayItem(bug));
        }
        for (OsmNote note : Platforms.OSM_NOTES.getBugs())
        {
            mOsmNotesOverlay.addItem(new BugOverlayItem(note));
        }

        /* Setup Main MapView */
        mMap.setMultiTouchControls(true);
        mMap.setBuiltInZoomControls(true);

        /* This adds an empty Overlay to retrieve the Touch Events. */
        mMap.getOverlays().add(new Overlay(this)
        {
            @Override
            public void draw(Canvas c, MapView osmv, boolean shadow)
            {

            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean onTouchEvent(MotionEvent event, MapView mapView)
            {
                if (event.getAction() == MotionEvent.ACTION_DOWN && mAddNewBugOnNextClick)
                {
                    mNewBugLocation = (GeoPoint) mMap.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                    startCreateBug();
                    mAddNewBugOnNextClick = false;
                    invalidateOptionsMenu();
                    return false;
                }
                return super.onTouchEvent(event, mapView);
            }

            @Override
            public boolean onLongPress(MotionEvent e, MapView mapView)
            {
                mNewBugLocation = (GeoPoint) mMap.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                startCreateBug();
                mAddNewBugOnNextClick = false;
                invalidateOptionsMenu();
                return false;
            }
        });
        mMap.getController().setZoom(Math.min(Settings.getLastZoom(), mMap.getTileProvider().getMaximumZoomLevel()));
        mMap.getController().setCenter(Settings.getLastMapCenter());
    }


    private void startCreateBug()
    {
        /* If a default bug creation platform is selected, we start the Activity directly.
         *  Otherwise we ask the user on which platform we should create the bug */
        if(Settings.getDefaultNewBugPlatform() == 2) {
            launchCreateOsmNoteActivity();
        } else if(Settings.getDefaultNewBugPlatform() == 3) {
            launchCreateMapdustBugActivity();
        } else
        {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.create_bug)
                    .setCancelable(true)
                    .setItems(R.array.new_bug_platforms, (dialogInterface, i) -> {
                        if (i == 0)
                        {
                            launchCreateOsmNoteActivity();
                        } else if (i == 1)
                        {
                            launchCreateMapdustBugActivity();
                        }
                    })
                    .show();
        }
    }


    private void launchCreateOsmNoteActivity()
    {
        Intent addBugIntent = new Intent(BugMapActivity.this, AddOsmNoteActivity_.class);
        addBugIntent.putExtra(AddOsmNoteActivity.EXTRA_LATITUDE, mNewBugLocation.getLatitude());
        addBugIntent.putExtra(AddOsmNoteActivity.EXTRA_LONGITUDE, mNewBugLocation.getLongitude());
        startActivityForResult(addBugIntent, REQUEST_CODE_ADD_OSM_NOTE_BUG_ACTIVITY);
    }


    private void launchCreateMapdustBugActivity()
    {
        Intent addBugIntent = new Intent(BugMapActivity.this, AddMapdustBugActivity_.class);
        addBugIntent.putExtra(AddMapdustBugActivity_.EXTRA_LATITUDE, mNewBugLocation.getLatitude());
        addBugIntent.putExtra(AddMapdustBugActivity_.EXTRA_LONGITUDE, mNewBugLocation.getLongitude());
        startActivityForResult(addBugIntent, REQUEST_CODE_ADD_MAPDUST_BUG_ACTIVITY);
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Permiso.getInstance().setActivity(this);

        Permiso.getInstance().requestPermissions(new Permiso.IOnPermissionResult()
        {
            @Override
            public void onPermissionResult(Permiso.ResultSet resultSet)
            {
            }

            @Override
            public void onRationaleRequested(Permiso.IOnRationaleProvided callback, String... permissions)
            {
                Permiso.getInstance().showRationaleInDialog(
                        getString(R.string.title_request_permissions),
                        getString(R.string.message_request_permissions),
                        null,
                        callback);
            }
        }, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }


    @Override
    public void onPause()
    {
        super.onPause();

        Settings.setLastMapCenter(mMap.getBoundingBox().getCenter());
        Settings.setLastZoom(mMap.getZoomLevel());

        mMap.getOverlays().remove(mKeeprightOverlay);
        mMap.getOverlays().remove(mOsmoseOverlay);
        mMap.getOverlays().remove(mMapdustOverlay);
        mMap.getOverlays().remove(mOsmNotesOverlay);

        mLocationOverlay.disableFollowLocation();
        mLocationOverlay.disableMyLocation();

        if (mMapScrollWatcher != null)
        {
            mMapScrollWatcher.cancel();
        }

        mMap.onPause();
    }


    @Override
    public void onResume()
    {
        super.onResume();

        Permiso.getInstance().setActivity(this);

        mMap.onResume();

		/* Display enabled Bug platforms */
        if (Settings.Keepright.isEnabled())
        {
            mMap.getOverlays().add(mKeeprightOverlay);
        }
        if (Settings.Osmose.isEnabled())
        {
            mMap.getOverlays().add(mOsmoseOverlay);
        }
        if (Settings.Mapdust.isEnabled())
        {
            mMap.getOverlays().add(mMapdustOverlay);
        }
        if (Settings.OsmNotes.isEnabled())
        {
            mMap.getOverlays().add(mOsmNotesOverlay);
        }

        mMap.setTileSource(TileSources.getInstance().getPreferredTileSource());

        setupLocationOverlay();

        mMap.invalidate();

        if (Settings.getAutoLoad())
        {
            mRefreshButton.setVisibility(View.GONE);

            mMapScrollWatcher = new MapScrollWatcher(mMap, () -> Platforms.ALL_PLATFORMS.loadIfEnabled(mMap.getBoundingBox()));
        }
        else
        {
            mRefreshButton.setVisibility(View.VISIBLE);
        }

        if (Platforms.ALL_PLATFORMS.getLoaderState() == Loader.LOADING)
        {
            mProgressBar.show();
        }
        else
        {
            mProgressBar.hide();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Permiso.getInstance().onRequestPermissionResult(requestCode, permissions, grantResults);
    }

    @Click(R.id.btnRefreshBugs)
    void onRefreshButtonClicked()
    {
        Platforms.ALL_PLATFORMS.loadIfEnabled(mMap.getBoundingBox());
    }


    private void setupLocationOverlay()
    {
        if (mLocationOverlay == null)
        {
            mLocationOverlay = new MyLocationOverlay(mMap, mFollowModeListener);
        }

        if (Settings.getEnableGps())
        {
            mLocationOverlay.enableMyLocation();
            if (!mMap.getOverlays().contains(mLocationOverlay))
            {
                mMap.getOverlays().add(mLocationOverlay);
            }
        }
        else
        {
            mLocationOverlay.disableMyLocation();
            mMap.getOverlays().remove(mLocationOverlay);
        }

        if (Settings.getFollowGps())
        {
            mLocationOverlay.enableFollowLocation();
        }
        else
        {
            mLocationOverlay.disableFollowLocation();
        }
    }


    @OnActivityResult(REQUEST_CODE_KEEPRIGHT_EDIT_ACTIVITY)
    void onKeeprightEditActivityResult(int resultCode)
    {
        if (resultCode == RESULT_OK)
        {
            Platforms.KEEPRIGHT.getLoader().getQueue().add(mMap.getBoundingBox());
        }
    }


    @OnActivityResult(REQUEST_CODE_OSMOSE_EDIT_ACTIVITY)
    void onOsmoseEditActivityResult(int resultCode)
    {
        if (resultCode == RESULT_OK)
        {
            Platforms.OSMOSE.getLoader().getQueue().add(mMap.getBoundingBox());
        }
    }


    @OnActivityResult(REQUEST_CODE_MAPDUST_EDIT_ACTIVITY)
    void onMapdustEditActivityResult(int resultCode)
    {
        if (resultCode == RESULT_OK)
        {
            Platforms.MAPDUST.getLoader().getQueue().add(mMap.getBoundingBox());
        }
    }


    @OnActivityResult(REQUEST_CODE_OSM_NOTE_EDIT_ACTIVITY)
    void onOsmNoteEditActivityResult(int resultCode)
    {
        if (resultCode == RESULT_OK)
        {
            Platforms.OSM_NOTES.getLoader().getQueue().add(mMap.getBoundingBox());
        }
    }


    @OnActivityResult(REQUEST_CODE_BUG_LIST_ACTIVITY)
    void onListActivityResult(int resultCode, @Extra(value = BugListActivity.RESULT_EXTRA_BUG) Bug bug)
    {
        if (resultCode == BugListActivity.RESULT_BUG_MINI_MAP_CLICKED)
        {
            mMap.getController().setCenter(bug.getPoint());
            mMap.getController().setZoom(17);

            Settings.setFollowGps(false);

            invalidateOptionsMenu();
        }
    }


    @OnActivityResult(REQUEST_CODE_ADD_MAPDUST_BUG_ACTIVITY)
    void onAddMapdustBugActivityResult(int resultCode)
    {
        if (resultCode == RESULT_OK)
        {
            Platforms.MAPDUST.getLoader().getQueue().add(mMap.getBoundingBox());
        }
    }


    @OnActivityResult(REQUEST_CODE_ADD_OSM_NOTE_BUG_ACTIVITY)
    void onAddOsmNoteActivityResult(int resultCode)
    {
        if (resultCode == RESULT_OK)
        {
            Platforms.OSM_NOTES.getLoader().getQueue().add(mMap.getBoundingBox());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        mMenuEnableGps.setChecked(Settings.getEnableGps());

        if (mAddNewBugOnNextClick)
        {
            mMenuAddBug.setIcon(Images.get(R.drawable.ic_menu_add_bug_red));
        }
        else
        {
            mMenuAddBug.setIcon(Images.get(R.drawable.ic_menu_add_bug));
        }

        mMenuList.setVisible(
                Settings.Keepright.isEnabled()
                || Settings.Osmose.isEnabled()
                || Settings.Mapdust.isEnabled()
                || Settings.OsmNotes.isEnabled());

        return true;
    }


    @OptionsItem(R.id.settings)
    void menuSettings()
    {
        Intent i = new Intent(this, SettingsActivity_.class);
        startActivityForResult(i, REQUEST_CODE_SETTINGS_ACTIVITY);
    }


    @OptionsItem(R.id.list)
    void menuListClicked()
    {
        BugListActivity_.intent(this).startForResult(REQUEST_CODE_BUG_LIST_ACTIVITY);
    }


    @OptionsItem(R.id.center_on_gps)
    void menuCenterOnGpsClicked()
    {
        Settings.setFollowGps(true);

        setupLocationOverlay();
    }


    @OptionsItem(R.id.feedback)
    void onFeedbackClicked()
    {
        EmailFeedbackStarter.start(this);
    }


    @OptionsItem(R.id.enable_gps)
    void menuEnableGPSClicked()
    {
        Settings.setEnableGps(!Settings.getEnableGps());

        invalidateOptionsMenu();

        setupLocationOverlay();
    }


    @OptionsItem(R.id.add_bug)
    void menuAddBugClicked()
    {
        mAddNewBugOnNextClick = !mAddNewBugOnNextClick;

        invalidateOptionsMenu();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBugsChanged(BugsChangedEvent event)
    {
        if (event.getPlatform() == Platforms.KEEPRIGHT)
        {
            mKeeprightOverlay.removeAllItems();

            for (KeeprightBug bug : Platforms.KEEPRIGHT.getBugs())
            {
                mKeeprightOverlay.addItem(new BugOverlayItem(bug));
            }
        }
        else if (event.getPlatform() == Platforms.OSMOSE)
        {
            mOsmoseOverlay.removeAllItems();

            for (OsmoseBug bug : Platforms.OSMOSE.getBugs())
            {
                mOsmoseOverlay.addItem(new BugOverlayItem(bug));
            }
        }
        else if (event.getPlatform() == Platforms.MAPDUST)
        {
            mMapdustOverlay.removeAllItems();

            for (MapdustBug bug : Platforms.MAPDUST.getBugs())
            {
                mMapdustOverlay.addItem(new BugOverlayItem(bug));
            }
        }
        else if (event.getPlatform() == Platforms.OSM_NOTES)
        {
            mOsmNotesOverlay.removeAllItems();

            for (OsmNote bug : Platforms.OSM_NOTES.getBugs())
            {
                mOsmNotesOverlay.addItem(new BugOverlayItem(bug));
            }
        }

        mMap.invalidate();
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoaderStateChanged(Loader.StateChangedEvent event)
    {
        if (event.getState() == Loader.FAILED)
        {
            String text = getString(R.string.failed_to_download_from) + " " + event.getPlatform().getName();

            Toast.makeText(BugMapActivity.this, text, Toast.LENGTH_LONG).show();
        }

        if (Platforms.ALL_PLATFORMS.getLoaderState() == Loader.LOADING)
        {
            mProgressBar.show();
        }
        else
        {
            mProgressBar.hide();
        }
    }


    private class LaunchEditorListener implements ItemizedIconOverlay.OnItemGestureListener<BugOverlayItem>
    {
        final int mRequestCode;


        public LaunchEditorListener(int requestCode)
        {
            mRequestCode = requestCode;
        }


        @Override
        public boolean onItemSingleTapUp(int index, BugOverlayItem bugItem)
        {
            BugEditActivity_.intent(BugMapActivity.this)
                    .mBug(bugItem.getBug())
                    .startForResult(mRequestCode);
            return true;
        }


        @Override
        public boolean onItemLongPress(int index, BugOverlayItem item)
        {
            return false;
        }
    }
}
