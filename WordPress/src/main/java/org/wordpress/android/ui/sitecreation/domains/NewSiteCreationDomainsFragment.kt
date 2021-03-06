package org.wordpress.android.ui.sitecreation.domains

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.annotation.LayoutRes
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.ui.accounts.HelpActivity
import org.wordpress.android.ui.sitecreation.NewSiteCreationBaseFormFragment
import org.wordpress.android.ui.sitecreation.domains.NewSiteCreationDomainsViewModel.DomainsUiState.DomainsUiContentState
import org.wordpress.android.ui.sitecreation.misc.OnHelpClickedListener
import org.wordpress.android.ui.sitecreation.misc.SearchInputWithHeader
import org.wordpress.android.ui.utils.UiHelpers
import javax.inject.Inject

private const val KEY_LIST_STATE = "list_state"

class NewSiteCreationDomainsFragment : NewSiteCreationBaseFormFragment() {
    private lateinit var nonNullActivity: FragmentActivity
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var searchInputWithHeader: SearchInputWithHeader
    private lateinit var emptyView: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var createSiteButtonContainer: View
    private lateinit var viewModel: NewSiteCreationDomainsViewModel

    private lateinit var domainsScreenListener: DomainsScreenListener
    private lateinit var helpClickedListener: OnHelpClickedListener

    @Inject internal lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject internal lateinit var uiHelpers: UiHelpers

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context !is DomainsScreenListener) {
            throw IllegalStateException("Parent activity must implement DomainsScreenListener.")
        }
        if (context !is OnHelpClickedListener) {
            throw IllegalStateException("Parent activity must implement OnHelpClickedListener.")
        }
        domainsScreenListener = context
        helpClickedListener = context
    }

    @LayoutRes
    override fun getContentLayout(): Int {
        return R.layout.new_site_creation_domains_screen
    }

    override fun setupContent(rootView: ViewGroup) {
        searchInputWithHeader = SearchInputWithHeader(
                uiHelpers = uiHelpers,
                rootView = rootView,
                onClear = { viewModel.onClearTextBtnClicked() }
        )
        emptyView = rootView.findViewById(R.id.domain_list_empty_view)
        createSiteButtonContainer = rootView.findViewById(R.id.create_site_button_container)
        rootView.findViewById<AppCompatButton>(R.id.create_site_button).setOnClickListener {
            viewModel.createSiteBtnClicked()
        }
        initRecyclerView(rootView)
        initViewModel()
    }

    override fun getScreenTitle(): String {
        return arguments?.getString(EXTRA_SCREEN_TITLE)
                ?: throw IllegalStateException("Required argument screen title is missing.")
    }

    override fun onHelp() {
        viewModel.onHelpClicked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nonNullActivity = activity!!
        (nonNullActivity.application as WordPress).component().inject(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_LIST_STATE, linearLayoutManager.onSaveInstanceState())
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.getParcelable<Parcelable>(KEY_LIST_STATE)?.let {
            linearLayoutManager.onRestoreInstanceState(it)
        }
        // we need to set the `onTextChanged` after the viewState has been restored otherwise the viewModel.updateQuery
        // is called when the system sets the restored value to the EditText which results in an unnecessary request
        searchInputWithHeader.onTextChanged = { viewModel.updateQuery(it) }
    }

    private fun initRecyclerView(rootView: ViewGroup) {
        recyclerView = rootView.findViewById(R.id.recycler_view)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        linearLayoutManager = layoutManager
        recyclerView.layoutManager = linearLayoutManager
        initAdapter()
    }

    private fun initAdapter() {
        val adapter = NewSiteCreationDomainsAdapter()
        recyclerView.adapter = adapter
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this, viewModelFactory)
                .get(NewSiteCreationDomainsViewModel::class.java)

        viewModel.uiState.observe(this, Observer { uiState ->
            uiState?.let {
                searchInputWithHeader.updateHeader(nonNullActivity, uiState.headerUiState)
                searchInputWithHeader.updateSearchInput(nonNullActivity, uiState.searchInputUiState)
                updateContentUiState(uiState.contentState)
                uiHelpers.updateVisibility(createSiteButtonContainer, uiState.createSiteButtonContainerVisibility)
            }
        })
        viewModel.clearBtnClicked.observe(this, Observer {
            searchInputWithHeader.setInputText("")
        })
        viewModel.createSiteBtnClicked.observe(this, Observer { domain ->
            domain?.let { domainsScreenListener.onDomainSelected(domain) }
        })
        viewModel.onHelpClicked.observe(this, Observer {
            helpClickedListener.onHelpClicked(HelpActivity.Origin.NEW_SITE_CREATION_DOMAINS)
        })
        viewModel.start(getSiteTitleFromArguments())
    }

    private fun updateContentUiState(contentState: DomainsUiContentState) {
        uiHelpers.updateVisibility(emptyView, contentState.emptyViewVisibility)
        if (contentState.items.isNotEmpty()) {
            view?.announceForAccessibility(getString(R.string.suggestions_updated_content_description))
        }
        (recyclerView.adapter as NewSiteCreationDomainsAdapter).update(contentState.items)
    }

    private fun getSiteTitleFromArguments(): String? {
        return arguments?.getString(EXTRA_SITE_TITLE)
    }

    companion object {
        const val TAG = "site_creation_domains_fragment_tag"
        const val EXTRA_SITE_TITLE = "extra_site_title"

        fun newInstance(screenTitle: String, siteTitle: String?): NewSiteCreationDomainsFragment {
            val fragment = NewSiteCreationDomainsFragment()
            val bundle = Bundle()
            bundle.putString(NewSiteCreationBaseFormFragment.EXTRA_SCREEN_TITLE, screenTitle)
            siteTitle?.let { bundle.putString(EXTRA_SITE_TITLE, siteTitle) }
            fragment.arguments = bundle
            return fragment
        }
    }
}
