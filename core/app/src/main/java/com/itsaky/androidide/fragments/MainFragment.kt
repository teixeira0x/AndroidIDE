package com.itsaky.androidide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.itsaky.androidide.activities.MainActivity
import com.itsaky.androidide.activities.PreferencesActivity
import com.itsaky.androidide.activities.TerminalActivity
import com.itsaky.androidide.adapters.MainActionsListAdapter
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.app.BaseIDEActivity
import com.itsaky.androidide.common.databinding.LayoutDialogProgressBinding
import com.itsaky.androidide.databinding.FragmentMainBinding
import com.itsaky.androidide.models.MainScreenAction
import com.itsaky.androidide.preferences.databinding.LayoutDialogTextInputBinding
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.tasks.runOnUiThread
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import com.itsaky.androidide.viewmodel.MainViewModel
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CancellationException

class MainFragment : BaseFragment() {

  private val viewModel by viewModels<MainViewModel>(
    ownerProducer = { requireActivity() })
  private var binding: FragmentMainBinding? = null

  companion object {

    private val log = LoggerFactory.getLogger(MainFragment::class.java)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentMainBinding.inflate(inflater, container, false)
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val actions = MainScreenAction.all().also { actions ->
      val onClick = { action: MainScreenAction, _: View ->
        when (action.id) {
          MainScreenAction.ACTION_CREATE_PROJECT -> showCreateProject()
          MainScreenAction.ACTION_OPEN_PROJECT -> pickDirectory()
          MainScreenAction.ACTION_OPEN_TERMINAL -> startActivity(
            Intent(requireActivity(), TerminalActivity::class.java))

          MainScreenAction.ACTION_PREFERENCES -> gotoPreferences()
          MainScreenAction.ACTION_DONATE -> BaseApplication.getBaseInstance().openDonationsPage()
          MainScreenAction.ACTION_DOCS -> BaseApplication.getBaseInstance().openDocs()
        }
      }

      actions.forEach { action ->
        action.onClick = onClick

        if (action.id == MainScreenAction.ACTION_OPEN_TERMINAL) {
          action.onLongClick = { _: MainScreenAction, _: View ->
            val intent = Intent(requireActivity(), TerminalActivity::class.java).apply {
              putExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, true)
            }
            startActivity(intent)
            true
          }
        }
      }
    }

    binding!!.actions.adapter = MainActionsListAdapter(actions)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }

  private fun pickDirectory() {
    pickDirectory(this::openProject)
  }

  private fun showCreateProject() {
    viewModel.setScreen(MainViewModel.SCREEN_TEMPLATE_LIST)
  }

  fun openProject(root: File) {
    (requireActivity() as MainActivity).openProject(root)
  }

  private fun gotoPreferences() {
    startActivity(Intent(requireActivity(), PreferencesActivity::class.java))
  }
}
