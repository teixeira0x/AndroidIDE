package com.itsaky.androidide.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.transition.TransitionManager;

import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;

import com.itsaky.androidide.R;
import com.itsaky.androidide.adapters.WizardTemplateAdapter;
import com.itsaky.androidide.app.StudioApp;
import com.itsaky.androidide.databinding.FragmentWizardBinding;
import com.itsaky.androidide.databinding.LayoutLoadingWizardBinding;
import com.itsaky.androidide.databinding.SetupFooterBinding;
import com.itsaky.androidide.databinding.WizardDetailsBinding;
import com.itsaky.androidide.databinding.WizardTemplatesBinding;
import com.itsaky.androidide.fragments.sheets.ProgressSheet;
import com.itsaky.androidide.interfaces.ProjectWriterCallback;
import com.itsaky.androidide.managers.PreferenceManager;
import com.itsaky.androidide.models.NewProjectDetails;
import com.itsaky.androidide.models.ProjectTemplate;
import com.itsaky.androidide.tasks.TaskExecutor;
import com.itsaky.androidide.tasks.callables.ProjectCreatorCallable;
import com.itsaky.androidide.utils.AndroidUtils;
import com.itsaky.androidide.utils.Environment;
import com.itsaky.androidide.utils.FileUtil;
import com.itsaky.androidide.utils.SingleTextWatcher;
import com.itsaky.toaster.Toaster;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

public class WizardFragment extends BaseFragment implements ProjectWriterCallback {

  public static final String TAG = "WizardFragmentTag";
  public static final String PREF_PACKAGE_DOMAIN_KEY = "pref_package_domain";
  public static final String PREF_MIN_SDK_INDEX_KEY = "pref_min_sdk_index";
  public static final String PREF_TERGET_SDK_INDEX_KEY = "pref_target_sdk_index";
  public static final String PREF_SAVE_PROJECT_DIR_KEY = "pref_save_prohect_dir";

  private FragmentWizardBinding binding;
  private WizardTemplatesBinding templatesBinding;
  private WizardDetailsBinding detailsBinding;
  private WizardTemplateAdapter mAdapter;
  private LayoutLoadingWizardBinding loadingLayout;
  private SetupFooterBinding footerBinding;

  private ProgressSheet mProgressSheet;
  private ProjectTemplate mCurrentTemplate;
  private OnProjectCreatedListener mListener;

  private boolean mLast;
  private int minSdkIndex;
  private int targetSdkIndex;

