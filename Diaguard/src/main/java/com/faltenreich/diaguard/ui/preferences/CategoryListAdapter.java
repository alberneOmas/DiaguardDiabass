package com.faltenreich.diaguard.ui.preferences;

import android.content.Context;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import android.widget.ArrayAdapter;

/**
 * Created by Faltenreich on 16.03.2017
 */

class CategoryListAdapter extends ArrayAdapter<CharSequence> {

    CategoryListAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull CharSequence[] objects) {
        super(context, resource, objects);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        // Blood sugar must not be disabled
        return position != 0;
    }
}