package com.faltenreich.diaguard.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.faltenreich.diaguard.R;
import com.faltenreich.diaguard.data.dao.EntryDao;
import com.faltenreich.diaguard.data.dao.FoodDao;
import com.faltenreich.diaguard.data.dao.MeasurementDao;
import com.faltenreich.diaguard.data.dao.TagDao;
import com.faltenreich.diaguard.data.entity.BloodSugar;
import com.faltenreich.diaguard.data.entity.Entry;
import com.faltenreich.diaguard.data.entity.Food;
import com.faltenreich.diaguard.data.entity.Meal;
import com.faltenreich.diaguard.data.entity.Tag;
import com.faltenreich.diaguard.util.Helper;
import com.faltenreich.diaguard.util.NumberUtils;
import com.faltenreich.diaguard.util.export.Export;
import com.opencsv.CSVReader;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ImportHelper {

    private static final String TAG = ImportHelper.class.getSimpleName();
    private static final String FOOD_CSV_FILE_NAME = "food_common.csv";
    private static final String TAGS_CSV_FILE_NAME = "tags.csv";

    public static void createTestData() {
        new CreateTestData().execute();
    }

    public static void validateImports(Context context) {
        Locale locale = Helper.getLocale();
        validateTagImport(context, locale);
        validateFoodImport(context, locale);
    }

    private static void validateFoodImport(Context context, Locale locale) {
        int versionCode = PreferenceHelper.getInstance().getVersionCode();
        if (!PreferenceHelper.getInstance().didImportCommonFood(locale)) {
            new ImportFoodTask(context, locale).execute();
        } else if (versionCode > 0 && versionCode < 19) {
            new UpdateFoodImagesTask(context, locale).execute();
        }
    }

    private static void validateTagImport(Context context, Locale locale) {
        if (!PreferenceHelper.getInstance().didImportTags(locale)) {
            new ImportTagsTask(context, locale).execute();
        }
    }

    private static int getLanguageColumn(String languageCode, String[] row) {
        int languageColumn = 0;
        for (int column = 0; column < 4; column++) {
            String availableLanguageCode = row[column];
            if (languageCode.startsWith(availableLanguageCode.substring(0, 1))) {
                languageColumn = column;
                break;
            }
        }
        return languageColumn;
    }

    private static CSVReader getCsvReader(Context context, String fileName) throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = assetManager.open(fileName);
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        return new CSVReader(bufferedReader, Export.CSV_DELIMITER);
    }

    private static class ImportFoodTask extends AsyncTask<Void, Void, Void> {

        private WeakReference<Context> context;
        private Locale locale;

        ImportFoodTask(Context context, Locale locale) {
            this.context = new WeakReference<>(context);
            this.locale = locale;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                CSVReader reader = getCsvReader(context.get(), FOOD_CSV_FILE_NAME);

                String languageCode = locale.getLanguage();
                String[] nextLine = reader.readNext();
                int languageRow = getLanguageColumn(languageCode, nextLine);

                List<Food> foodList = new ArrayList<>();
                while ((nextLine = reader.readNext()) != null) {

                    if (nextLine.length >= 14) {
                        Food food = new Food();

                        food.setName(nextLine[languageRow]);
                        food.setIngredients(food.getName());
                        food.setLabels(context.get().getString(R.string.food_common));
                        food.setLanguageCode(languageCode);

                        food.setCarbohydrates(NumberUtils.parseNullableNumber(nextLine[4]));
                        food.setEnergy(NumberUtils.parseNullableNumber(nextLine[5]));
                        food.setFat(NumberUtils.parseNullableNumber(nextLine[6]));
                        food.setFatSaturated(NumberUtils.parseNullableNumber(nextLine[7]));
                        food.setFiber(NumberUtils.parseNullableNumber(nextLine[8]));
                        food.setProteins(NumberUtils.parseNullableNumber(nextLine[9]));
                        food.setSalt(NumberUtils.parseNullableNumber(nextLine[10]));
                        food.setSodium(NumberUtils.parseNullableNumber(nextLine[11]));
                        food.setSugar(NumberUtils.parseNullableNumber(nextLine[12]));

                        if (!TextUtils.isEmpty(nextLine[13])) {
                            food.setImageUrl(nextLine[13]);
                        }

                        foodList.add(food);
                    }
                }

                Collections.reverse(foodList);
                FoodDao.getInstance().deleteAll();
                FoodDao.getInstance().bulkCreateOrUpdate(foodList);

                Log.i(TAG, String.format("Imported %d common food items from csv", foodList.size()));

            } catch (IOException exception) {
                Log.e(TAG, exception.getLocalizedMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            PreferenceHelper.getInstance().setDidImportCommonFood(locale, true);
        }
    }

    private static class UpdateFoodImagesTask extends AsyncTask<Void, Void, Void> {

        private WeakReference<Context> context;
        private Locale locale;

        UpdateFoodImagesTask(Context context, Locale locale) {
            this.context = new WeakReference<>(context);
            this.locale = locale;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                CSVReader reader = getCsvReader(context.get(), FOOD_CSV_FILE_NAME);

                String languageCode = locale.getLanguage();
                String[] nextLine = reader.readNext();
                int languageRow = getLanguageColumn(languageCode, nextLine);

                List<Food> foodList = new ArrayList<>();
                while ((nextLine = reader.readNext()) != null) {

                    if (nextLine.length >= 14) {
                        String name = nextLine[languageRow];
                        String imageUrl = nextLine[13];
                        Food food = FoodDao.getInstance().get(name);
                        if (food != null && !TextUtils.isEmpty(imageUrl)) {
                            Log.d(TAG, "Updating food image:  " + name);
                            food.setImageUrl(imageUrl);
                            foodList.add(food);
                        } else {
                            Log.e(TAG, "Failed to update image: " + name);
                        }
                    }
                }

                FoodDao.getInstance().bulkCreateOrUpdate(foodList);
                Log.i(TAG, String.format("Updated %d images for common food items", foodList.size()));

            } catch (IOException exception) {
                Log.e(TAG, exception.getLocalizedMessage());
            }
            return null;
        }
    }

    private static class ImportTagsTask extends AsyncTask<Void, Void, Void> {

        private WeakReference<Context> context;
        private Locale locale;

        ImportTagsTask(Context context, Locale locale) {
            this.context = new WeakReference<>(context);
            this.locale = locale;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                CSVReader reader = getCsvReader(context.get(), TAGS_CSV_FILE_NAME);

                String languageCode = locale.getLanguage();
                String[] nextLine = reader.readNext();
                int languageRow = getLanguageColumn(languageCode, nextLine);

                List<Tag> tags = new ArrayList<>();
                while ((nextLine = reader.readNext()) != null) {
                    if (nextLine.length >= 4) {
                        Tag tag = new Tag();
                        tag.setName(nextLine[languageRow]);
                        tags.add(tag);
                    }
                }

                TagDao.getInstance().deleteAll();
                Collections.reverse(tags);
                TagDao.getInstance().bulkCreateOrUpdate(tags);

                Log.i(TAG, String.format("Imported %d tags from csv", tags.size()));

            } catch (IOException exception) {
                Log.e(TAG, exception.getLocalizedMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            PreferenceHelper.getInstance().setDidImportTags(locale, true);
        }
    }

    private static class CreateTestData extends AsyncTask<Void, Void, Void> {

        private static final int DATA_COUNT = 200;

        @Override
        protected Void doInBackground(Void... params) {
            for (int count = 0; count < DATA_COUNT; count++) {
                DateTime dateTime = DateTime.now().minusDays(count);
                Entry entry = new Entry();
                entry.setDate(dateTime);
                entry.setNote(DateTimeFormat.mediumDateTime().print(dateTime));
                EntryDao.getInstance().createOrUpdate(entry);

                BloodSugar bloodSugar = new BloodSugar();
                bloodSugar.setMgDl(100);
                bloodSugar.setEntry(entry);
                MeasurementDao.getInstance(BloodSugar.class).createOrUpdate(bloodSugar);

                Meal meal = new Meal();
                meal.setCarbohydrates(20);
                meal.setEntry(entry);
                MeasurementDao.getInstance(Meal.class).createOrUpdate(meal);

                Log.d(TAG, "Created test data: " + (count + 1) + "/" + DATA_COUNT);
            }
            return null;
        }
    }
}
