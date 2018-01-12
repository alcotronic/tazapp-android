package de.thecode.android.tazreader.room;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

import de.thecode.android.tazreader.data.Paper;
import de.thecode.android.tazreader.data.Publication;
import de.thecode.android.tazreader.data.Resource;
import de.thecode.android.tazreader.data.Store;

/**
 * Created by mate on 10.01.2018.
 */

@Database(entities = {Paper.class, Resource.class, Store.class, Publication.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 7;
    private static final String DB_NAME = "db";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = create(context);
        }
        return instance;
    }

    private static AppDatabase create(final Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                   .addMigrations(Migrations.getAllmigrations())
                   .build();
    }
}
