package com.cliqz.browser.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.support.design.widget.AppBarLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.util.Patterns;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.cliqz.browser.R;
import com.cliqz.browser.app.BrowserApp;
import com.cliqz.browser.controlcenter.ControlCenterDialog;
import com.cliqz.browser.main.CliqzBrowserState.Mode;
import com.cliqz.browser.main.Messages.ControlCenterStatus;
import com.cliqz.browser.main.search.SearchView;
import com.cliqz.browser.telemetry.TelemetryKeys;
import com.cliqz.browser.utils.ConfirmSubscriptionDialog;
import com.cliqz.browser.utils.EnableNotificationDialog;
import com.cliqz.browser.utils.RelativelySafeUniqueId;
import com.cliqz.browser.utils.SubscriptionsManager;
import com.cliqz.browser.webview.BrowserActionTypes;
import com.cliqz.browser.webview.CliqzMessages;
import com.cliqz.browser.webview.ExtensionEvents;
import com.cliqz.browser.widget.OverFlowMenu;
import com.cliqz.browser.widget.SearchBar;
import com.cliqz.nove.Subscribe;
import com.cliqz.utils.ActivityUtils;
import com.cliqz.utils.FragmentUtilsV4;
import com.cliqz.utils.NoInstanceException;
import com.cliqz.utils.StreamUtils;
import com.cliqz.utils.ViewUtils;
import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.inject.Inject;

import acr.browser.lightning.bus.BrowserEvents;
import acr.browser.lightning.constant.Constants;
import acr.browser.lightning.utils.UrlUtils;
import acr.browser.lightning.utils.Utils;
import acr.browser.lightning.view.AnimatedProgressBar;
import acr.browser.lightning.view.LightningView;
import acr.browser.lightning.view.TrampolineConstants;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnEditorAction;

/**
 * @author Stefano Pacifici
 * @author Moaz Mohamed
 */
public class TabFragment extends BaseFragment implements LightningView.LightingViewListener {

    @SuppressWarnings("unused")
    private static final String TAG = TabFragment.class.getSimpleName();

    public static final String KEY_URL = "cliqz_url_key";
    public static final String KEY_QUERY = "query";
    public static final String KEY_NEW_TAB_MESSAGE = "new_tab_message";
    public static final String KEY_TAB_ID = "tab_id";
    public static final String KEY_TITLE = "tab_title";
    public static final String KEY_FORCE_RESTORE = "tab_force_restore";

    private static final String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;
    private OverFlowMenu mOverFlowMenu = null;
    protected boolean mIsIncognito = false;
    private String mInitialUrl = null; // Initial url coming from outside the browser
    // Coming from history (or favorite) this is needed due to the url load delay introduced
    // for mitigating the multi process WebView on Android 8
    private String mOverviewUrl = null;
    // indicate that we should not load the mInitialUrl because we are restoring a persisted tab
    private boolean mShouldRestore = false;
    private String mSearchEngine;
    private Message newTabMessage = null;
    private String mExternalQuery = null;
    // TODO: @Ravjit this should not be public (avoid public members)
    public final CliqzBrowserState state = new CliqzBrowserState();
    protected boolean isHomePageShown = false;
    private JSONArray videoUrls = null;
    private int mTrackerCount = 0;
    String lastQuery = "";
    private String mId;

    protected LightningView mLightningView = null;
    private String mDelayedUrl = null;

    // A flag used to handle back button on old phones
    protected boolean mShowWebPageAgain = false;
    private boolean mRequestDesktopSite = false;
    private boolean mIsReaderModeOn = false;

    @Bind(R.id.local_container)
    FrameLayout localContainer;

    @Inject
    SearchView searchView;

    @Inject
    SubscriptionsManager subscriptionsManager;

    @Bind(R.id.progress_view)
    AnimatedProgressBar progressBar;

    @Bind(R.id.search_bar)
    SearchBar searchBar;

    @Bind(R.id.overflow_menu)
    View overflowMenuButton;

    @Bind(R.id.overflow_menu_icon)
    ImageView overflowMenuIcon;

    @Bind(R.id.in_page_search_bar)
    View inPageSearchBar;

    @Bind(R.id.yt_download_icon)
    ImageButton ytDownloadIcon;

    @Nullable
    @Bind(R.id.control_center)
    RelativeLayout antiTrackingDetails;

    @Bind(R.id.open_tabs_count)
    AppCompatTextView openTabsCounter;

    @Bind(R.id.toolbar_container)
    RelativeLayout toolBarContainer;

    @Bind(R.id.quick_access_bar)
    QuickAccessBar quickAccessBar;

    @Bind(R.id.reader_mode_button)
    ImageButton readerModeButton;

    @Bind(R.id.reader_mode_webview)
    WebView readerModeWebview;

    @Inject
    OnBoardingHelper onBoardingHelper;

    @Inject
    QueryManager queryManager;

    @Nullable
    @Bind(R.id.cc_icon)
    AppCompatImageView ccIcon;

    @NonNull
    public final String getTabId() {
        return mId;
    }

    public TabFragment() {
        super();
        // This must be not null
        mId = RelativelySafeUniqueId.createNewUniqueId();
    }

    @Override
    public void setArguments(Bundle args) {
        newTabMessage = args.getParcelable(KEY_NEW_TAB_MESSAGE);
        // Remove asap the message from the bundle
        args.remove(KEY_NEW_TAB_MESSAGE);
        mId = args.getString(KEY_TAB_ID);

        @SuppressWarnings("ConstantConditions")
        final String url = args.getString(KEY_URL);
        final String title = args.getString(KEY_TITLE);
        @SuppressWarnings("ConstantConditions")
        final boolean incognito = args.getBoolean(MainActivity.EXTRA_IS_PRIVATE, false);
        if (url != null && !url.isEmpty()) {
            state.setUrl(url);
            state.setTitle(title != null ? title : url);
            state.setMode(CliqzBrowserState.Mode.WEBPAGE);
            state.setIncognito(incognito);
        }
        super.setArguments(args);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle arguments = getArguments();
        if (arguments != null) {
            parseArguments(arguments);
        }
    }