  private final OnBackPressedCallback onBackPressedCallback =
      new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
          onNavigateBack();
        }
      };

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    binding = FragmentWizardBinding.inflate(inflater, container, false);
    loadingLayout = binding.loadingLayout;
    templatesBinding = binding.wizardTemplatesLayout;
    detailsBinding = binding.wizardDetailsLayout;
    footerBinding = binding.footerLayout;
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(getViewLifecycleOwner(), onBackPressedCallback);

    footerBinding.nextButton.setOnClickListener(v -> onNavigateNext());
    footerBinding.exitButton.setOnClickListener(v -> onNavigateBack());

    loadTemplates();

    templatesBinding.templateRecyclerview.setLayoutManager(
        new GridLayoutManager(requireContext(), 3));

    mAdapter = new WizardTemplateAdapter();
    templatesBinding.templateRecyclerview.setAdapter(mAdapter);

    initDetailsView();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    onBackPressedCallback.setEnabled(false);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
    loadingLayout = null;
    templatesBinding = null;
    detailsBinding = null;
    footerBinding = null;
    mAdapter = null;
    mProgressSheet = null;
  }

  private String getSelectedItem(int pos, AutoCompleteTextView view) {
    return view.getAdapter().getItem(pos).toString();
  }

  private void loadTemplates() {

    TransitionManager.beginDelayedTransition((ViewGroup) requireView(), new MaterialFadeThrough());
    binding.loadingLayout.getRoot().setVisibility(View.VISIBLE);
    binding.wizardTemplatesLayout.getRoot().setVisibility(View.GONE);

    Executors.newSingleThreadExecutor()
        .execute(
            () -> {
              List<ProjectTemplate> templates = createTemplates();

              if (getActivity() != null) {
                getActivity()
                    .runOnUiThread(
                        () -> {
                          TransitionManager.beginDelayedTransition(
                              (ViewGroup) requireView(), new MaterialFadeThrough());
                          binding.loadingLayout.getRoot().setVisibility(View.GONE);
                          binding.wizardTemplatesLayout.getRoot().setVisibility(View.VISIBLE);

                          mAdapter.submitList(templates);

                          mAdapter.setOnItemClickListener(
                              (item, pos) -> {
                                mCurrentTemplate = item;
                                onNavigateNext();
                              });
                        });
              }
            });
  }

  private List<ProjectTemplate> createTemplates() {
    List<ProjectTemplate> mTemplates = new ArrayList<>();

    ProjectTemplate
        empty =
            new ProjectTemplate()
                .setId(0)
                .setSupportJava(true)
                .setSupportKotlin(true)
                .setName(requireContext(), R.string.template_empty)
                .setDescription(requireContext(), R.string.template_description_empty)
                .setImageId(R.drawable.template_empty),
        basic =
            new ProjectTemplate()
                .setId(1)
                .setSupportJava(true)
                .setSupportKotlin(true)
                .setName(requireContext(), R.string.template_basic)
                .setDescription(requireContext(), R.string.template_description_basic)
                .setImageId(R.drawable.template_basic),
        drawer =
            new ProjectTemplate()
                .setId(2)
                .setSupportJava(true)
                .setSupportKotlin(true)
                .setName(requireContext(), R.string.template_navigation_drawer)
                .setDescription(requireContext(), R.string.template_description_navigation_drawer)
                .setImageId(R.drawable.template_navigation_drawer),
        libgdx =
            new ProjectTemplate()
                .setId(3)
                .setSupportJava(true)
                .setName(requireContext(), R.string.template_libgdx)
                .setDescription(requireContext(), R.string.template_description_libgdx)
                .setImageId(R.drawable.template_libgdx),
        compose =
            new ProjectTemplate()
                .setId(4)
                .setSupportKotlin(true)
                .setName(requireContext(), R.string.template_compose)
                .setDescription(requireContext(), R.string.template_description_compose)
                .setImageId(R.drawable.template_kotlin);
    mTemplates.add(empty);
    mTemplates.add(basic);
    mTemplates.add(drawer);
    mTemplates.add(libgdx);
    mTemplates.add(compose);
    return mTemplates;
  }

  private int getMinSdk() {
    try {
      String minSdk =
          detailsBinding
              .etMinSdk
              .getText()
              .toString()
              .substring("API".length() + 1, "API".length() + 3); // at least 2 digits
      int minSdkInt = Integer.parseInt(minSdk);

      return minSdkInt;
    } catch (Exception e) {
      StudioApp.getInstance().toast(e.getMessage(), Toaster.Type.ERROR);
    }
    return -1;
  }

  private int getTargetSdk() {
    try {
      String targetSdk =
          detailsBinding
              .etTargetSdk
              .getText()
              .toString()
              .substring("API".length() + 1, "API".length() + 3); // at least 2 digits
      int targetSdkInt = Integer.parseInt(targetSdk);
      return targetSdkInt;
    } catch (Exception e) {
      StudioApp.getInstance().toast(e.getMessage(), Toaster.Type.ERROR);
    }
    return -1;
  }

  private boolean validateDetails() {

    requireActivity()
        .runOnUiThread(
            () -> {
              verifyPackageName(detailsBinding.tilPackageName.getEditText().getText());
              verifyAppName(detailsBinding.tilAppName.getEditText().getText());
            });

    if (detailsBinding.tilPackageName.isErrorEnabled()) {
      return false;
    }

    if (detailsBinding.tilAppName.isErrorEnabled()) {
      return false;
    }

    return mCurrentTemplate != null;
  }

  private void createNewProject() {
    if (validateDetails()) {
      final String appName =
          Objects.requireNonNull(detailsBinding.etAppName).getText().toString().trim();
      final String packageName =
          Objects.requireNonNull(detailsBinding.etPackageName).getText().toString().trim();
      final int minSdk = getMinSdk();
      final int targetSdk = getTargetSdk();
      final String savePath = detailsBinding.etSaveLocation.getText().toString().trim();
      final String projectLanguage = detailsBinding.etLanguage.getText().toString();

      PreferenceManager manager = StudioApp.getInstance().getPrefManager();
      manager.putInt(PREF_MIN_SDK_INDEX_KEY, minSdkIndex);
      manager.putInt(PREF_TERGET_SDK_INDEX_KEY, targetSdkIndex);
      manager.putString(PREF_PACKAGE_DOMAIN_KEY, AndroidUtils.getPackageDomain(packageName));
      createProject(appName, packageName, minSdk, targetSdk, projectLanguage, savePath);
    }
  }

  private List<String> getSdks() {
    return Arrays.asList(
        "API 16: Android 4.0 (Ice Cream Sandwich)",
        "API 17: Android 4.2 (JellyBean)",
        "API 18: Android 4.3 (JellyBean)",
        "API 19: Android 4.4 (KitKat)",
        "API 20: Android 4.4W (KitKat Wear)",
        "API 21: Android 5.0 (Lollipop)",
        "API 22: Android 5.1 (Lollipop)",
        "API 23: Android 6.0 (Marshmallow)",
        "API 24: Android 7.0 (Nougat)",
        "API 25: Android 7.1 (Nougat)",
        "API 26: Android 8.0 (Oreo)",
        "API 27: Android 8.1 (Oreo)",
        "API 28: Android 9.0 (Pie)",
        "API 29: Android 10.0 (Q)",
        "API 30: Android 11.0 (R)",
        "API 31: Android 12.0 (S)",
        "API 32: Android 12.1L (S)");
  }

  private void onNavigateBack() {
    if (!mLast) {
      getParentFragmentManager().popBackStack();
    } else {
      showTemplatesView();
      mLast = false;
    }
  }

  private void onNavigateNext() {
    if (!mLast) {
      showDetailsView();
      mLast = true;
    } else {
      createNewProject();
    }
  }

  private void showTemplatesView() {
    templatesBinding.getRoot().setVisibility(View.GONE);

    MaterialSharedAxis sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.X, false);

    TransitionManager.beginDelayedTransition((ViewGroup) requireView(), sharedAxis);

    detailsBinding.getRoot().setVisibility(View.GONE);
    templatesBinding.getRoot().setVisibility(View.VISIBLE);
    footerBinding.nextButton.setVisibility(View.GONE);
    footerBinding.nextButton.setText(R.string.next);
    footerBinding.exitButton.setText(R.string.exit);
  }

  private void showDetailsView() {
    PreferenceManager manager = StudioApp.getInstance().getPrefManager();

    List<String> languages = new ArrayList<>();
    if (mCurrentTemplate != null) {
      if (mCurrentTemplate.isSupportJava()) {
        languages.add("Java");
      }
      if (mCurrentTemplate.isSupportKotlin()) {
        languages.add("Kotlin");
      }
    }

    detailsBinding.etAppName.setText("My Application");
    String domain = manager.getString(PREF_PACKAGE_DOMAIN_KEY, "com.example");
    detailsBinding.etPackageName.setText(domain + ".myapplication");
    detailsBinding.etLanguage.setAdapter(
        new ArrayAdapter<>(
            requireContext(),
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            languages));

    detailsBinding.etLanguage.setListSelection(0);
    detailsBinding.etLanguage.setText(getSelectedItem(0, detailsBinding.etLanguage), false);
    minSdkIndex = manager.getInt(PREF_MIN_SDK_INDEX_KEY, 5);
    targetSdkIndex = manager.getInt(PREF_TERGET_SDK_INDEX_KEY, 16);
    detailsBinding.etMinSdk.setListSelection(minSdkIndex);
    detailsBinding.etMinSdk.setText(getSelectedItem(minSdkIndex, detailsBinding.etMinSdk), false);

    detailsBinding.etTargetSdk.setListSelection(targetSdkIndex);
    detailsBinding.etTargetSdk.setText(
        getSelectedItem(targetSdkIndex, detailsBinding.etTargetSdk), false);

    requireActivity()
        .runOnUiThread(
            () -> {
              setSaveLocation();
            });

    loadingLayout.getRoot().setVisibility(View.GONE);
    detailsBinding.getRoot().setVisibility(View.GONE);

    MaterialSharedAxis sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.X, true);

    TransitionManager.beginDelayedTransition((ViewGroup) requireView(), sharedAxis);

    detailsBinding.getRoot().setVisibility(View.VISIBLE);
    templatesBinding.getRoot().setVisibility(View.GONE);
    footerBinding.nextButton.setText(R.string.create_project);
    footerBinding.nextButton.setVisibility(View.VISIBLE);
    footerBinding.exitButton.setText(R.string.previous);
  }

  private void initDetailsView() {
    PreferenceManager manager = StudioApp.getInstance().getPrefManager();

    detailsBinding
        .tilAppName
        .getEditText()
        .addTextChangedListener(
            new SingleTextWatcher() {
              @Override
              public void afterTextChanged(Editable editable) {
                verifyAppName(editable);
              }
            });

    detailsBinding
        .tilPackageName
        .getEditText()
        .addTextChangedListener(
            new SingleTextWatcher() {
              @Override
              public void afterTextChanged(Editable editable) {
                verifyPackageName(editable);
              }
            });

    detailsBinding.etMinSdk.setAdapter(
        new ArrayAdapter<>(
            requireContext(),
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            getSdks()));

    detailsBinding.etMinSdk.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            minSdkIndex = position;
          }
        });

    detailsBinding.etTargetSdk.setAdapter(
        new ArrayAdapter<>(
            requireContext(),
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            getSdks()));

    detailsBinding.etTargetSdk.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            targetSdkIndex = position;
          }
        });

    detailsBinding.tilSaveLocation.setEndIconOnClickListener(
        v -> {
          pickDirectory(
              p -> {
                manager.putString(PREF_SAVE_PROJECT_DIR_KEY, p.getAbsolutePath());
                Environment.setProjectDir(p);
                setSaveLocation();
              });
        });
  }

  private void setPackageName(String appName) {
    final String packageName =
        Objects.requireNonNull(detailsBinding.etPackageName).getText().toString().trim();
    String newPackageName = AndroidUtils.appNameToPackageName(appName, packageName);
    detailsBinding.etPackageName.setText(newPackageName);
  }

  private void setSaveLocation() {
    PreferenceManager manager = StudioApp.getInstance().getPrefManager();

    String saveProjectPath =
        manager.getString(PREF_SAVE_PROJECT_DIR_KEY, Environment.PROJECTS_DIR.getAbsolutePath())
            + File.separator
            + AndroidUtils.trimWhiteSpace(detailsBinding.etAppName.getText().toString().trim());

    String newPath = FileUtil.getTargetNonExistPath(saveProjectPath, true);
    detailsBinding.etSaveLocation.setText(newPath);
  }

  private void verifyPackageName(Editable editable) {
    String packageName = editable.toString();
    String pkgCheckerMsg = AndroidUtils.validatePackageName(packageName);
    if (!TextUtils.isEmpty(pkgCheckerMsg)) {
      detailsBinding.tilPackageName.setError(pkgCheckerMsg);
      return;
    } else {
      detailsBinding.tilPackageName.setErrorEnabled(false);
    }
  }

  private void verifyAppName(Editable editable) {
    String name = editable.toString().trim();
    if (TextUtils.isEmpty(name)) {
      detailsBinding.tilAppName.setError(getString(R.string.wizard_error_name_empty));
      return;
    } else if (name.contains(File.pathSeparator)
        || name.contains(File.separator)
        || AndroidUtils.isIdentifier(name)) {
      detailsBinding.tilAppName.setError(getString(R.string.wizard_error_name_illegal));
      return;
    } else {
      detailsBinding.tilAppName.setErrorEnabled(false);
    }

    setPackageName(name);
    setSaveLocation();
  }

  @Override
  public void beforeBegin() {
    if (mProgressSheet == null) {
      createProgressSheet();
    }

    setMessage(R.string.msg_begin_project_write);
  }

  @Override
  public void onProcessTask(String taskName) {
    setMessage(taskName);
  }

  @Override
  public void onSuccess(File root) {
    if (mProgressSheet == null) {
      createProgressSheet();
    }

    if (mProgressSheet.isShowing()) {
      mProgressSheet.dismiss();
    }
    StudioApp.getInstance().toast(R.string.project_created_successfully, Toaster.Type.SUCCESS);

    if (getActivity() != null && mListener != null) {
      requireActivity()
          .runOnUiThread(
              () -> {
                getParentFragmentManager().popBackStack();
                mListener.openProject(root);
              });
    }
  }

  @Override
  public void onFailed(String reason) {
    if (mProgressSheet == null) {
      createProgressSheet();
    }

    if (mProgressSheet.isShowing()) {
      mProgressSheet.dismiss();
    }
    StudioApp.getInstance().toast(reason, Toaster.Type.ERROR);
  }

  private void createProgressSheet() {
    mProgressSheet = new ProgressSheet();
    mProgressSheet.setShowShadow(false);
  }

  private void setMessage(int msg) {
    setMessage(getString(msg));
  }

  private void setMessage(String msg) {
    mProgressSheet.setMessage(msg);
  }

  private void createProject(
      String appName,
      String packageName,
      int minSdk,
      int targetSdk,
      String language,
      String savePath) {
    new TaskExecutor()
        .executeAsync(
            new ProjectCreatorCallable(
                mCurrentTemplate,
                new NewProjectDetails(appName, packageName, minSdk, targetSdk, language, savePath),
                this),
            r -> {});
  }

  public interface OnProjectCreatedListener {
    void openProject(File project);
  }

  public void setOnProjectCreatedListener(OnProjectCreatedListener listener) {
    mListener = listener;
  }
}
