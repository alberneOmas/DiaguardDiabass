package com.faltenreich.diaguard.database;

import android.util.Log;

import com.faltenreich.diaguard.DiaguardApplication;
import com.faltenreich.diaguard.database.measurements.Activity;
import com.faltenreich.diaguard.database.measurements.BloodSugar;
import com.faltenreich.diaguard.database.measurements.HbA1c;
import com.faltenreich.diaguard.database.measurements.Insulin;
import com.faltenreich.diaguard.database.measurements.Meal;
import com.faltenreich.diaguard.database.measurements.Measurement;
import com.faltenreich.diaguard.database.measurements.Pressure;
import com.faltenreich.diaguard.database.measurements.Pulse;
import com.faltenreich.diaguard.database.measurements.Weight;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.DatabaseTableConfig;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Interval;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Filip on 28.06.2015.
 */
public class DatabaseFacade {

    private static DatabaseFacade instance;
    private DatabaseHelper databaseHelper;

    public static DatabaseFacade getInstance() {
        if (DatabaseFacade.instance == null) {
            DatabaseFacade.instance = new DatabaseFacade();
        }
        return DatabaseFacade.instance;
    }

    private DatabaseFacade() {
        this.databaseHelper = OpenHelperManager.getHelper(DiaguardApplication.getContext(), DatabaseHelper.class);
    }

    public <T extends Model> Dao<T, Long> getDao(Class<T> clazz) {
        try {
            return databaseHelper.getDao(clazz);
        } catch (SQLException exception) {
            Log.e("Couldn't get Dao", exception.getMessage());
            return null;
        }
    }
    
    public <T extends Model> List<T> getAll(Class<T> clazz, long offset, long limit,
                                            String orderColumn, boolean ascending) throws SQLException {
        return getDao(clazz).queryBuilder().offset(offset).limit(limit).orderBy(orderColumn, ascending).query();
    }

    @SuppressWarnings("unchecked")
    public List<Measurement> getMeasurements(Entry entry) throws SQLException {
        Class[] classes = new Class[] {
                BloodSugar.class,
                Insulin.class,
                Meal.class,
                Activity.class,
                HbA1c.class,
                Weight.class,
                Pulse.class,
                Pressure.class
        };
        return getMeasurements(entry, classes);
    }

    public <T extends Measurement> List<Measurement> getMeasurements(Entry entry, Class<T>[] classes) throws SQLException {
        List<Measurement> measurements = new ArrayList<>();
        for (Class<T> clazz : classes) {
            T measurement = getDao(clazz).queryBuilder().where().eq(Measurement.ENTRY_ID, entry.getId()).queryForFirst();
            if (measurement != null) {
                measurements.add(measurement);
            }
        }
        return measurements;
    }

    public <T extends Model, D extends Model> QueryBuilder<T, ?> join(Class<T> clazzOne, Class<D> classTwo) throws SQLException {
        QueryBuilder<T, ?> qbOne = getDao(clazzOne).queryBuilder();
        QueryBuilder<D, ?> qbTwo = getDao(classTwo).queryBuilder();
        return qbOne.join(qbTwo);
    }

    /**
     * Get average from one day
     */
    public <T extends Measurement> float avg(Class<T> clazz, String avgColumn, DateTime dateTime) throws SQLException {
        return avg(clazz, avgColumn, new Interval(dateTime, dateTime));
    }


    /**
     * Get average from interval
     */
    public <T extends Measurement> float avg(Class<T> clazz, String avgColumn, Interval interval) throws SQLException {
        String classNameEntry = DatabaseTableConfig.extractTableName(Entry.class);
        String classNameMeasurement = DatabaseTableConfig.extractTableName(clazz);
        long intervalStart = interval.getStart().withTimeAtStartOfDay().getMillis();
        long intervalEnd = interval.getEnd().withTime(
                DateTimeConstants.HOURS_PER_DAY - 1,
                DateTimeConstants.MINUTES_PER_HOUR - 1,
                DateTimeConstants.SECONDS_PER_MINUTE - 1,
                DateTimeConstants.MILLIS_PER_SECOND - 1)
                .getMillis();
        String query = "SELECT AVG(" + avgColumn + ")" +
                " FROM " + classNameMeasurement +
                " INNER JOIN " + classNameEntry +
                " ON " + classNameMeasurement + "." + Measurement.ENTRY_ID +
                " = " + classNameEntry + "." + Model.ID +
                " AND " + classNameEntry + "." + Entry.DATE +
                " >= " + intervalStart +
                " AND " + classNameEntry + "." + Entry.DATE +
                " <= " + intervalEnd + ";";
        GenericRawResults<String[]> rawResults = getDao(clazz).queryRaw(query);
        List<String[]> results = rawResults.getResults();
        String[] resultArray = results.get(0);
        String result = resultArray[0];
        try {
            return Float.parseFloat(result);
        } catch (NumberFormatException exception) {
            return 0;
        } catch (NullPointerException exception) {
            return 0;
        }
    }
}