    private void parseArguments(Bundle arguments) {
        mIsIncognito = arguments.getBoolean(MainActivity.EXTRA_IS_PRIVATE, false);
        mInitialUrl = arguments.getString(KEY_URL);
        mShouldRestore = arguments.getBoolean(KEY_FORCE_RESTORE);
        final String mInitialTitle = arguments.getString(KEY_TITLE);
        mExternalQuery = arguments.getString(KEY_QUERY);
        if (mInitialUrl != null) {
            state.setUrl(mInitialUrl);
        }
        if (mInitialTitle != null) {
            state.setTitle(mInitialTitle);
        }
        // We need to remove the key, otherwise the url/query/msg gets reloaded for each resume
        arguments.remove(KEY_URL);
        arguments.remove(KEY_NEW_TAB_MESSAGE);
        arguments.remove(KEY_QUERY);
        arguments.remove(KEY_TAB_ID);
        arguments.remove(KEY_FORCE_RESTORE);
    }

    // Use this to get which view is visible between home, cards or web
    @NonNull
    String getTelemetryView() {
        if (state.getMode() == Mode.WEBPAGE) {
            return TelemetryKeys.WEB;
        } else if (searchView.isFreshTabVisible()) {
            return TelemetryKeys.HOME;
        } else {
            return TelemetryKeys.CARDS;
        }
    }

    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);
        searchBar.setSearchEditText(searchEditText);
        searchBar.setProgressBar(progressBar);
        final MainActivity mainActivity = (MainActivity) getActivity();
        final int tabCount = mainActivity != null ? mainActivity.tabsManager.getTabCount() : 1;
        openTabsCounter.setText(Integer.toString(tabCount));
        updateUI();
        inPageSearchBar.setVisibility(View.GONE);
        state.setIncognito(mIsIncognito);
        searchBar.setStyle(mIsIncognito);
        //openTabsCounter.setBa
        if (searchView == null || mLightningView == null) {
            // Must use activity due to Crosswalk webview
            searchView = ((MainActivity) getActivity()).searchView;
            final String id = mId != null ? mId : RelativelySafeUniqueId.createNewUniqueId();
            mLightningView = new LightningView(getActivity(), mIsIncognito, id);
            mLightningView.setListener(this);
        }

        TabFragmentListener.create(this);
        final WebView webView = mLightningView.getWebView();
        if (webView != null) {
            webView.setId(R.id.browser_view);
            ViewUtils.safelyAddView(localContainer, webView);
            if (webView.getVisibility() == View.VISIBLE &&
                    UrlUtils.isYoutubeVideo(mLightningView.getUrl())) {
                fetchYoutubeVideo(null);
            }
        }
        searchView.setCurrentTabState(state);
        ViewUtils.safelyAddView(localContainer, searchView);
        searchBar.setTrackerCount(mTrackerCount);
        quickAccessBar.setSearchTextView(searchEditText);
        quickAccessBar.hide();
        //way to handle links in the readermode article
        readerModeWebview.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                toggleReaderMode();
                mLightningView.loadUrl(url);
                return true;
            }
        });
        onPageFinished(null);
    }

    @Override
    public void onStart() {
        super.onStart();
        //noinspection ConstantConditions
        final MainActivityComponent component = BrowserApp.getActivityComponent(getActivity());
        if (component != null) {
            component.inject(this);
            component.inject(quickAccessBar);
        }
        if (mDelayedUrl != null) {
            openLink(mDelayedUrl, true, true);
            mDelayedUrl = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (searchView != null) {
            searchView.onResume();
            searchView.setVisibility(View.VISIBLE);
        }
        final WebView webView;
        if (mLightningView != null) {
            mLightningView.onResume();
            mLightningView.resumeTimers();
            webView = mLightningView.getWebView();
        } else {
            webView = null;
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }

        searchBar.setIsAutocompletionEnabled(preferenceManager.getAutocompletionEnabled());

        if (state.getMode() == Mode.WEBPAGE) {
            bringWebViewToFront();
        } else {
            searchView.bringToFront();
            //update topsites and news whenever app resumes or comes back from settings
            searchView.freshtab.updateFreshTab();
        }

        // Workaround for Oreo multi-process WebView, we can not anymore call openLink(...)
        // directly from the OverviewFragment.
        final String oreoUrl;
        final boolean oreoShouldReset;
        if (mOverviewUrl != null) {
            oreoUrl = mOverviewUrl;
            oreoShouldReset = false;
        } else if (mInitialUrl != null)  {
            oreoUrl = mInitialUrl;
            oreoShouldReset = true;
        } else {
            oreoUrl = null;
            oreoShouldReset = false;
        }
        mOverviewUrl = null;
        mInitialUrl = null;
        // The code below shouldn't be executed if app is reset
        if (mShouldRestore) {
            state.setMode(Mode.WEBPAGE);
            bringWebViewToFront();
            mShouldRestore = false;
        } else if (oreoUrl != null && !oreoUrl.isEmpty()) {
            state.setMode(Mode.WEBPAGE);
            bus.post(new CliqzMessages.OpenLink(oreoUrl, oreoShouldReset));
        } else if (newTabMessage != null && webView != null && newTabMessage.obj != null) {
            final WebView.WebViewTransport transport = (WebView.WebViewTransport) newTabMessage.obj;
            transport.setWebView(webView);
            newTabMessage.sendToTarget();
            newTabMessage = null;
            bringWebViewToFront();
        } else if (mExternalQuery != null && !mExternalQuery.isEmpty()) {
            state.setMode(Mode.SEARCH);
            bus.post(new Messages.ShowSearch(mExternalQuery));
        } else {
            // final boolean reset = System.currentTimeMillis() - state.getTimestamp() >= Constants.HOME_RESET_DELAY;
            final String lightningUrl = mLightningView.getUrl();
            final String query = state.getQuery();
            final boolean mustShowSearch = lightningUrl.isEmpty() && query != null && !query.isEmpty();
            if (mustShowSearch) {
                state.setMode(Mode.SEARCH);
            }
            if (state.getMode() == Mode.SEARCH) {
                //showToolBar(null);
                delayedPostOnBus(new Messages.ShowSearch(query));
            } else {
                mLightningView.getWebView().bringToFront();
                enableUrlBarScrolling();
                searchBar.showTitleBar();
                searchBar.showProgressBar();
                searchBar.setAntiTrackingDetailsVisibility(View.VISIBLE);
                searchBar.setTitle(mLightningView.getTitle());
                searchBar.switchIcon(false);
            }
        }
        if(!preferenceManager.isAttrackEnabled() || mLightningView.isUrlWhiteListed()){
            updateControlIcon(new Messages.UpdateControlCenterIcon(ControlCenterStatus.DISABLED));
        }else if(preferenceManager.isAttrackEnabled() && !mLightningView.isUrlWhiteListed()){
            updateControlIcon(new Messages.UpdateControlCenterIcon(ControlCenterStatus.ENABLED));
        }
        queryManager.setForgetMode(mIsIncognito);
        mIsReaderModeOn = false;
        readerModeButton.setImageResource(R.drawable.ic_reader_mode_off);
    }

    @Override
    protected int getMenuResource() {
        return R.menu.fragment_search_menu;
    }

    @Override
    protected boolean onMenuItemClick(MenuItem item) {
        return false;
    }

    @Override
    protected int getFragmentTheme() {
        if (mIsIncognito) {
            return R.style.Theme_LightTheme_Incognito;
        } else {
            return R.style.Theme_Cliqz_Overview;
        }
    }

    @Nullable
    @Override
    protected View onCreateCustomToolbarView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_toolbar, container, false);
    }

    @OnClick(R.id.menu_overview)
    void historyClicked() {
        hideKeyboard(null);
        telemetry.sendOverViewSignal(Integer.parseInt(openTabsCounter.getText().toString()),
                mIsIncognito, getTelemetryView());
        delayedPostOnBus(new Messages.GoToOverview());
    }

    @OnClick(R.id.overflow_menu)
    void menuClicked() {
        telemetry.sendOverflowMenuSignal(mIsIncognito, getTelemetryView());
        if (mOverFlowMenu != null && mOverFlowMenu.isShown()) {
            mOverFlowMenu.dismiss();
        } else {
            final String url = mLightningView.getUrl();
            mOverFlowMenu = new OverFlowMenu(getActivity());
            mOverFlowMenu.setCanGoForward(mLightningView.canGoForward());
            mOverFlowMenu.setAnchorView(overflowMenuButton);
            mOverFlowMenu.setIncognitoMode(mIsIncognito);
            mOverFlowMenu.setUrl(url);
            mOverFlowMenu.setFavorite(historyDatabase.isFavorite(url));
            mOverFlowMenu.setTitle(mLightningView.getTitle());
            mOverFlowMenu.setState(state);
            mOverFlowMenu.setIsFreshTabVisible(searchView.isFreshTabVisible());
            mOverFlowMenu.setDesktopSiteEnabled(mRequestDesktopSite);
            mOverFlowMenu.show();
            hideKeyboard(null);
        }
    }

    @Subscribe
    void onOpenMenuMessage(Messages.OnOpenMenuButton event) {
        menuClicked();
    }

    @OnClick(R.id.in_page_search_cancel_button)
    void closeInPageSearchClosed() {
        inPageSearchBar.setVisibility(View.GONE);
        mLightningView.findInPage("");
    }

    @OnClick(R.id.in_page_search_up_button)
    void previousResultInPageSearchClicked() {
        mLightningView.findPrevious();
    }

    @OnClick(R.id.in_page_search_down_button)
    void nextResultInPageSearchClicked() {
        mLightningView.findNext();
    }

    // TODO @Ravjit, the dialog should disappear if you pause the app
    @OnClick(R.id.control_center)
    void showControlCenter() {
        final WebView webView = mLightningView.getWebView();
        if (webView == null) {
            return;
        }
        final ControlCenterDialog controlCenterDialog = ControlCenterDialog
                .create(mStatusBar, mIsIncognito, webView.hashCode(), mLightningView.getUrl());
        controlCenterDialog.show(getChildFragmentManager(), Constants.CONTROL_CENTER);
        telemetry.sendControlCenterOpenSignal(mIsIncognito, mTrackerCount);
    }

    @OnClick(R.id.yt_download_icon)
    void showYTDownloadDialog() {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            telemetry.sendYTIconClickedSignal(mIsIncognito);
            final ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo activeNetwork = connectivityManager != null ?
                    connectivityManager.getActiveNetworkInfo() : null;
            if (activeNetwork != null && activeNetwork.getType() != ConnectivityManager.TYPE_WIFI &&
                    preferenceManager.shouldLimitDataUsage()) {
                NoWiFiDialog.show(getContext(), bus);
                return;
            }
            if (videoUrls != null) {
                YTDownloadDialog.show((MainActivity) getActivity(), videoUrls, telemetry);
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
    }

    @OnClick(R.id.reader_mode_button)
    void toggleReaderMode() {
        if (!mIsReaderModeOn) {
            mIsReaderModeOn = true;
            readerModeButton.setImageResource(R.drawable.ic_reader_mode_on);
            readerModeWebview.setVisibility(View.VISIBLE);
            readerModeWebview.bringToFront();
            readerModeWebview.scrollTo(0,0);
        } else {
            mIsReaderModeOn = false;
            readerModeButton.setImageResource(R.drawable.ic_reader_mode_off);
            readerModeWebview.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("UnusedParameters")
    @OnEditorAction(R.id.search_edit_text)
    boolean onEditorAction(EditText editText, int actionId, KeyEvent keyEvent) {
        // Navigate to autocomplete url or search otherwise
        if (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            final String content = searchBar.getSearchText();
            if (content != null && !content.isEmpty()) {
                final Object event;
                if (Patterns.WEB_URL.matcher(content).matches()) {
                    final String guessedUrl = URLUtil.guessUrl(content);
                    if (searchBar.isAutoCompleted()) {
                        telemetry.sendResultEnterSignal(false, true,
                                searchBar.getQuery().length(), guessedUrl.length());
                    } else {
                        telemetry.sendResultEnterSignal(false, false, content.length(), -1);
                    }
                    event = new CliqzMessages.OpenLink(guessedUrl);
                } else {
                    telemetry.sendResultEnterSignal(true, false, content.length(), -1);
                    setSearchEngine();
                    String searchUrl = mSearchEngine + UrlUtils.QUERY_PLACE_HOLDER;
                    event = new CliqzMessages.OpenLink(UrlUtils.smartUrlFilter(content, true, searchUrl));
                }
                if (!onBoardingHelper.conditionallyShowSearchDescription()) {
                    bus.post(event);
                } else {
                    hideKeyboard(null);
                    searchView.notifySearchWebViewEvent(ExtensionEvents.CLIQZ_EVENT_PERFORM_SHOWCASE_CARD_SWIPE);
                }
                return true;
            }
        }
        return false;
    }

    // Hide the keyboard, used also in SearchFragmentListener
    @Subscribe
    void hideKeyboard(CliqzMessages.HideKeyboard event) {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            InputMethodManager imm = (InputMethodManager) context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) {
                return;
            }
            imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
            final View set = searchBar.getSearchEditText();
            // This if avoids calling searchBar.showTitleBar() multiple times and sending the same
            // telemetry signals multiple times
            if (set != null && imm.isActive(set)) {
                searchBar.showTitleBar();
                final String view = getTelemetryView();
                telemetry.sendKeyboardSignal(false, mIsIncognito, getTelemetryView());
                telemetry.sendQuickAccessBarSignal(TelemetryKeys.HIDE, null, view);
                quickAccessBar.hide();
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
    }

    private void shareText(String text) {
        final String footer = getString(R.string.shared_using);
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text + "\n" + footer);
        startActivity(Intent.createChooser(intent, getString(R.string.share_link)));
    }

    @Subscribe
    public void updateProgress(BrowserEvents.UpdateProgress event) {
        searchBar.setProgress(event.progress);
        if (state.getMode() == Mode.SEARCH) {
            return;
        }
        if (!mLightningView.getUrl().contains(TrampolineConstants.CLIQZ_TRAMPOLINE_GOTO)) {
            if (event.progress == 100) {
                searchBar.switchIcon(false);
                // Re-adjust the layout in cases when height of content is not more than the
                // visible content height + toolbar height
                final AppBarLayout.LayoutParams params =
                        (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
                final WebView webView = mLightningView.getWebView();
                if (webView != null) {
                    if (webView.getContentHeight() <= webView.getHeight()) {
                        disableUrlBarScrolling();
                    } else if (params.getScrollFlags() == 0) {
                        enableUrlBarScrolling();
                    }
                }
                searchBar.switchIcon(true);
            }
        }
    }

    @Subscribe
    public void updateTitle(Messages.UpdateTitle event) {
        updateTitle();
    }

    @Subscribe
    public void updateUrl(BrowserEvents.UpdateUrl event) {
        final String url = event.url;
        if (url != null && !url.isEmpty() && !url.startsWith(TrampolineConstants.CLIQZ_TRAMPOLINE_GOTO)) {
            state.setUrl(url);
        }
    }

    private void bringWebViewToFront() {
        final WebView webView = mLightningView.getWebView();
        if (webView == null) {
            return;
        }
        searchBar.showTitleBar();
        searchBar.showProgressBar();
        searchBar.setTitle(webView.getTitle());
        searchBar.setAntiTrackingDetailsVisibility(View.VISIBLE);
        webView.bringToFront();
        enableUrlBarScrolling();
        state.setMode(Mode.WEBPAGE);
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            toolBarContainer.setBackgroundColor(ContextCompat.getColor(context,
                    mIsIncognito ? R.color.incognito_tab_primary_color : R.color.primary_color));
            overflowMenuIcon.setColorFilter(ContextCompat.getColor(context, R.color.white),
                    PorterDuff.Mode.SRC_IN);
            openTabsCounter
                    .getBackground()
                    .setColorFilter(ContextCompat.getColor(context, R.color.white),
                            PorterDuff.Mode.SRC_IN);
            openTabsCounter.setTextColor(ContextCompat.getColor(context, R.color.white));
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
    }

    @Subscribe
    public void openLink(CliqzMessages.OpenLink event) {
        queryManager.addLatestQueryToDatabase();
        openLink(event.url, event.reset, false);
        mShowWebPageAgain = false;
    }

    @Subscribe
    public void addToFavourites(Messages.AddToFavourites event) {
        historyDatabase.setFavorites(event.url, event.title, System.currentTimeMillis(), true);
    }

    @Subscribe
    public void searchOnPage(BrowserEvents.SearchInPage event) {
        SearchInPageDialog.show(getContext(), inPageSearchBar, mLightningView);
    }

    @Subscribe
    public void askLocationPermission(Messages.AskForLocationPermission event) {
        //first check if android 6 has necessary permission
        try {
            final Activity activity = FragmentUtilsV4.getActivity(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    activity.checkSelfPermission(LOCATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                final String[] permissions = {LOCATION_PERMISSION};
                activity.requestPermissions(permissions, Constants.LOCATION_PERMISSION);
            } else {
                showRequestLocationAccessDialog();
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null activity", e);
        }
    }

    @Subscribe
    public void onPageFinished(CliqzMessages.OnPageFinished event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                final InputStream inputStream = getResources().openRawResource(R.raw.readability);
                final String script = StreamUtils.readTextStream(inputStream);
                inputStream.close();
                final WebView webView = mLightningView.getWebView();
                if (webView != null) {
                    webView.evaluateJavascript(script, readabilityCallBack);
                } else {
                    Log.w(TAG, "Null WebView found in onPageFinished");
                }
            } catch (IOException e) {
                Log.e(TAG, "Problem reading the file readability.js", e);
            }
        }
    }

    private ValueCallback<String> readabilityCallBack = new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String s) {
            if (s == null) {
                return;
            }
            try {
                final String decodedResponse = URLDecoder.decode(s, "UTF-8");
                //removing extra quotation marks at the start
                final String jsonString = decodedResponse.substring(1,decodedResponse.length()-1);
                final JSONObject jsonObject = new JSONObject(jsonString);
                final String readerModeText = jsonObject.optString("content", "");
                final String readerModeTitle = jsonObject.optString("title", "");
                if (readerModeText != null && !readerModeText.isEmpty()) {
                    readerModeButton.setVisibility(View.VISIBLE);
                    readerModeWebview.loadDataWithBaseURL("",
                            getFormattedHtml(readerModeTitle, readerModeText),
                            "text/html", "UTF-8", "");
                }
            } catch (JSONException e) {
                Log.i(TAG,"error reading the json object", e);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG,"error decoding the response from readability.js", e);
            }
        }
    };

    //This function adds the title to the page content and resizes the images to fit the screen
    private String getFormattedHtml(String title, String bodyHTML) {
        final String head = "<head><style>img{max-width: 100%; height: auto;}</style></head>";
        final String titleHTML = "<h2>" + title + "</h2>";
        return "<html>" + head + "<body>" +
                titleHTML + bodyHTML + "</body></html>";
    }

    public void openLink(String eventUrl, boolean reset, boolean fromHistory) {

        //if Request Desktop site is enabled, remove "m." if it is a mobile url
        if (mRequestDesktopSite && (eventUrl.startsWith("m.") || eventUrl.contains("/m."))) {
            eventUrl = eventUrl.replaceFirst("m.", "");
        }

        if (mLightningView == null) {
            mDelayedUrl = eventUrl;
            return;
        }
        if (telemetry != null) {
            telemetry.resetNavigationVariables(eventUrl.length());
        }

        new Uri.Builder();
        final Uri.Builder builder = new Uri.Builder();
        builder.scheme(TrampolineConstants.CLIQZ_SCHEME)
                .authority(TrampolineConstants.CLIQZ_TRAMPOLINE_AUTHORITY)
                .path(TrampolineConstants.CLIQZ_TRAMPOLINE_GOTO_PATH)
                .appendQueryParameter("url", eventUrl)
                .appendQueryParameter("q", lastQuery);
        if (reset) {
            builder.appendQueryParameter("r", "true");
        }
        if (fromHistory) {
            builder.appendQueryParameter("h", "true");
        }
        final String url = builder.build().toString();
        mLightningView.loadUrl(url);
        searchBar.setTitle(eventUrl);
        bringWebViewToFront();
        quickAccessBar.hide();
        telemetry.sendQuickAccessBarSignal(TelemetryKeys.HIDE, null, getTelemetryView());
    }

    /**
     * Show the search interface in the current tab for the given query
     *
     * @param query the query to display (and search)
     */
    public void searchQuery(String query) {
        searchBar.showSearchEditText();
        searchView.bringToFront();
        disableUrlBarScrolling();
        inPageSearchBar.setVisibility(View.GONE);
        mLightningView.findInPage("");
        searchBar.requestSearchFocus();
        if (query != null) {
            searchBar.setSearchText(query);
            searchBar.setCursorPosition(query.length());
        }
        state.setMode(Mode.SEARCH);
        searchBar.setAntiTrackingDetailsVisibility(View.GONE);
        searchBar.showKeyBoard();
    }

    @SuppressLint("ObsoleteSdkInt")
    @Subscribe
    public void onBackPressed(Messages.BackPressed event) {
        // Due to webview bug (history manipulation doesn't work) we have to handle the back in a
        // different way.
        showToolbar();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            onBackPressedV16();
        } else {
            onBackPressedV21();
        }
    }

    private void onBackPressedV16() {
        final Mode mode = state.getMode();
        if (!onBoardingHelper.close() && !hideOverFlowMenu()) {
            if (readerModeWebview.getVisibility() == View.VISIBLE) {
                toggleReaderMode();
            } else if (mode == Mode.WEBPAGE && mLightningView.canGoBack()) {
                telemetry.backPressed = true;
                telemetry.showingCards = false;
                mLightningView.goBack();
                mShowWebPageAgain = false;
            } else if (mode == Mode.SEARCH && mShowWebPageAgain) {
                bringWebViewToFront();
            } else {
                bus.post(new BrowserEvents.CloseTab());
            }
        }
    }

    private void onBackPressedV21() {
        final String url = mLightningView != null ? mLightningView.getUrl() : "";
        final Mode mode = state.getMode();
        if (!onBoardingHelper.close() && !hideOverFlowMenu()) {
            if (mIsReaderModeOn) {
                toggleReaderMode();
            } else if (mode == Mode.SEARCH &&
                    !"".equals(url) &&
                    !TrampolineConstants.CLIQZ_TRAMPOLINE_GOTO.equals(url)) {
                bringWebViewToFront();
                if (UrlUtils.isYoutubeVideo(mLightningView.getUrl())) {
                    fetchYoutubeVideo(null);
                }
            } else if (mLightningView.canGoBack()) {
                // In any case the trampoline will be current page predecessor
                if (mode == Mode.SEARCH) {
                    bringWebViewToFront();
                }
                telemetry.backPressed = true;
                telemetry.showingCards = mode == Mode.SEARCH;
                mLightningView.goBack();
            } else {
                bus.post(new BrowserEvents.CloseTab());
            }
        }
    }

    // Hide the OverFlowMenu if it is visible. Return true if it was, false otherwise.
    private boolean hideOverFlowMenu() {
        if (mOverFlowMenu != null && mOverFlowMenu.isShown()) {
            mOverFlowMenu.dismiss();
            mOverFlowMenu = null;
            return true;
        }
        return false;
    }

    @Subscribe
    public void onGoForward(Messages.GoForward event) {
        if (mLightningView.canGoForward()) {
            mLightningView.goForward();
            if (state.getMode() == Mode.SEARCH) {
                bringWebViewToFront();
            }
        }
    }

    @Subscribe
    public void showSearch(Messages.ShowSearch event) {
        searchQuery(event.query);
    }

    @Subscribe
    public void autocomplete(CliqzMessages.Autocomplete event) {
        searchBar.setAutocompleteText(event.completion);
    }

    @Subscribe
    public void reloadPage(Messages.ReloadPage event) {
        final WebView webView = mLightningView.getWebView();
        if (webView != null) {
            webView.reload();
        }
    }

    @Subscribe
    public void shareLink(Messages.ShareLink event) {
        if (state.getMode() == Mode.SEARCH) {
            searchView.requestCardUrl();
        } else {
            final String url = mLightningView.getUrl();
            shareText(url);
            telemetry.sendShareSignal(TelemetryKeys.WEB);
        }
    }

    @Subscribe
    public void shareCard(Messages.ShareCard event) {
        new ShareCardHelper(getActivity(), localContainer, event.cardDetails);
        telemetry.sendShareSignal(TelemetryKeys.CARDS);

    }

    @Subscribe
    public void copyUrl(Messages.CopyUrl event) {
        if (mLightningView.getWebView() != null) {
            putInClipboard(R.string.message_url_copied, mLightningView.getUrl(), "link");
        }
    }

    @Subscribe
    public void saveLink(Messages.SaveLink event) {
        final WebView webView = mLightningView.getWebView();
        final WebSettings settings = webView != null ? webView.getSettings() : null;
        final String userAgent = settings != null ? settings.getUserAgentString() : null;
        Utils.downloadFile(getActivity(), mLightningView.getUrl(), userAgent, "attachment", false);
    }

    @Subscribe
    public void copyData(CliqzMessages.CopyData event) {
        putInClipboard(R.string.message_text_copied, event.data, "result");
    }

    private void putInClipboard(@SuppressWarnings("unused") @StringRes int message,
                                @NonNull String data, @NonNull String label) {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            final ClipboardManager clipboard = (ClipboardManager) context
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                final ClipData clip = ClipData.newPlainText(label, data);
                clipboard.setPrimaryClip(clip);
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
    }

    @Subscribe
    public void callNumber(CliqzMessages.CallNumber event) {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            final BrowserActionTypes action = BrowserActionTypes.phoneNumber;
            Intent callIntent = action.getIntent(context, event.number);
            if (callIntent != null) {
                context.startActivity(callIntent);
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
    }

    @Subscribe
    public void setYoutubeUrls(Messages.SetVideoUrls event) {
        videoUrls = event.urls;
        if (videoUrls == null) {
            hideYTIcon();
            return;
        }
        if (videoUrls.length() == 0) {
            telemetry.sendVideoPageSignal(false);
            hideYTIcon();
        } else {
            telemetry.sendVideoPageSignal(true);
            showYTIcon();
            onBoardingHelper.conditionallyShowYouTubeDescription();
        }
    }

    @Subscribe
    public void fetchYoutubeVideo(Messages.FetchYoutubeVideoUrls event) {
        videoUrls = null;
        // To fetch the videos url we have to run the ytdownloader.getUrls script that is bundled
        // with the extension
        searchView.fetchYouTubeUrls(mLightningView.getUrl());
    }

    @Subscribe
    public void downloadYoutubeVideo(Messages.DownloadYoutubeVideo event) {
        if (videoUrls != null) {
            YTDownloadDialog.show((MainActivity) getActivity(), videoUrls, telemetry);
            preferenceManager.setShouldShowYouTubeDescription(false);
        }
    }

    @Override
    public void increaseAntiTrackingCounter() {
        mTrackerCount++;
        // Quick fix for Ad-Block loading problems (attrack.isWhitelist(...) timeout)
        if (mTrackerCount == 1) {
            bus.post(new Messages.UpdateControlCenterIcon(ControlCenterStatus.ENABLED));
        }
        searchBar.setTrackerCount(mTrackerCount);
        if (mTrackerCount > 0 && onBoardingHelper.conditionallyShowAntiTrackingDescription()) {
            hideKeyboard(null);
        }
        bus.post(new Messages.UpdateAntiTrackingList(mTrackerCount));
        bus.post(new Messages.UpdateAdBlockingList());
    }

    @Override
    public void onFavIconLoaded(Bitmap favicon) {
        state.setFavIcon(favicon);
    }

    //Hack to update the counter in the url bar to match with that in the CC when user opens CC
    @Subscribe
    public void updateTrackerCountHack(Messages.ForceUpdateTrackerCount event) {
        mTrackerCount = event.trackerCount;
        searchBar.setTrackerCount(event.trackerCount);
    }

    @Subscribe
    public void resetTrackerCount(Messages.ResetTrackerCount event) {
        mTrackerCount = 0;
        searchBar.setTrackerCount(mTrackerCount);
        readerModeWebview.setVisibility(View.GONE);
        readerModeButton.setVisibility(View.GONE);
    }

    @SuppressLint("SetTextI18n")
    @Subscribe
    public void updateTabCounter(Messages.UpdateTabCounter event) {
        openTabsCounter.setText(Integer.toString(event.count));
    }

    @Subscribe
    public void updateUserAgent(Messages.ChangeUserAgent event) {
        if (mLightningView != null) {
            if (event.isDesktopSiteEnabled) {
                mLightningView.setDesktopUserAgent();
            } else {
                mLightningView.setMobileUserAgent();
            }
        }
        mRequestDesktopSite = event.isDesktopSiteEnabled;
    }

    @Subscribe
    public void openUrlInCurrentTab(BrowserEvents.OpenUrlInCurrentTab event) {
        if (mLightningView != null && !event.url.isEmpty()) {
            mLightningView.loadUrl(event.url);
        }
    }

    void updateTitle() {
        if (state.getMode() == Mode.SEARCH) {
            return;
        }
        final String title = mLightningView.getTitle();
        searchBar.setTitle(title);
        state.setTitle(title);
        final Activity activity = getActivity();
        if (activity != null) {
            ActivityUtils.setTaskDescription(activity, title,
                    R.color.primary_color_dark, R.mipmap.ic_launcher);
        }
    }

    private void setSearchEngine() {
        mSearchEngine = getString(preferenceManager.getSearchChoice().engineUrl);
    }

    public void resetFindInPage() {
        if (mLightningView != null) {
            mLightningView.findInPage("");
        }
    }

    @Subscribe
    public void switchToForgetMode(Messages.SwitchToForget event) {
        Toast.makeText(getContext(), getString(R.string.switched_to_forget), Toast.LENGTH_SHORT).show();
        mIsIncognito = true;
        state.setIncognito(true);
        mLightningView.setIsIncognitoTab(true);
        mLightningView.setIsAutoForgetTab(true);
        updateUI();
        searchView.initExtensionPreferences();
        queryManager.setForgetMode(true);
    }

    @Subscribe
    public void switchToNormalMode(Messages.SwitchToNormalTab event) {
        mIsIncognito = false;
        state.setIncognito(false);
        mLightningView.setIsIncognitoTab(false);
        mLightningView.setIsAutoForgetTab(false);
        updateUI();
        searchView.initExtensionPreferences();
    }

    @Subscribe
    public void showToolBar(BrowserEvents.ShowToolBar event) {
        showToolbar();
    }

    @Subscribe
    public void updateControlIcon(Messages.UpdateControlCenterIcon event) {
        if (ccIcon == null) {
            return;
        }
        final ControlCenterStatus status;
        if (!preferenceManager.isAttrackEnabled()) {
            status = ControlCenterStatus.DISABLED;
        } else {
            status = event.status;
        }
        ccIcon.setImageLevel(status.ordinal());
    }

    @Subscribe
    public void enableAdBlock(Messages.EnableAdBlock event) {
        preferenceManager.setAdBlockEnabled(true);
        mLightningView.enableAdBlock();
        mLightningView.reload();
    }


    @Subscribe
    public void enableAttrack(Messages.EnableAttrack event) {
        preferenceManager.setAttrackEnabled(true);
        mLightningView.enableAttrack();
        mLightningView.reload();
    }

    @Subscribe
    public void updateFavIcon(Messages.UpdateFavIcon event) {
        state.setFavIcon(mLightningView.getFavicon());
    }

    @Subscribe
    public void keyBoardClosed(Messages.KeyBoardClosed event) {
        searchBar.setTitle(searchBar.getQuery());
        searchBar.showTitleBar();
    }

    @Subscribe
    public void updateQuery(Messages.UpdateQuery event) {
        searchBar.updateQuery(event.suggestion);
    }

    @Subscribe
    public void suggestions(Messages.QuerySuggestions event) {
        final String query = searchBar.getQuery();
        if (quickAccessBar == null) {
            return;
        }
        if (query.length() == 0) {
            return;
        }
        if (!query.startsWith(event.query)) {
            return;
        }
        quickAccessBar.showSuggestions(event.suggestions, event.query);
    }

    @Subscribe
    public void subscribeToNotifications(CliqzMessages.Subscribe event) {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            if (event.type == null || event.subtype == null || event.id == null ||
                    EnableNotificationDialog.showIfNeeded(context, telemetry) != null) {
                return;
            }
            if (!preferenceManager.isFirstSubscription()) {
                subscriptionsManager.addSubscription(event.type, event.subtype, event.id);
                event.resolve();
            } else {
                ConfirmSubscriptionDialog.show(context, bus,
                        subscriptionsManager, telemetry, event);
                preferenceManager.setFirstSubscription(false);
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
    }

    @Subscribe
    public void unsubscribeToNotifications(CliqzMessages.Unsubscribe event) {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            if (event.type == null || event.subtype == null || event.id == null ||
                    EnableNotificationDialog.showIfNeeded(context, telemetry) != null) {
                return;
            }
            subscriptionsManager.removeSubscription(event.type, event.subtype, event.id);
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context", e);
        }
        event.resolve();
    }

    @Subscribe
    public void notifySubscrioption(Messages.NotifySubscription event) {
        searchView.initExtensionPreferences();
    }

    @Subscribe
    public void onFreshTabVisible(Messages.OnFreshTabVisible event) {
        try {
            final Context context = FragmentUtilsV4.getContext(this);
            toolBarContainer.setBackgroundColor(ContextCompat.getColor(context,
                    mIsIncognito ? R.color.fresh_tab_incognito_background : R.color.fresh_tab_background));
            overflowMenuIcon.setColorFilter(ContextCompat.getColor(context, mIsIncognito ?
                    R.color.cardview_light_background : R.color.black), PorterDuff.Mode.SRC_IN);
            openTabsCounter.getBackground().setColorFilter(ContextCompat.getColor(context,
                    mIsIncognito ? R.color.white : R.color.black), PorterDuff.Mode.SRC_IN);
            openTabsCounter.setTextColor(ContextCompat.getColor(context,
                    mIsIncognito ? R.color.white : R.color.black));
            readerModeWebview.setVisibility(View.GONE);
            readerModeButton.setVisibility(View.GONE);
            mIsReaderModeOn = false;
            readerModeButton.setImageResource(R.drawable.ic_reader_mode_off);
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null context");
        }
    }

    @Subscribe
    public void onOrientationChanged(Configuration newConfig) {
        final String view;
        if (state.getMode() == Mode.SEARCH) {
            if (searchView.isFreshTabVisible()) {
                view = TelemetryKeys.HOME;
            } else {
                view = TelemetryKeys.CARDS;
            }
        } else {
            view = TelemetryKeys.WEB;
        }
        telemetry.sendOrientationSignal(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ?
                TelemetryKeys.LANDSCAPE : TelemetryKeys.PORTRAIT, view);
    }

    private void updateUI() {
        @StyleRes final int themeId = getFragmentTheme();
        final Context wrapper = new ContextThemeWrapper(getActivity(), themeId);
        final Resources.Theme theme = wrapper.getTheme();

        final TypedArray typedArray = theme.obtainStyledAttributes(new int[]{
                R.attr.colorPrimary,
                R.attr.colorPrimaryDark,
                R.attr.textColorPrimary,
        });
        final int iconColor = typedArray.getColor(2,
                ContextCompat.getColor(wrapper, R.color.text_color_primary));
        final int toolbarColor = typedArray.getColor(0,
                ContextCompat.getColor(wrapper, mIsIncognito ? R.color.incognito_tab_primary_color
                        : R.color.primary_color));
        final int statusBarColor = typedArray.getColor(1,
                ContextCompat.getColor(wrapper, R.color.primary_color_dark));
        typedArray.recycle();
        toolBarContainer.setBackgroundColor(toolbarColor);
        openTabsCounter.getBackground().setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
        overflowMenuIcon.getDrawable().setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
        openTabsCounter.setTextColor(iconColor);
        try {
            final Activity activity = FragmentUtilsV4.getActivity(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.getWindow().setStatusBarColor(statusBarColor);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                SystemBarTintManager tintManager = new SystemBarTintManager(activity);
                tintManager.setStatusBarTintEnabled(true);
                tintManager.setNavigationBarTintEnabled(true);
                tintManager.setTintColor(statusBarColor);
            }
        } catch (NoInstanceException e) {
            Log.e(TAG, "Null activity", e);
        }
    }

    protected void disableUrlBarScrolling() {
        final AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        params.setScrollFlags(0);
        mContentContainer.requestLayout();
    }

    protected void enableUrlBarScrolling() {
        final AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP);
        mContentContainer.requestLayout();
    }

    private void showRequestLocationAccessDialog() {
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setMessage(R.string.request_location_access)
                .setPositiveButton(R.string.action_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            preferenceManager.setLocationEnabled(true);
                            dialog.dismiss();
                            final MainActivity activity = (MainActivity) FragmentUtilsV4
                                    .getActivity(TabFragment.this);
                            searchView.updateQuery(state.getQuery());
                            if (!activity.locationCache.isGPSEnabled()) {
                                Toast.makeText(getContext(), R.string.gps_permission, Toast.LENGTH_SHORT).show();
                            }
                        } catch (NoInstanceException e) {
                            Log.e(TAG, "Null activity", e);
                        }
                    }
                })
                .setNegativeButton(R.string.action_dont_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void showYTIcon() {
        if (ytDownloadIcon.getVisibility() == View.VISIBLE) {
            return;
        }
        telemetry.sendYTIconVisibleSignal();
        ytDownloadIcon.setVisibility(View.VISIBLE);
    }

    protected void hideYTIcon() {
        if (ytDownloadIcon.getVisibility() == View.GONE) {
            return;
        }
        ytDownloadIcon.setVisibility(View.GONE);
    }

    // TODO: dirty hack due to the Oreo multi-process WebView
    // Due to the loading page delay introduced to fix the multi-process WebView, we have to
    // open urls from history and favorites like they are initial urls (meaning similar logic).
    public void openFromOverview(String url) {
        mOverviewUrl = url;
    }
}
