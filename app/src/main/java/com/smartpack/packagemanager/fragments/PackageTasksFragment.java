/*
 * Copyright (C) 2021-2022 sunilpaulmathew <sunil.kde@gmail.com>
 *
 * This file is part of Package Manager, a simple, yet powerful application
 * to manage other application installed on an android device.
 *
 */

package com.smartpack.packagemanager.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.smartpack.packagemanager.R;
import com.smartpack.packagemanager.activities.AboutActivity;
import com.smartpack.packagemanager.activities.SettingsActivity;
import com.smartpack.packagemanager.adapters.RecycleViewAdapter;
import com.smartpack.packagemanager.utils.PackageData;
import com.smartpack.packagemanager.utils.PackageTasks;
import com.smartpack.packagemanager.utils.SplitAPKInstaller;
import com.smartpack.packagemanager.utils.Utils;

import java.io.File;
import java.util.List;
import java.util.Objects;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on October 08, 2020
 */
public class PackageTasksFragment extends Fragment {

    private AppCompatEditText mSearchWord;
    private AppCompatImageButton mSettings, mSort;
    private AsyncTask<Void, Void, List<String>> mLoader;
    private boolean mExit;
    private Handler mHandler = new Handler();
    private MaterialTextView mAppTitle;
    private LinearLayout mProgressLayout;
    private RecyclerView mRecyclerView;
    private RecycleViewAdapter mRecycleViewAdapter;
    private String mPath;

    @SuppressLint("StaticFieldLeak")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View mRootView = inflater.inflate(R.layout.fragment_packagetasks, container, false);

