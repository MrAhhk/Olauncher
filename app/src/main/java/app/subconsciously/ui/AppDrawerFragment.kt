package app.subconsciously.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.subconsciously.DrawerNavHint
import app.subconsciously.MainViewModel
import app.subconsciously.R
import app.subconsciously.data.AppModel
import app.subconsciously.data.BlockManager
import app.subconsciously.data.Constants
import app.subconsciously.data.DistractionList
import app.subconsciously.data.Prefs
import app.subconsciously.databinding.FragmentAppDrawerBinding
import app.subconsciously.helper.deletePinnedShortcut
import app.subconsciously.helper.hideKeyboard
import app.subconsciously.helper.isEinkDisplay
import app.subconsciously.helper.isSystemApp
import app.subconsciously.helper.openAppInfo
import app.subconsciously.helper.openSearch
import app.subconsciously.helper.openUrl
import app.subconsciously.helper.showKeyboard
import app.subconsciously.helper.showToast
import app.subconsciously.helper.uninstall
import java.util.Calendar
import java.util.Collections
import java.util.Random

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }

        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
    }

    private fun initViews() {
        binding.search.onActionViewExpanded()
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (Constants.isHomeAppFlag(flag) || flag in Constants.FLAG_SET_SWIPE_LEFT_APP..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
        try {
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val submittedQuery = query?.trim().orEmpty()
                val exactMatch = adapter.appsList.firstOrNull { app ->
                    submittedQuery == app.appLabel.trim()
                }
                if (exactMatch != null) {
                    val drawerLaunch = flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS
                    viewModel.selectedApp(exactMatch, flag, drawerLaunch)
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.filter.filter(newText)
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    private fun initAdapter() {
        val blockManager = BlockManager(requireContext())
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            blockManager,
            showBlockedDialog = { packageName ->
                BlockedAppSheet.newInstance(packageName).show(childFragmentManager, "blocked")
            },
            appClickListener = { appModel ->
                if (appModel.appPackage.isBlank()) return@AppDrawerAdapter
                val drawerLaunch = flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS
                viewModel.selectedApp(appModel, flag, drawerLaunch)
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
                when (appModel) {
                    is AppModel.PinnedShortcut ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().deletePinnedShortcut(
                                packageName = appModel.appPackage,
                                shortcutIdToDelete = appModel.shortcutId,
                                user = appModel.user,
                            )
                        }

                    is AppModel.App -> {
                        requireContext().apply {
                            if (isSystemApp(appModel.appPackage, appModel.user))
                                showToast(getString(R.string.system_app_cannot_delete))
                            else
                                uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel, position ->
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // for backward compatibility
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.shortcutId
                    is AppModel.App -> appModel.appPackage
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            }
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
    }

    private fun initObservers() {
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                it?.let { appModels ->
                    val listToShow = appModels.toMutableList()
                    if (flag == Constants.FLAG_LAUNCH_APP) {
                        val today = Calendar.getInstance().run {
                            get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
                        }
                        Collections.shuffle(listToShow, Random(today.toLong()))
                    }
                    if (Constants.isHomeAppFlag(flag)) {
                        val distractionList = DistractionList(requireContext())
                        listToShow.removeAll { app ->
                            app is AppModel.App && distractionList.isDistraction(app.appPackage)
                        }
                    }
                    adapter.setAppList(listToShow)
                    adapter.filter.filter(binding.search.query)
                }
            }
        }
        viewModel.drawerNavHint.observe(viewLifecycleOwner) { hint ->
            applyDrawerNavHint(hint)
        }
    }

    private fun applyDrawerNavHint(hint: DrawerNavHint) {
        if (hint.refreshAppList) viewModel.getAppList()
        if (hint.refreshHiddenApps) viewModel.getHiddenApps()
        val pkg = hint.blockedPackage
        if (pkg != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                BlockedAppSheet.newInstance(pkg).show(childFragmentManager, "blocked")
            }
        }
        when {
            hint.stayOnDrawer -> { /* reflection or blocked: keep drawer open */ }
            hint.popToMain -> findNavController().popBackStack(R.id.mainFragment, false)
            hint.popOnce -> findNavController().popBackStack()
        }
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            val homeAppIndex = when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> 1
                Constants.FLAG_SET_HOME_APP_2 -> 2
                Constants.FLAG_SET_HOME_APP_3 -> 3
                Constants.FLAG_SET_HOME_APP_4 -> 4
                Constants.FLAG_SET_HOME_APP_5 -> 5
                Constants.FLAG_SET_HOME_APP_6 -> 6
                Constants.FLAG_SET_HOME_APP_7 -> 7
                Constants.FLAG_SET_HOME_APP_8 -> 8
                Constants.FLAG_SET_HOME_APP_9 -> 9
                Constants.FLAG_SET_HOME_APP_10 -> 10
                Constants.FLAG_SET_HOME_APP_11 -> 11
                Constants.FLAG_SET_HOME_APP_12 -> 12
                else -> -1
            }
            if (homeAppIndex != -1) {
                prefs.setAppName(homeAppIndex, name)
            }
            binding.search.hideKeyboard()
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            binding.search.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            if (!onTop && isRemoving.not())
                                binding.search.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 10 && binding.search.hasFocus()) {
                    binding.search.hideKeyboard()
                    binding.search.clearFocus()
                }
            }
        }
    }

    private fun checkMessageAndExit() {
        binding.search.hideKeyboard()
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
