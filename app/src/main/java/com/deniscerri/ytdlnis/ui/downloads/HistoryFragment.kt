package com.deniscerri.ytdlnis.ui.downloads

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.view.View.*
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.HistoryAdapter
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.repository.HistoryRepository
import com.deniscerri.ytdlnis.database.repository.HistoryRepository.HistorySort
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.databinding.FragmentHistoryBinding
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * A fragment representing a list of Items.
 */
class HistoryFragment : Fragment(), HistoryAdapter.OnItemClickListener{
    private lateinit var historyViewModel : HistoryViewModel
    private lateinit var downloadViewModel : DownloadViewModel

    private var fragmentView: View? = null
    private var activity: Activity? = null
    private var mainActivity: MainActivity? = null
    private var fragmentContext: Context? = null
    private var layoutinflater: LayoutInflater? = null
    private var shimmerCards: ShimmerFrameLayout? = null
    private var topAppBar: MaterialToolbar? = null
    private var recyclerView: RecyclerView? = null
    private var historyAdapter: HistoryAdapter? = null
    private var bottomSheet: BottomSheetDialog? = null
    private var sortSheet: BottomSheetDialog? = null
    private var uiHandler: Handler? = null
    private var noResults: RelativeLayout? = null
    private var selectionChips: LinearLayout? = null
    private var websiteGroup: ChipGroup? = null
    private var historyList: List<HistoryItem?>? = null
    private var allhistoryList: List<HistoryItem?>? = null
    private var selectedObjects: ArrayList<HistoryItem>? = null
    private var deleteFab: ExtendedFloatingActionButton? = null
    private var fileUtil: FileUtil? = null
    private var uiUtil: UiUtil? = null
    private var _binding : FragmentHistoryBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        fragmentView = inflater.inflate(R.layout.fragment_history, container, false)
        activity = getActivity()
        mainActivity = activity as MainActivity?

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentContext = context
        layoutinflater = LayoutInflater.from(context)
        shimmerCards = view.findViewById(R.id.shimmer_history_framelayout)
        topAppBar = view.findViewById(R.id.history_toolbar)
        noResults = view.findViewById(R.id.no_results)
        selectionChips = view.findViewById(R.id.history_selection_chips)
        websiteGroup = view.findViewById(R.id.website_chip_group)
        deleteFab = view.findViewById(R.id.delete_selected_fab)
        fileUtil = FileUtil()
        uiUtil = UiUtil(FileUtil())
        deleteFab?.tag = "deleteSelected"
        deleteFab?.setOnClickListener{
            removeSelectedItems()
        }
        uiHandler = Handler(Looper.getMainLooper())
        selectedObjects = ArrayList()


        historyList = mutableListOf()
        allhistoryList = mutableListOf()

