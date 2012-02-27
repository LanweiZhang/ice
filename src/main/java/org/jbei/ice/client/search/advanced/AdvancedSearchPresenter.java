package org.jbei.ice.client.search.advanced;

import java.util.ArrayList;

import org.jbei.ice.client.AppController;
import org.jbei.ice.client.RegistryServiceAsync;
import org.jbei.ice.client.common.EntryDataViewDataProvider;
import org.jbei.ice.client.event.SearchEvent;
import org.jbei.ice.client.event.SearchEventHandler;
import org.jbei.ice.client.search.blast.BlastSearchDataProvider;
import org.jbei.ice.client.util.Utils;
import org.jbei.ice.shared.QueryOperator;
import org.jbei.ice.shared.dto.BlastResultInfo;
import org.jbei.ice.shared.dto.SearchFilterInfo;

import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class AdvancedSearchPresenter {

    private final RegistryServiceAsync rpcService;
    private final HandlerManager eventBus;
    private final AdvancedSearchView display;
    private final EntryDataViewDataProvider dataProvider;
    private final BlastSearchDataProvider blastProvider;

    public AdvancedSearchPresenter(RegistryServiceAsync rpcService, HandlerManager eventBus) {
        this.rpcService = rpcService;
        this.eventBus = eventBus;
        this.display = new AdvancedSearchView();

        // hide the results table
        dataProvider = new AdvancedSearchDataProvider(display.getResultsTable(), rpcService);
        blastProvider = new BlastSearchDataProvider(display.getBlastResultTable(),
                new ArrayList<BlastResultInfo>(), rpcService);

        // register for search events
        eventBus.addHandler(SearchEvent.TYPE, new SearchEventHandler() {

            @Override
            public void onSearch(SearchEvent event) {
                search(event.getFilters());
            }
        });
    }

    public AdvancedSearchPresenter(final RegistryServiceAsync rpcService,
            final HandlerManager eventBus, ArrayList<SearchFilterInfo> operands) {
        this(rpcService, eventBus);
        search(operands);
    }

    protected void search(final ArrayList<SearchFilterInfo> searchFilters) {
        if (searchFilters == null)
            return;

        // currently support only a single blast search with filters
        // search for blast operator
        SearchFilterInfo blastInfo = null;
        for (SearchFilterInfo filter : searchFilters) {
            QueryOperator operator = QueryOperator.operatorValueOf(filter.getOperator());
            if (operator == null)
                continue;

            if (operator == QueryOperator.TBLAST_X || operator == QueryOperator.BLAST_N) {
                if (searchFilters.remove(filter)) {
                    blastInfo = filter;
                }
                break;
            }
        }

        // TODO : move to model

        if (blastInfo != null) {

            // show blast table loading
            blastProvider.updateRowCount(0, false);
            display.setBlastVisibility(true);
            display.getBlastResultTable().setVisibleRangeAndClearData(
                display.getBlastResultTable().getVisibleRange(), false);

            // get blast results and filter 
            QueryOperator program = QueryOperator.operatorValueOf(blastInfo.getOperator());
            rpcService.blastSearch(AppController.sessionId, blastInfo.getOperand(), program,
                new AsyncCallback<ArrayList<BlastResultInfo>>() {

                    @Override
                    public void onSuccess(final ArrayList<BlastResultInfo> blastResult) {
                        if (searchFilters.isEmpty()) {
                            blastProvider.setData(blastResult);

                        } else {

                            // retrieve other filters
                            rpcService.retrieveSearchResults(AppController.sessionId,
                                searchFilters, new AsyncCallback<ArrayList<Long>>() {

                                    @Override
                                    public void onSuccess(ArrayList<Long> result) {
                                        if (result == null) {
                                            display.setBlastVisibility(false);
                                            reset();
                                            return;
                                        }

                                        // TODO : performance
                                        // TODO : push to server for filtering. this search can return a very long list
                                        ArrayList<BlastResultInfo> toRemove = new ArrayList<BlastResultInfo>();

                                        for (BlastResultInfo info : blastResult) {
                                            long entryId = info.getEntryInfo().getId();
                                            if (!result.contains(entryId)) {
                                                toRemove.add(info);
                                            }
                                        }

                                        blastResult.removeAll(toRemove);
                                        display.setSearchFilters(searchFilters);
                                        blastProvider.setData(blastResult);
                                        reset();
                                    }

                                    @Override
                                    public void onFailure(Throwable caught) {
                                        Window.alert("Call failed: " + caught.getMessage());
                                        display.setBlastVisibility(false);
                                        blastProvider.reset();
                                        reset();
                                    }

                                    public void reset() {
                                        Utils.showDefaultCursor(null);
                                    }
                                });
                        }
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                        display.setBlastVisibility(false);
                        // TODO proper error handler
                        Window.alert("Could not retrieve blast results");
                    }
                });
        } else {
            display.setSearchVisibility(true);
            display.getResultsTable().setVisibleRangeAndClearData(
                display.getResultsTable().getVisibleRange(), false);

            rpcService.retrieveSearchResults(AppController.sessionId, searchFilters,
                new AsyncCallback<ArrayList<Long>>() {

                    @Override
                    public void onSuccess(ArrayList<Long> result) {
                        display.setSearchFilters(searchFilters);
                        dataProvider.setValues(result);
                        reset();
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                        Window.alert("Call failed: " + caught.getMessage());
                        display.setSearchVisibility(false);
                        dataProvider.reset();
                    }

                    public void reset() {
                        Utils.showDefaultCursor(null);
                    }
                });
        }
    }

    public AdvancedSearchView getView() {
        return this.display;
    }
}