        mAppTitle = mRootView.findViewById(R.id.app_title);
        mProgressLayout = mRootView.findViewById(R.id.progress_layout);
        PackageTasks.mBatchOptions = mRootView.findViewById(R.id.batch_options);
        mRecyclerView = mRootView.findViewById(R.id.recycler_view);
        mSearchWord = mRootView.findViewById(R.id.search_word);
        AppCompatImageButton mSearch = mRootView.findViewById(R.id.search_icon);
        mSort = mRootView.findViewById(R.id.sort_icon);
        mSettings = mRootView.findViewById(R.id.settings_icon);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(requireActivity(), DividerItemDecoration.VERTICAL));

        loadUI(requireActivity());

        mSearch.setOnClickListener(v -> {
            if (mSearchWord.getVisibility() == View.VISIBLE) {
                mSearchWord.setVisibility(View.GONE);
                mAppTitle.setVisibility(View.VISIBLE);
            } else {
                mSearchWord.setVisibility(View.VISIBLE);
                mSearchWord.requestFocus();
                mAppTitle.setVisibility(View.GONE);
            }
        });

        mSearchWord.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                PackageData.mSearchText = s.toString().toLowerCase();
                reload(requireActivity());
            }
        });

        mSettings.setOnClickListener(v -> settingsMenu(requireActivity()));

        mSort.setOnClickListener(v -> sortMenu(requireActivity()));

        PackageTasks.mBatchOptions.setOnClickListener(v -> batchOptionsMenu(requireActivity()));

        requireActivity().getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (PackageData.mSearchText != null) {
                    mSearchWord.setText(null);
                    PackageData.mSearchText = null;
                    return;
                }
                if (mSearchWord.getVisibility() == View.VISIBLE) {
                    mSearchWord.setVisibility(View.GONE);
                    mAppTitle.setVisibility(View.VISIBLE);
                    return;
                }
                if (!PackageData.getBatchList().isEmpty() && PackageData.getBatchList().contains(".")) {
                    new MaterialAlertDialogBuilder(requireActivity())
                            .setMessage(R.string.batch_warning)
                            .setCancelable(false)
                            .setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                            })
                            .setPositiveButton(getString(R.string.yes), (dialogInterface, i) -> {
                                requireActivity().finish();
                            })
                            .show();
                } else if (mExit) {
                    mExit = false;
                    requireActivity().finish();
                } else {
                    Utils.snackbar(mRootView, getString(R.string.press_back));
                    mExit = true;
                    mHandler.postDelayed(() -> mExit = false, 2000);
                }
            }
        });

        return mRootView;
    }

    private void sortMenu(Activity activity) {
        PopupMenu popupMenu = new PopupMenu(activity, mSort);
        Menu menu = popupMenu.getMenu();
        menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.system)).setCheckable(true)
                .setChecked(Utils.getBoolean("system_apps", true, activity));
        menu.add(Menu.NONE, 2, Menu.NONE, getString(R.string.user)).setCheckable(true)
                .setChecked(Utils.getBoolean("user_apps", true, activity));
        SubMenu sort = menu.addSubMenu(Menu.NONE, 0, Menu.NONE, getString(R.string.sort_by));
        sort.add(Menu.NONE, 3, Menu.NONE, getString(R.string.name)).setCheckable(true)
                .setChecked(Utils.getBoolean("sort_name", false, activity));
        sort.add(Menu.NONE, 4, Menu.NONE, getString(R.string.package_id)).setCheckable(true)
                .setChecked(Utils.getBoolean("sort_id", true, activity));
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    break;
                case 1:
                    if (Utils.getBoolean("system_apps", true, activity)) {
                        Utils.saveBoolean("system_apps", false, activity);
                    } else {
                        Utils.saveBoolean("system_apps", true, activity);
                    }
                    reload(activity);
                    break;
                case 2:
                    if (Utils.getBoolean("user_apps", true, activity)) {
                        Utils.saveBoolean("user_apps", false, activity);
                    } else {
                        Utils.saveBoolean("user_apps", true, activity);
                    }
                    reload(activity);
                    break;
                case 3:
                    if (!Utils.getBoolean("sort_name", false, activity)) {
                        Utils.saveBoolean("sort_name", true, activity);
                        Utils.saveBoolean("sort_id", false, activity);
                        reload(activity);
                    }
                    break;
                case 4:
                    if (!Utils.getBoolean("sort_id", true, activity)) {
                        Utils.saveBoolean("sort_id", true, activity);
                        Utils.saveBoolean("sort_name", false, activity);
                        reload(activity);
                    }
                    break;
            }
            return false;
        });
        popupMenu.show();
    }

    private void settingsMenu(Activity activity) {
        PopupMenu popupMenu = new PopupMenu(activity, mSettings);
        Menu menu = popupMenu.getMenu();
        if (Utils.rootAccess()) {
            menu.add(Menu.NONE, 0, Menu.NONE, getString(R.string.install_bundle));
        }
        menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.settings));
        menu.add(Menu.NONE, 2, Menu.NONE, getString(R.string.about));
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    if (Utils.isStorageWritePermissionDenied(requireActivity())) {
                        ActivityCompat.requestPermissions(requireActivity(), new String[] {
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
                        Utils.snackbar(mRecyclerView, getString(R.string.permission_denied_write_storage));
                    } else {
                        if (Utils.getBoolean("firstAttempt", true, requireActivity())) {
                            new MaterialAlertDialogBuilder(Objects.requireNonNull(requireActivity()))
                                    .setIcon(R.mipmap.ic_launcher)
                                    .setTitle(getString(R.string.install_bundle))
                                    .setMessage(getString(R.string.bundle_install_message))
                                    .setCancelable(false)
                                    .setPositiveButton(getString(R.string.got_it), (dialog, id) -> {
                                        Utils.saveBoolean("firstAttempt", false, requireActivity());
                                        initializeSplitAPKInstallation();
                                    }).show();
                        } else {
                            initializeSplitAPKInstallation();
                        }
                    }
                    break;
                case 1:
                    Intent settingsPage = new Intent(activity, SettingsActivity.class);
                    startActivity(settingsPage);
                    break;
                case 2:
                    Intent aboutView = new Intent(activity, AboutActivity.class);
                    startActivity(aboutView);
                    break;
            }
            return false;
        });
        popupMenu.show();
    }

    private void batchOptionsMenu(Activity activity) {
        PopupMenu popupMenu = new PopupMenu(activity, PackageTasks.mBatchOptions);
        Menu menu = popupMenu.getMenu();
        if (Utils.rootAccess()) {
            menu.add(Menu.NONE, 0, Menu.NONE, getString(R.string.turn_on_off));
            menu.add(Menu.NONE, 1, Menu.NONE, getString(R.string.uninstall));
            menu.add(Menu.NONE, 2, Menu.NONE, getString(R.string.reset));
        }
        menu.add(Menu.NONE, 3, Menu.NONE, getString(R.string.export));
        menu.add(Menu.NONE, 4, Menu.NONE, getString(R.string.batch_list_clear));
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    new MaterialAlertDialogBuilder(activity)
                            .setIcon(R.mipmap.ic_launcher)
                            .setTitle(R.string.sure_question)
                            .setMessage(getString(R.string.batch_list_disable) + "\n" + PackageData.showBatchList())
                            .setNeutralButton(getString(R.string.cancel), (dialogInterface, i) -> {
                            })
                            .setPositiveButton(getString(R.string.turn_on_off), (dialogInterface, i) -> {
                                PackageTasks.batchDisableTask(activity);
                            })
                            .show();
                    break;
                case 1:
                    MaterialAlertDialogBuilder uninstall = new MaterialAlertDialogBuilder(activity);
                    uninstall.setIcon(R.mipmap.ic_launcher);
                    uninstall.setTitle(R.string.sure_question);
                    uninstall.setMessage(getString(R.string.batch_list_remove) + "\n" + PackageData.showBatchList());
                    uninstall.setNeutralButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    });
                    uninstall.setPositiveButton(getString(R.string.uninstall), (dialogInterface, i) -> {
                        PackageTasks.batchUninstallTask(activity);
                    });
                    uninstall.show();
                    break;
                case 2:
                    MaterialAlertDialogBuilder reset = new MaterialAlertDialogBuilder(activity);
                    reset.setIcon(R.mipmap.ic_launcher);
                    reset.setTitle(R.string.sure_question);
                    reset.setMessage(getString(R.string.batch_list_reset) + "\n" + PackageData.showBatchList());
                    reset.setNeutralButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    });
                    reset.setPositiveButton(getString(R.string.reset), (dialogInterface, i) -> {
                        PackageTasks.batchResetTask(activity);
                    });
                    reset.show();
                    break;
                case 3:
                    MaterialAlertDialogBuilder export = new MaterialAlertDialogBuilder(activity);
                    export.setIcon(R.mipmap.ic_launcher);
                    export.setTitle(R.string.sure_question);
                    export.setMessage(getString(R.string.batch_list_export) + "\n" + PackageData.showBatchList());
                    export.setNeutralButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    });
                    export.setPositiveButton(getString(R.string.export), (dialogInterface, i) -> {
                        PackageTasks.batchExportTask(activity);
                    });
                    export.show();
                    break;
                case 4:
                    PackageData.mBatchList.clear();
                    reload(activity);
                    break;
            }
            return false;
        });
        popupMenu.show();
    }

    private void initializeSplitAPKInstallation() {
        Intent install = new Intent(Intent.ACTION_GET_CONTENT);
        install.setType("*/*");
        startActivityForResult(install, 0);
    }

    @SuppressLint("StaticFieldLeak")
    private void loadUI(Activity activity) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mProgressLayout.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
            }
            @Override
            protected Void doInBackground(Void... voids) {
                mRecycleViewAdapter = new RecycleViewAdapter(PackageData.getData(activity));
                return null;
            }
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                mRecyclerView.setAdapter(mRecycleViewAdapter);
                mProgressLayout.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
            }
        }.execute();
    }

    private void reload(Activity activity) {
        if (mLoader == null) {
            mHandler.postDelayed(new Runnable() {
                @SuppressLint("StaticFieldLeak")
                @Override
                public void run() {
                    mLoader = new AsyncTask<Void, Void, List<String>>() {
                        @Override
                        protected void onPreExecute() {
                            super.onPreExecute();
                            mProgressLayout.setVisibility(View.VISIBLE);
                            PackageTasks.mBatchOptions.setVisibility(View.GONE);
                            mRecyclerView.setVisibility(View.GONE);
                            if (!PackageData.getBatchList().isEmpty() && PackageData.getBatchList().contains(".")) {
                                PackageData.mBatchList.clear();
                            }
                            mRecyclerView.removeAllViews();
                        }

                        @Override
                        protected List<String> doInBackground(Void... voids) {
                            mRecycleViewAdapter = new RecycleViewAdapter(PackageData.getData(activity));
                            return null;
                        }

                        @Override
                        protected void onPostExecute(List<String> recyclerViewItems) {
                            super.onPostExecute(recyclerViewItems);
                            mRecyclerView.setAdapter(mRecycleViewAdapter);
                            mRecycleViewAdapter.notifyDataSetChanged();
                            PackageTasks.mBatchOptions.setVisibility(PackageData.getBatchList().length() > 0 && PackageData
                                    .getBatchList().contains(".") ? View.VISIBLE : View.GONE);
                            mProgressLayout.setVisibility(View.GONE);
                            mRecyclerView.setVisibility(View.VISIBLE);
                            mLoader = null;
                        }
                    };
                    mLoader.execute();
                }
            }, 250);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            assert uri != null;
            File file = new File(Objects.requireNonNull(uri.getPath()));
            if (Utils.isDocumentsUI(uri)) {
                @SuppressLint("Recycle") Cursor cursor = requireActivity().getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    mPath = Environment.getExternalStorageDirectory().toString() + "/Download/" +
                            cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } else {
                mPath = Utils.getPath(file);
            }
            if (requestCode == 0) {
                if (mPath.endsWith(".apk") || mPath.endsWith(".apks") || mPath.endsWith(".apkm") || mPath.endsWith(".xapk")) {
                    MaterialAlertDialogBuilder installApp = new MaterialAlertDialogBuilder(requireActivity());
                    if (mPath.endsWith(".apks") || mPath.endsWith(".apkm") || mPath.endsWith(".xapk")) {
                        installApp.setMessage(getString(R.string.bundle_install_apks, new File(mPath).getName()));
                    } else {
                        installApp.setIcon(R.mipmap.ic_launcher);
                        installApp.setTitle(getString(R.string.sure_question));
                        installApp.setMessage(getString(R.string.bundle_install, Objects.requireNonNull(new File(mPath)
                                .getParentFile()).toString()));
                        installApp.setNeutralButton(getString(R.string.list), (dialogInterface, i) ->
                                new MaterialAlertDialogBuilder(Objects.requireNonNull(requireActivity()))
                                        .setIcon(R.mipmap.ic_launcher)
                                        .setTitle(R.string.split_apk_list)
                                        .setMessage(SplitAPKInstaller.listSplitAPKs(Objects.requireNonNull(new File(mPath).getParentFile()).toString()))
                                        .setNegativeButton(getString(R.string.cancel), (dialog, id) -> {
                                        })
                                        .setPositiveButton(getString(R.string.install), (dialog, id) ->
                                                SplitAPKInstaller.installSplitAPKs(Objects.requireNonNull(new File(mPath).getParentFile())
                                                .toString(), requireActivity())).show());
                    }
                    installApp.setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    });
                    installApp.setPositiveButton(getString(R.string.install), (dialogInterface, i) -> {
                        if (mPath.endsWith(".apk")) {
                            SplitAPKInstaller.installSplitAPKs(Objects.requireNonNull(new File(mPath).getParentFile())
                                    .toString(), requireActivity());
                        } else {
                            SplitAPKInstaller.installSplitAPKs(mPath, requireActivity());
                        }
                    });
                    installApp.show();
                } else {
                    Utils.snackbar(mRecyclerView, getString(R.string.wrong_extension, ".apk/.apks/.apkm/.xapk"));
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Utils.getBoolean("welcomeMessage", true, getActivity())) {
            Utils.WelcomeDialog(getActivity());
        }
        if (PackageTasks.mReloadPage) {
            PackageTasks.mReloadPage = false;
            reload(requireActivity());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (PackageData.mSearchText != null) {
            mSearchWord.setText(null);
            PackageData.mSearchText = null;
        }
    }

}