package com.faltenreich.diaguard.feature.log;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.faltenreich.diaguard.R;
import com.faltenreich.diaguard.databinding.FragmentLogBinding;
import com.faltenreich.diaguard.feature.entry.edit.EntryEditFragmentFactory;
import com.faltenreich.diaguard.feature.entry.search.EntrySearchActivity;
import com.faltenreich.diaguard.feature.log.empty.LogEmptyListItem;
import com.faltenreich.diaguard.feature.log.entry.LogEntryListItem;
import com.faltenreich.diaguard.feature.navigation.Navigation;
import com.faltenreich.diaguard.feature.navigation.ToolbarProperties;
import com.faltenreich.diaguard.shared.data.database.dao.EntryDao;
import com.faltenreich.diaguard.shared.data.database.entity.Entry;
import com.faltenreich.diaguard.shared.data.database.entity.EntryTag;
import com.faltenreich.diaguard.shared.data.database.entity.FoodEaten;
import com.faltenreich.diaguard.shared.data.database.entity.Tag;
import com.faltenreich.diaguard.shared.event.data.EntryAddedEvent;
import com.faltenreich.diaguard.shared.event.data.EntryDeletedEvent;
import com.faltenreich.diaguard.shared.event.data.EntryUpdatedEvent;
import com.faltenreich.diaguard.shared.event.file.BackupImportedEvent;
import com.faltenreich.diaguard.shared.event.preference.CategoryPreferenceChangedEvent;
import com.faltenreich.diaguard.shared.event.preference.UnitChangedEvent;
import com.faltenreich.diaguard.shared.view.ViewUtils;
import com.faltenreich.diaguard.shared.view.fragment.DateFragment;
import com.faltenreich.diaguard.shared.view.recyclerview.decoration.StickyHeaderDecoration;
import com.faltenreich.diaguard.shared.view.recyclerview.layoutmanager.SafeLinearLayoutManager;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.joda.time.DateTime;

import java.util.List;

/**
 * Created by Filip on 05.07.2015.
 */
public class LogFragment extends DateFragment<FragmentLogBinding> implements LogListAdapter.Listener {

    private LogListAdapter listAdapter;
    private StickyHeaderDecoration listDecoration;
    private LinearLayoutManager listLayoutManager;

    public LogFragment() {
        super(R.layout.fragment_log);
    }

    @Override
    protected FragmentLogBinding createBinding(LayoutInflater layoutInflater) {
        return FragmentLogBinding.inflate(layoutInflater);
    }

