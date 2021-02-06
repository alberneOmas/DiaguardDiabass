package com.faltenreich.diaguard.feature.entry.edit.measurement;

import android.content.Context;
import android.view.LayoutInflater;

import com.faltenreich.diaguard.R;
import com.faltenreich.diaguard.databinding.ListItemMeasurementInsulinBinding;
import com.faltenreich.diaguard.feature.preference.data.PreferenceStore;
import com.faltenreich.diaguard.shared.data.database.entity.Category;
import com.faltenreich.diaguard.shared.data.database.entity.Insulin;
import com.faltenreich.diaguard.shared.data.database.entity.Measurement;
import com.faltenreich.diaguard.shared.data.primitive.FloatUtils;
import com.faltenreich.diaguard.shared.data.primitive.StringUtils;
import com.faltenreich.diaguard.shared.view.edittext.StickyHintInputView;

/**
 * Created by Faltenreich on 20.09.2015.
 */
public class MeasurementInsulinView extends MeasurementAbstractView<ListItemMeasurementInsulinBinding, Insulin> {

    private StickyHintInputView bolusInputField;
    private StickyHintInputView correctionInputField;
    private StickyHintInputView basalInputField;

    public MeasurementInsulinView(Context context) {
        super(context, Category.INSULIN);
    }

    public MeasurementInsulinView(Context context, Insulin insulin) {
        super(context, insulin);
    }

    @Override
    protected ListItemMeasurementInsulinBinding createBinding(LayoutInflater inflater) {
        return ListItemMeasurementInsulinBinding.inflate(inflater, this, true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        bolusInputField = getBinding().bolusInputField;
        bolusInputField.setText(measurement.getValuesForUI()[0]);

        correctionInputField = getBinding().correctionInputField;
        correctionInputField.setText(measurement.getValuesForUI()[1]);

        basalInputField = getBinding().basalInputField;
        basalInputField.setText(measurement.getValuesForUI()[2]);
    }

    @Override
    protected boolean isValid() {
        boolean isValid = true;

        String bolus = bolusInputField.getText().trim();
        String correction = correctionInputField.getText().trim();
        String basal = basalInputField.getText().trim();

        if (StringUtils.isBlank(bolus) && StringUtils.isBlank(correction) && StringUtils.isBlank(basal)) {
            bolusInputField.setError(getContext().getString(R.string.validator_value_empty));
            isValid = false;
        } else {
            if (!StringUtils.isBlank(bolus)) {
                isValid = PreferenceStore.getInstance().isValueValid(bolusInputField.getEditText(), Category.INSULIN);
            }
            if (!StringUtils.isBlank(correction)) {
                isValid = PreferenceStore.getInstance().isValueValid(correctionInputField.getEditText(), Category.INSULIN, true);
            }
            if (!StringUtils.isBlank(basal)) {
                isValid = PreferenceStore.getInstance().isValueValid(basalInputField.getEditText(), Category.INSULIN);
            }
        }
        return isValid;
    }

    @Override
    public Measurement getMeasurement() {
        if (isValid()) {
            measurement.setValues(
                    bolusInputField.getText().length() > 0 ?
                            PreferenceStore.getInstance().formatCustomToDefaultUnit(
                                    measurement.getCategory(),
                                    FloatUtils.parseNumber(bolusInputField.getText())) : 0,
                    correctionInputField.getText().length() > 0 ?
                            PreferenceStore.getInstance().formatCustomToDefaultUnit(
                                    measurement.getCategory(),
                                    FloatUtils.parseNumber(correctionInputField.getText())) : 0,
                    basalInputField.getText().length() > 0 ?
                            PreferenceStore.getInstance().formatCustomToDefaultUnit(
                                    measurement.getCategory(),
                                    FloatUtils.parseNumber(basalInputField.getText())) : 0);
            return measurement;
        } else {
            return null;
        }
    }
}