        historyAdapter =
            HistoryAdapter(
                this,
                requireActivity()
            )
        recyclerView = view.findViewById(R.id.recyclerviewhistorys)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || resources.getBoolean(R.bool.isTablet)){
            recyclerView?.layoutManager = GridLayoutManager(context, 2)
        }else{
            recyclerView?.layoutManager = LinearLayoutManager(context)
        }
        recyclerView?.adapter = historyAdapter

        noResults?.visibility = GONE
        selectionChips?.visibility = VISIBLE
        shimmerCards?.visibility = GONE


        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        historyViewModel.allItems.observe(viewLifecycleOwner) {
            allhistoryList = it
            if(it.isEmpty()){
                noResults!!.visibility = VISIBLE
                selectionChips!!.visibility = GONE
                websiteGroup!!.removeAllViews()
            }else{
                noResults!!.visibility = GONE
                selectionChips!!.visibility = VISIBLE
                updateWebsiteChips(it)
            }
        }

        historyViewModel.getFilteredList().observe(viewLifecycleOwner) {
            historyAdapter!!.submitList(it)
            historyList = it
            scrollToTop()
        }

        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        initMenu()
        initChips()
    }

    private fun scrollToTop() {
        recyclerView!!.scrollToPosition(0)
        Handler(Looper.getMainLooper()).post {
            (topAppBar!!.parent as AppBarLayout).setExpanded(
                true,
                true
            )
        }
    }

    private fun initMenu() {
        val onActionExpandListener: MenuItem.OnActionExpandListener =
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                    return true
                }

                override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                    return true
                }
            }
        topAppBar!!.menu.findItem(R.id.search_history)
            .setOnActionExpandListener(onActionExpandListener)
        val searchView = topAppBar!!.menu.findItem(R.id.search_history).actionView as SearchView?
        searchView!!.inputType = InputType.TYPE_CLASS_TEXT
        searchView.queryHint = getString(R.string.search_history_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                topAppBar!!.menu.findItem(R.id.search_history).collapseActionView()
                historyViewModel.setQueryFilter(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                historyViewModel.setQueryFilter(newText)
                return true
            }
        })
        topAppBar!!.setOnClickListener { scrollToTop() }
        topAppBar!!.setOnMenuItemClickListener { m: MenuItem ->
            when (m.itemId) {
                R.id.remove_history -> {
                    if(allhistoryList!!.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                        deleteDialog.setMessage(getString(R.string.confirm_delete_history_desc))
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.deleteAll()
                        }
                        deleteDialog.show()
                    }
                }
                R.id.remove_deleted_history -> {
                    if(allhistoryList!!.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                        deleteDialog.setMessage(getString(R.string.confirm_delete_history_desc))
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.clearDeleted()
                        }
                        deleteDialog.show()
                    }
                }
                R.id.remove_duplicates -> {
                    if(allhistoryList!!.isEmpty()){
                        Toast.makeText(context, R.string.history_is_empty, Toast.LENGTH_SHORT).show()
                    }else{
                        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
                        deleteDialog.setTitle(getString(R.string.confirm_delete_history))
                        deleteDialog.setMessage(getString(R.string.confirm_delete_history_desc))
                        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                            historyViewModel.deleteDuplicates()
                        }
                        deleteDialog.show()
                    }
                }
            }
            true
        }
    }

    private fun changeSortIcon(item: TextView, order: HistorySort){
        when(order){
            HistorySort.DESC ->{
                item.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_up, 0,0,0)
            }
            HistorySort.ASC ->                 {
                item.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_down, 0,0,0)
            }
        }
    }


    private fun initChips() {
        val sortChip = fragmentView!!.findViewById<Chip>(R.id.sortChip)
        sortChip.setOnClickListener {
            sortSheet = BottomSheetDialog(requireContext())
            sortSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
            sortSheet!!.setContentView(R.layout.history_sort_sheet)

            val date = sortSheet!!.findViewById<TextView>(R.id.date)
            val title = sortSheet!!.findViewById<TextView>(R.id.title)
            val author = sortSheet!!.findViewById<TextView>(R.id.author)
            val filesize = sortSheet!!.findViewById<TextView>(R.id.filesize)

            val sortOptions = listOf(date!!, title!!, author!!, filesize!!)
            sortOptions.forEach { it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.empty,0,0,0) }
            when(historyViewModel.sortType.value!!) {
                HistoryRepository.HistorySortType.DATE -> changeSortIcon(date, historyViewModel.sortOrder.value!!)
                HistoryRepository.HistorySortType.TITLE -> changeSortIcon(title, historyViewModel.sortOrder.value!!)
                HistoryRepository.HistorySortType.AUTHOR -> changeSortIcon(author, historyViewModel.sortOrder.value!!)
                HistoryRepository.HistorySortType.FILESIZE -> changeSortIcon(filesize, historyViewModel.sortOrder.value!!)
            }

            date.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.DATE)
                changeSortIcon(date, historyViewModel.sortOrder.value!!)
            }
            title.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.TITLE)
                changeSortIcon(title, historyViewModel.sortOrder.value!!)
            }
            author.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.AUTHOR)
                changeSortIcon(author, historyViewModel.sortOrder.value!!)
            }
            filesize.setOnClickListener {
                sortOptions.forEach { it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.empty,0,0,0) }
                historyViewModel.setSorting(HistoryRepository.HistorySortType.FILESIZE)
                changeSortIcon(filesize, historyViewModel.sortOrder.value!!)
            }
            sortSheet!!.show()
        }

        //format
        val audio = fragmentView!!.findViewById<Chip>(R.id.audio_chip)
        audio.setOnClickListener {
            if (audio.isChecked) {
                historyViewModel.setFormatFilter("audio")
                audio.isChecked = true
            } else {
                historyViewModel.setFormatFilter("")
                audio.isChecked = false
            }
        }
        val video = fragmentView!!.findViewById<Chip>(R.id.video_chip)
        video.setOnClickListener {
            if (video.isChecked) {
                historyViewModel.setFormatFilter("video")
                video.isChecked = true
            } else {
                historyViewModel.setFormatFilter("")
                video.isChecked = false
            }
        }
    }

    private fun updateWebsiteChips(list : List<HistoryItem>) {
        val websites = mutableListOf<String>()
        for (item in list){
            if (!websites.contains(item.website)) websites.add(item.website)
        }
        websiteGroup!!.removeAllViews()
        //val websites = historyRecyclerViewAdapter!!.websites
        for (i in websites.indices) {
            val w = websites[i]
            val tmp = layoutinflater!!.inflate(R.layout.filter_chip, websiteGroup, false) as Chip
            tmp.text = w
            tmp.id = i
            tmp.setOnClickListener {
                Log.e(TAG, tmp.isChecked.toString())
                if (tmp.isChecked) {
                    historyViewModel.setWebsiteFilter(tmp.text as String)
                    tmp.isChecked = true
                } else {
                    historyViewModel.setWebsiteFilter("")
                    tmp.isChecked = false
                }
            }
            websiteGroup!!.addView(tmp)
        }
    }


    private fun removeSelectedItems() {
        if (bottomSheet != null) bottomSheet!!.hide()
        val deleteFile = booleanArrayOf(false)
        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
        deleteDialog.setMultiChoiceItems(
            arrayOf(getString(R.string.delete_files_too)),
            booleanArrayOf(false)
        ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            for (item in selectedObjects!!){
                historyViewModel.delete(item, deleteFile[0])
            }
            selectedObjects = ArrayList()
            historyAdapter!!.clearCheckeditems()
            deleteFab!!.visibility = GONE
        }
        deleteDialog.show()
    }

    private fun removeItem(item: HistoryItem) {
        if (bottomSheet != null) bottomSheet!!.hide()
        val deleteFile = booleanArrayOf(false)
        val deleteDialog = MaterialAlertDialogBuilder(fragmentContext!!)
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        val path = item.downloadPath
        val file = File(path)
        if (file.exists() && path.isNotEmpty()) {
            deleteDialog.setMultiChoiceItems(
                arrayOf(getString(R.string.delete_file_too)),
                booleanArrayOf(false)
            ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
        }
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
              historyViewModel.delete(item, deleteFile[0])
        }
        deleteDialog.show()
    }



    override fun onCardClick(itemID: Long, isPresent: Boolean) {
        bottomSheet = BottomSheetDialog(fragmentContext!!)
        bottomSheet!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet!!.setContentView(R.layout.history_item_details_bottom_sheet)
        val item = findItem(itemID)
        val title = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = item!!.title
        val author = bottomSheet!!.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = item.author

        // BUTTON ----------------------------------
        val buttonLayout = bottomSheet!!.findViewById<LinearLayout>(R.id.downloads_download_button_layout)
        val btn = buttonLayout!!.findViewById<MaterialButton>(R.id.downloads_download_button_type)

        if (item.type == DownloadViewModel.Type.audio) {
            if (isPresent) btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_music_downloaded) else btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_music)
        } else if (item.type == DownloadViewModel.Type.video) {
            if (isPresent) btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_video_downloaded) else btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_video)
        }else{
            btn.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_terminal)
        }

        val time = bottomSheet!!.findViewById<TextView>(R.id.time)
        val formatNote = bottomSheet!!.findViewById<TextView>(R.id.format_note)
        val codec = bottomSheet!!.findViewById<TextView>(R.id.codec)
        val fileSize = bottomSheet!!.findViewById<TextView>(R.id.file_size)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = item.time * 1000L
        time!!.text = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(calendar.time)
        time.isClickable = false

        if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility = GONE
        else formatNote!!.text = item.format.format_note

        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none"){
            codec!!.visibility = GONE
        }else{
            codec!!.visibility = VISIBLE
            codec.text = codecText
        }

        val fileSizeReadable = fileUtil!!.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize!!.visibility = GONE
        else fileSize!!.text = fileSizeReadable

        val link = bottomSheet!!.findViewById<Button>(R.id.bottom_sheet_link)
        val url = item.url
        link!!.text = url
        link.tag = itemID
        link.setOnClickListener{
            uiUtil!!.openLinkIntent(requireContext(), item.url, bottomSheet)
        }
        link.setOnLongClickListener{
            uiUtil!!.copyLinkToClipBoard(requireContext(), item.url, bottomSheet)
            true
        }
        val remove = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = itemID
        remove.setOnClickListener{
            removeItem(item)
        }
        val openFile = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.tag = itemID
        openFile.setOnClickListener{
            uiUtil!!.openFileIntent(requireContext(), item.downloadPath)
        }

        val redownload = bottomSheet!!.findViewById<Button>(R.id.bottomsheet_redownload_button)
        redownload!!.tag = itemID
        redownload.setOnClickListener{
            val downloadItem = downloadViewModel.createDownloadItemFromHistory(item)
            downloadViewModel.queueDownloads(listOf(downloadItem))
            historyViewModel.delete(item, false)
            bottomSheet!!.cancel()
        }

        if (!isPresent) openFile.visibility = GONE
        else redownload.visibility = GONE

        bottomSheet!!.show()
        bottomSheet!!.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onCardSelect(itemID: Long, isChecked: Boolean) {
        val item = findItem(itemID)
        if (isChecked) selectedObjects!!.add(item!!)
        else selectedObjects!!.remove(item)
        if (selectedObjects!!.size > 1) {
            deleteFab!!.visibility = VISIBLE
        } else {
            deleteFab!!.visibility = GONE
        }
    }

    private fun findItem(id : Long): HistoryItem? {
        return historyList?.find { it?.id == id }
    }

    companion object {
        private const val TAG = "historyFragment"
    }
}