    @Override
    public ToolbarProperties getToolbarProperties() {
        boolean isLargeTitle = ViewUtils.isLandscape(getActivity()) || ViewUtils.isLargeScreen(getActivity());
        String format = isLargeTitle ? "MMMM YYYY" : "MMM YYYY";
        String title = getDay().toString(format);
        return new ToolbarProperties.Builder()
            .setTitle(title)
            .setMenu(R.menu.log)
            .build();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initLayout();

        // Fake delay for smoother fragment transitions
        new Handler().postDelayed(() -> goToDay(getDay()), 350);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_today) {
            goToDay(DateTime.now());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initLayout() {
        RecyclerView listView = getBinding().listView;
        listLayoutManager = new SafeLinearLayoutManager(getActivity());
        listView.setLayoutManager(listLayoutManager);
        listAdapter = new LogListAdapter(getActivity(), this);
        listView.setAdapter(listAdapter);
        listDecoration = new StickyHeaderDecoration(listAdapter, true);
        listView.addItemDecoration(listDecoration);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new LogSwipeCallback(listAdapter));
        itemTouchHelper.attachToRecyclerView(listView);

        // Fragment updates
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                DateTime firstVisibleDay = getFirstVisibleDay();
                if (firstVisibleDay != null) {
                    setDay(firstVisibleDay);
                    // Update month in Toolbar when section is being crossed
                    boolean isScrollingUp = dy < 0;
                    boolean isCrossingMonth = isScrollingUp ?
                            getDay().dayOfMonth().get() == getDay().dayOfMonth().getMaximumValue() :
                            getDay().dayOfMonth().get() == getDay().dayOfMonth().getMinimumValue();
                    if (isCrossingMonth) {
                        updateLabels();
                    }
                }
            }
        });
    }

    private DateTime getFirstVisibleDay() {
        int firstVisibleItemPosition = listLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition >= 0 && firstVisibleItemPosition < listAdapter.getItemCount()) {
            LogListItem item = listAdapter.getItem(listLayoutManager.findFirstVisibleItemPosition());
            return item.getDateTime();
        } else {
            return null;
        }
    }

    @Override
    protected void goToDay(DateTime dateTime) {
        super.goToDay(dateTime);

        int position = listAdapter.getDayPosition(dateTime);
        if (position >= 0) {
            getBinding().listView.scrollToPosition(position);
        } else {
            getBinding().progressIndicator.setVisibility(View.VISIBLE);
            listAdapter.setup(dateTime);
        }
    }

    @Override
    public void onOrderChanges() {
        invalidateSections();
    }

    @Override
    public void onSetupEnd() {
        getBinding().progressIndicator.setVisibility(View.GONE);
        goToDay(getDay());
    }

    @Override
    public void onEntrySelected(Entry entry) {
        openFragment(EntryEditFragmentFactory.newInstance(entry), Navigation.Operation.REPLACE, true);
    }

    @Override
    public void onTagSelected(Tag tag, View view) {
        if (isAdded()) {
            Intent intent = EntrySearchActivity.newInstance(getContext(), tag);
            startActivity(intent);
        }
    }

    @Override
    public void onDateSelected(DateTime dateTime) {
        openFragment(EntryEditFragmentFactory.newInstance(dateTime), Navigation.Operation.REPLACE, true);
    }

    private void invalidateSections() {
        if (isAdded() && listDecoration != null) {
            listDecoration.clearHeaderCache();
        }
    }

    private void updateHeaderSection(DateTime dateTime) {
        int position = listAdapter.getFirstListItemEntryOfDayPosition(dateTime);
        if (position >= 0) {
            LogEntryListItem firstListItemEntry = (LogEntryListItem) listAdapter.getItem(position);
            while (listAdapter.getItem(position).getDateTime().withTimeAtStartOfDay().isEqual(dateTime.withTimeAtStartOfDay()) &&
                    listAdapter.getItem(position) instanceof LogEntryListItem) {
                LogEntryListItem listItem = (LogEntryListItem) listAdapter.getItem(position);
                listItem.setFirstListItemEntryOfDay(firstListItemEntry);
                position++;
            }
        }
        invalidateSections();
    }

    private void addEntry(Entry entry, List<EntryTag> entryTags, List<FoodEaten> foodEatenList) {
        if (entry != null) {
            DateTime date = entry.getDate();
            int position = listAdapter.getNextDateTimePosition(date);
            if (position >= 0) {

                // Remove any existing empty view
                int previousPosition = position - 1;
                LogListItem previousListItem = listAdapter.getItem(previousPosition);
                if (previousListItem instanceof LogEmptyListItem && previousListItem.getDateTime().getDayOfYear() == date.getDayOfYear()) {
                    listAdapter.removeItem(previousPosition);
                    listAdapter.notifyItemRemoved(previousPosition);
                    position = previousPosition;
                }

                entry.setMeasurementCache(EntryDao.getInstance().getMeasurements(entry));

                LogEntryListItem listItemEntry = new LogEntryListItem(entry, entryTags, foodEatenList);
                listAdapter.addItem(position, listItemEntry);
                listAdapter.notifyItemInserted(position);

                updateHeaderSection(date);
            }
        }
    }

    private void removeEntry(Entry entry) {
        if (entry != null) {
            int position = listAdapter.getEntryPosition(entry);
            if (position >= 0) {
                removeEntry(position, entry.getDate());
            }
        }
    }

    private void removeEntry(int position, DateTime date) {
        listAdapter.removeItem(position);

        // Add empty view if there is no entry available anymore for this day
        boolean hasNoMoreEntries = listAdapter.getFirstListItemEntryOfDayPosition(date) == -1;
        if (hasNoMoreEntries) {
            listAdapter.addItem(position, new LogEmptyListItem(date));
            listAdapter.notifyItemChanged(position);
        } else {
            listAdapter.notifyItemRemoved(position);
        }

        updateHeaderSection(date);
    }

    private void updateEntry(Entry entry, List<EntryTag> entryTags, List<FoodEaten> foodEatenList, DateTime originalDate) {
        if (entry != null) {
            int originalPosition = listAdapter.getEntryPosition(entry);
            if (originalPosition >= 0) {
                int updatedPosition = listAdapter.getNextDateTimePosition(entry.getDate()) - 1;
                if (originalPosition == updatedPosition) {
                    Object listItem = listAdapter.getItem(originalPosition);
                    if (listItem instanceof LogEntryListItem) {
                        LogEntryListItem listItemEntry = (LogEntryListItem) listItem;
                        listItemEntry.setEntry(entry);
                        listItemEntry.setEntryTags(entryTags);
                        listItemEntry.setFoodEatenList(foodEatenList);
                        listAdapter.notifyItemChanged(originalPosition);
                    }
                } else {
                    removeEntry(originalPosition, originalDate);
                    addEntry(entry, entryTags, foodEatenList);
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EntryAddedEvent event) {
        if (isAdded()) {
            addEntry(event.context, event.entryTags, event.foodEatenList);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EntryDeletedEvent event) {
        super.onEvent(event);
        if (isAdded()) {
            removeEntry(event.context);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EntryUpdatedEvent event) {
        if (isAdded()) {
            updateEntry(event.context, event.entryTags, event.foodEatenList, event.originalDate);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@SuppressWarnings("unused") UnitChangedEvent event) {
        if (isAdded()) {
            getBinding().progressIndicator.setVisibility(View.VISIBLE);
            listAdapter.setup(getDay());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@SuppressWarnings("unused") BackupImportedEvent event) {
        if (isAdded()) {
            getBinding().progressIndicator.setVisibility(View.VISIBLE);
            listAdapter.setup(getDay());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(@SuppressWarnings("unused") CategoryPreferenceChangedEvent event) {
        if (isAdded()) {
            getBinding().progressIndicator.setVisibility(View.VISIBLE);
            listAdapter.setup(getDay());
        }
    }
}
