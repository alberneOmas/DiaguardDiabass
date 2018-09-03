package com.faltenreich.diaguard.ui.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.faltenreich.diaguard.R;
import com.faltenreich.diaguard.adapter.LinearDividerItemDecoration;
import com.faltenreich.diaguard.adapter.TagListAdapter;
import com.faltenreich.diaguard.data.dao.EntryTagDao;
import com.faltenreich.diaguard.data.dao.TagDao;
import com.faltenreich.diaguard.data.entity.Tag;
import com.faltenreich.diaguard.ui.activity.EntrySearchActivity;

import java.util.List;

import butterknife.BindView;

public class TagsFragment extends BaseFragment implements TagListAdapter.TagListener {

    @BindView(R.id.list) RecyclerView list;
    @BindView(R.id.list_placeholder) View placeholder;

    private TagListAdapter listAdapter;

    public TagsFragment() {
        super(R.layout.fragment_list, R.string.tags, -1);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initLayout();
        invalidateLayout();
        loadTags();
    }

    private void initLayout() {
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.addItemDecoration(new LinearDividerItemDecoration(getContext()));
        listAdapter = new TagListAdapter(getContext());
        listAdapter.setTagListener(this);
        list.setAdapter(listAdapter);
    }

    private void invalidateLayout() {
        boolean isEmpty = listAdapter == null || listAdapter.getItemCount() == 0;
        placeholder.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void loadTags() {
        // TODO: Make asynchronous
        List<Tag> tags = TagDao.getInstance().getAll();
        setTags(tags);
    }

    private void setTags(List<Tag> tags) {
        listAdapter.clear();
        listAdapter.addItems(tags);
        listAdapter.notifyDataSetChanged();
        invalidateLayout();
    }

    private void deleteTag(Tag tag) {
        // TODO: Delete tag
        listAdapter.notifyDataSetChanged();
        invalidateLayout();
    }

    @Override
    public void onTagSelected(Tag tag, View view) {
        EntrySearchActivity.show(getContext(), tag, view);
    }

    @Override
    public void onTagDeleted(final Tag tag, View view) {
        long entryTagCount = EntryTagDao.getInstance().count(tag);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.tag_delete)
                .setMessage(String.format(getString(R.string.tag_delete_confirmation), entryTagCount))
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteTag(tag);
                    }
                })
                .create()
                .show();
    }
}
