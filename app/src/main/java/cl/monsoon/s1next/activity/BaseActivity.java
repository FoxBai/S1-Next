package cl.monsoon.s1next.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.melnykov.fab.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cl.monsoon.s1next.Api;
import cl.monsoon.s1next.MyApplication;
import cl.monsoon.s1next.R;
import cl.monsoon.s1next.fragment.SettingsFragment;
import cl.monsoon.s1next.singleton.Config;
import cl.monsoon.s1next.singleton.MyAccount;
import cl.monsoon.s1next.singleton.MyOkHttpClient;
import cl.monsoon.s1next.util.LollipopUtil;
import cl.monsoon.s1next.util.ObjectUtil;
import cl.monsoon.s1next.util.ResourceUtil;
import cl.monsoon.s1next.widget.InsetsFrameLayout;
import cl.monsoon.s1next.widget.MyRecyclerView;

/**
 * A base Activity which includes the Toolbar tweaks,
 * drop-down navigation, navigation drawer amongst others.
 * Also changes theme depends on settings.
 */
public abstract class BaseActivity extends ActionBarActivity
        implements MyAccount.OnLogoutListener,
        InsetsFrameLayout.OnInsetsCallback {

    private Rect mSystemWindowInsets;
    private final List<InsetsFrameLayout.OnInsetsCallback> onInsetsCallbackList =
            Collections.synchronizedList(new ArrayList<>());

    private Toolbar mToolbar;
    private boolean mIsToolbarShown = true;

    /**
     * We need to set up overlay Toolbar if we want to enable Toolbar's auto show/hide effect.
     * We could start hiding Toolbar if main content has been full covered by Toolbar,
     * that's why this value always equals to Toolbar's height.
     */
    private int mToolbarAutoHideMinY;

    private DrawerLayout mDrawerLayout;
    private View mDrawer;
    private ActionBarDrawerToggle mDrawerToggle;
    private boolean mHasNavDrawer = true;
    private boolean mHasNavDrawerIndicator = true;

    private View mDrawerTopBackgroundView;
    private ImageView mDrawerUserAvatarView;
    private TextView mDrawerUsernameView;

    private FloatingActionButton mFloatingActionButton;

    /**
     * Either {@link cl.monsoon.s1next.fragment.SettingsFragment#ACTION_CHANGE_THEME}
     * or {@link cl.monsoon.s1next.fragment.SettingsFragment#ACTION_CHANGE_FONT_SIZE}.
     */
    private BroadcastReceiver mRecreateActivityReceiver;

    private BroadcastReceiver mUserLoginStatusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!Config.isDefaultApplicationTheme()) {
            setTheme(Config.getCurrentTheme());
        }

        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LollipopUtil.adjustTaskDescription(this);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SettingsFragment.ACTION_CHANGE_THEME);
        intentFilter.addAction(SettingsFragment.ACTION_CHANGE_FONT_SIZE);
        // recreate this Activity when night mode or font size setting changes
        mRecreateActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BaseActivity.this instanceof SettingsActivity
                        && intent.getAction().equals(SettingsFragment.ACTION_CHANGE_FONT_SIZE)) {
                    return;
                }

                recreate();
            }
        };
        registerReceiver(mRecreateActivityReceiver, intentFilter);

        intentFilter = new IntentFilter();
        intentFilter.addAction(MyAccount.ACTION_USER_LOGIN);
        intentFilter.addAction(MyAccount.ACTION_USER_COOKIE_EXPIRATION);
        // change drawer's top area depends on user's login status
        mUserLoginStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(MyAccount.ACTION_USER_LOGIN)) {
                    setupDrawerUserView();
                } else {
                    setupDrawerLoginPrompt();
                }
            }
        };
        registerReceiver(mUserLoginStatusReceiver, intentFilter);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setUpToolbar();

        InsetsFrameLayout insetsFrameLayout =
                (InsetsFrameLayout) findViewById(R.id.insets_frame_layout);
        if (insetsFrameLayout != null) {
            insetsFrameLayout.setOnInsetsCallback(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mRecreateActivityReceiver);
        unregisterReceiver(mUserLoginStatusReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app drawer touch event.
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                if (NavUtils.getParentActivityName(this) != null) {
                    NavUtils.navigateUpFromSameTask(this);
                } else {
                    super.onBackPressed();
                }

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (mHasNavDrawer) {
            setupNavDrawer();

            // Syncs the toggle state after onRestoreInstanceState has occurred.
            if (mDrawerToggle != null) {
                mDrawerToggle.syncState();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    void enableWindowTranslucentStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    private void setUpToolbar() {
        if (mToolbar == null) {
            mToolbar = (Toolbar) findViewById(R.id.toolbar);
            if (mToolbar != null) {
                // designate a Toolbar as the ActionBar
                setSupportActionBar(mToolbar);
            }
        }
    }

    /**
     * This method only useful if API >= 19 (which we can enable translucent status bar).
     * But we don't use `fitsSystemWindows` to adjust layout
     * in order to show overlay status bar & Toolbar.
     */
    @Override
    public void onInsetsChanged(@NonNull Rect insets) {
        mSystemWindowInsets = insets;

        int insetsTop = insets.top;

        if (mToolbar != null) {
            // We need to set Toolbar's padding
            // instead of translucent/transparent status bar.
            mToolbar.setPadding(0, insetsTop, 0, 0);
            mToolbar.getLayoutParams().height = insetsTop + ResourceUtil.getToolbarHeight();
            mToolbar.requestLayout();
        }

        if (mDrawerTopBackgroundView != null && mDrawerUserAvatarView != null) {
            // adjust drawer top background & user avatar's layout
            mDrawerTopBackgroundView.getLayoutParams().height =
                    insetsTop + getResources().getDimensionPixelSize(R.dimen.drawer_top_height);
            mDrawerTopBackgroundView.requestLayout();

            ViewGroup.MarginLayoutParams marginLayoutParams =
                    (ViewGroup.MarginLayoutParams) mDrawerUserAvatarView.getLayoutParams();
            marginLayoutParams.topMargin =
                    insetsTop + getResources().getDimensionPixelSize(R.dimen.drawer_avatar_margin_top);
            mDrawerUserAvatarView.requestLayout();
        }

        for (InsetsFrameLayout.OnInsetsCallback onInsetsCallback : onInsetsCallbackList) {
            onInsetsCallback.onInsetsChanged(insets);
        }
    }

    public void registerInsetsCallback(@NonNull InsetsFrameLayout.OnInsetsCallback onInsetsCallback) {
        onInsetsCallbackList.add(onInsetsCallback);
    }

    public void unregisterInsetsCallback(@NonNull InsetsFrameLayout.OnInsetsCallback onInsetsCallback) {
        onInsetsCallbackList.remove(onInsetsCallback);
    }

    void setupFloatingActionButton(@DrawableRes int resId) {
        mFloatingActionButton = (FloatingActionButton) findViewById(R.id.floating_action_button);
        // subclass need to implement android.view.View.OnClickListener
        mFloatingActionButton.setOnClickListener(ObjectUtil.cast(this, View.OnClickListener.class));
        mFloatingActionButton.setImageResource(resId);
        mFloatingActionButton.setVisibility(View.VISIBLE);
    }

    /**
     * Also enables {@link cl.monsoon.s1next.activity.BaseActivity#mFloatingActionButton}
     * auto show/hide effect.
     */
    public void enableToolbarAndFabAutoHideEffect(MyRecyclerView myRecyclerView, @Nullable RecyclerView.OnScrollListener onScrollListener) {
        mToolbarAutoHideMinY = ResourceUtil.getToolbarHeight();

        myRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(recyclerView, newState);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (onScrollListener != null) {
                    onScrollListener.onScrolled(recyclerView, dx, dy);
                }

                // myRecyclerView.computeVerticalScrollOffset() may cause poor performance
                // so we also check mIsToolbarShown though we will do it later (during showOrHideToolbarAndFab(boolean))
                if (mIsToolbarShown
                        && dy > 0
                        && myRecyclerView.computeVerticalScrollOffset() >= mToolbarAutoHideMinY) {
                    showOrHideToolbarAndFab(false);
                } else if (dy < 0) {
                    showOrHideToolbarAndFab(true);
                }
            }
        });
    }

    public void showOrHideToolbarAndFab(boolean show) {
        if (show == mIsToolbarShown) {
            return;
        }

        mIsToolbarShown = show;
        onToolbarAutoShowOrHide(show);
        onFloatingActionButtonAutoShowOrHide(show);
    }

    private void onToolbarAutoShowOrHide(boolean show) {
        if (show) {
            mToolbar.animate()
                    .alpha(1)
                    .translationY(0)
                    .setInterpolator(new DecelerateInterpolator());
        } else {
            mToolbar.animate()
                    .alpha(0)
                    .translationY(-mToolbar.getBottom())
                    .setInterpolator(new DecelerateInterpolator());
        }
    }

    private void onFloatingActionButtonAutoShowOrHide(boolean show) {
        if (mFloatingActionButton != null) {
            if (show) {
                mFloatingActionButton.show();
            } else {
                mFloatingActionButton.hide();
            }
        }
    }

    /**
     * Sets Toolbar's navigation icon to cross.
     */
    void setupNavCrossIcon() {
        if (mToolbar != null) {
            mToolbar.setNavigationIcon(ResourceUtil.getResourceId(getTheme(), R.attr.menuCross));
        }
    }

    private void setupNavDrawer() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout == null) {
            return;
        }

        mDrawer = mDrawerLayout.findViewById(R.id.drawer);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close) {
            /**
             * Only show items in the Toolbar relevant to this screen
             * if the drawer is not showing. Otherwise, let the drawer
             * decide what to show in the Toolbar.
             */
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                showOrHideToolbarAndFab(true);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                if (drawerView.getTag() instanceof Runnable) {
                    Runnable runnable = (Runnable) drawerView.getTag();
                    drawerView.setTag(null);
                    runnable.run();
                }
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        if (!mHasNavDrawerIndicator) {
            mDrawerToggle.setDrawerIndicatorEnabled(false);
        }

        // According to the Google Material Design,
        // width = screen width - app bar height in mobile.
        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);

        ViewGroup.LayoutParams layoutParams = mDrawer.getLayoutParams();
        layoutParams.width =
                point.x -
                        getResources().getDimensionPixelSize(
                                R.dimen.abc_action_bar_default_height_material);
        mDrawer.requestLayout();

        setupNavDrawerItem();
    }

    private void closeDrawer(@Nullable Runnable runnable) {
        if (mDrawerLayout != null && mDrawer != null) {
            mDrawerLayout.post(() -> {
                mDrawer.setTag(runnable);
                mDrawerLayout.closeDrawer(mDrawer);
            });
        }
    }

    private void setupNavDrawerItem() {
        if (mDrawerLayout == null || mDrawer == null) {
            return;
        }

        mDrawerTopBackgroundView = mDrawer.findViewById(R.id.drawer_top_background);
        mDrawerUserAvatarView = (ImageView) mDrawer.findViewById(R.id.drawer_user_avatar);
        mDrawerUserAvatarView.setOnClickListener(v ->
                new ThemeChangeDialog().show(getSupportFragmentManager(), ThemeChangeDialog.TAG));
        mDrawerUsernameView = (TextView) mDrawer.findViewById(R.id.drawer_username);

        // Show default avatar and login prompt if user hasn't logged in,
        // else show user's avatar and username.
        if (MyAccount.hasLoggedIn()) {
            setupDrawerUserView();
        } else {
            setupDrawerLoginPrompt();
        }

        // add settings item
        TextView settingsView = (TextView) mDrawer.findViewById(R.id.settings);
        settingsView.setText(getText(R.string.settings));

        // set up settings icon
        settingsView.setCompoundDrawablePadding(
                getResources().getDimensionPixelSize(R.dimen.left_icon_margin_right));
        settingsView.setCompoundDrawablesWithIntrinsicBounds(
                ResourceUtil.getResourceId(getTheme(), R.attr.iconSettings), 0, 0, 0);

        // start SettingsActivity if clicked
        settingsView.setOnClickListener(v ->
                closeDrawer(() -> {
                    Intent intent = new Intent(BaseActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }));
    }

    /**
     * Show default avatar and login prompt if user hasn't logged in.
     */
    private void setupDrawerLoginPrompt() {
        if (mDrawerLayout == null || mDrawer == null
                || mDrawerTopBackgroundView == null
                || mDrawerUserAvatarView == null || mDrawerUsernameView == null) {
            return;
        }

        // setup default avatar
        Glide.with(this)
                .load(R.drawable.ic_drawer_avatar_placeholder)
                .signature(Config.getAvatarCacheInvalidationIntervalSignature())
                .transform(new CenterCrop(Glide.get(this).getBitmapPool()))
                .into(mDrawerUserAvatarView);

        // start LoginActivity if clicked
        mDrawerUsernameView.setText(R.string.action_login);

        mDrawerTopBackgroundView.setOnClickListener(v -> {
            mDrawerLayout.closeDrawer(mDrawer);

            Intent intent = new Intent(BaseActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Show user's avatar and username if user has logged in.
     */
    void setupDrawerUserView() {
        if (mDrawerTopBackgroundView == null
                || mDrawerUserAvatarView == null || mDrawerUsernameView == null) {
            return;
        }

        // setup user's avatar
        Glide.with(this)
                .load(Api.getAvatarMediumUrl(MyAccount.getUid()))
                .signature(Config.getAvatarCacheInvalidationIntervalSignature())
                .error(R.drawable.ic_drawer_avatar_placeholder)
                .transform(new CenterCrop(Glide.get(this).getBitmapPool()))
                .into(mDrawerUserAvatarView);

        // show logout dialog if clicked
        mDrawerUsernameView.setText(MyAccount.getName());

        mDrawerTopBackgroundView.setOnClickListener(v ->
                new LogoutDialog().show(getSupportFragmentManager(), LogoutDialog.TAG));
    }

    Toolbar getToolbar() {
        return mToolbar;
    }

    public Rect getSystemWindowInsets() {
        if (mSystemWindowInsets == null) {
            mSystemWindowInsets = new Rect();
        }

        return mSystemWindowInsets;
    }

    void setNavDrawerEnabled(boolean enabled) {
        mHasNavDrawer = enabled;
    }

    void setNavDrawerIndicatorEnabled(boolean enabled) {
        mHasNavDrawerIndicator = enabled;
    }

    public static class ThemeChangeDialog extends DialogFragment {

        private static final String TAG = "theme_change_dialog";

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());

            int checkedItem =
                    Integer.parseInt(
                            sharedPreferences.getString(
                                    SettingsFragment.PREF_KEY_THEME,
                                    MyApplication.getContext()
                                            .getString(R.string.pref_theme_default_value)));
            return
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.pref_theme)
                            .setSingleChoiceItems(
                                    R.array.pref_theme_entries,
                                    checkedItem,
                                    (dialog, which) -> {
                                        Runnable runnable = null;
                                        // won't change theme if unchanged
                                        if (which != checkedItem) {
                                            sharedPreferences.edit()
                                                    .putString(
                                                            SettingsFragment.PREF_KEY_THEME,
                                                            String.valueOf(which)).apply();
                                            Config.setCurrentTheme(sharedPreferences);

                                            // We use MyApplication.getContext() instead of getActivity()
                                            // in order to avoid NullPointerException when out of scope.
                                            runnable = () -> MyApplication.getContext()
                                                    .sendBroadcast(
                                                            new Intent(SettingsFragment.ACTION_CHANGE_THEME));
                                        }
                                        dismiss();
                                        ObjectUtil.cast(
                                                getActivity(),
                                                BaseActivity.class).closeDrawer(runnable);
                                    })
                            .create();
        }
    }

    public static class LogoutDialog extends DialogFragment {

        private static final String TAG = "log_out_dialog";

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.dialog_message_log_out)
                            .setPositiveButton(
                                    android.R.string.ok,
                                    (dialog, which) ->
                                            ObjectUtil.cast(
                                                    getActivity(),
                                                    MyAccount.OnLogoutListener.class).onLogout())
                            .setNegativeButton(android.R.string.cancel, null)
                            .create();
        }
    }

    @Override
    public void onLogout() {
        // clear account cookie and current user's info
        MyOkHttpClient.clearCookie();
        MyAccount.clear();
        MyAccount.sendCookieExpirationBroadcast();
    }
}
