/*
 * Copyright (c) 2014
 *
 * ApkTrack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ApkTrack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ApkTrack.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.kwiatkowski.ApkTrack;

import android.app.ListActivity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends ListActivity
{

    AppAdapter adapter;
    PackageManager pacman;
    AppPersistence persistence;
    List<InstalledApp> installed_apps;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        persistence = new AppPersistence(getApplicationContext(), getResources());
        installed_apps = getInstalledAps();

        adapter = new AppAdapter(this, installed_apps);
        setListAdapter(adapter);
    }

    /**
     * Retreives the list of applications installed on the device.
     * If no data is present in the database, the list is generated.
     */
    private List<InstalledApp> getInstalledAps()
    {
        List<InstalledApp> applist = persistence.getStoredApps();
        if (applist.size() == 0) {
            applist = refreshInstalledApps(true);
        }
        Collections.sort(applist); // Sort applications in alphabetical order.
        return applist;
    }


    /**
     * Generates a list of (non-system) applications installed on
     * this device. The data is retrieved from the PackageManager.
     *
     * @param overwrite_database If true, the data already present in ApkTrack's SQLite database will be
     *                           overwritten by the new data.
     */
    private List<InstalledApp> refreshInstalledApps(boolean overwrite_database)
    {
        List<InstalledApp> applist = new ArrayList<InstalledApp>();
        pacman = getPackageManager();
        if (pacman != null)
        {
            List<PackageInfo> list = pacman.getInstalledPackages(0);
            for (PackageInfo pi : list)
            {
                ApplicationInfo ai;
                try {
                    ai = pacman.getApplicationInfo(pi.packageName, 0);
                }
                catch (final PackageManager.NameNotFoundException e) {
                    ai = null;
                }
                String applicationName = (String) (ai != null ? pacman.getApplicationLabel(ai) : null);
                applist.add(new InstalledApp(pi.packageName,
                        pi.versionName,
                        applicationName,
                        isSystemPackage(pi),
                        ai != null ? ai.loadIcon(pacman) : null));
            }

            if (overwrite_database)
            {
                for (InstalledApp ia : applist) {
                    persistence.persistApp(ia);
                }
            }
        }
        else {
            Log.v("MainActivity", "Could not get application list!");
        }
        return applist;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inf = getMenuInflater();
        inf.inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * This function handles user input through the action bar.
     * Three buttons exist as of yet:
     * - Get the latest version for all installed apps
     * - Regenerate the list of installed applications
     * - Hide / show system applications
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.check_all_apps:
                for (InstalledApp ia : installed_apps) {
                    performVersionCheck(ia);
                }
                return true;

            case R.id.refresh_apps:
                List<InstalledApp> new_list = refreshInstalledApps(false);

                // Remove the ones we already have. We wouldn't want duplicates
                new_list.removeAll(installed_apps);
                new_list.removeAll(adapter.getHiddenApps());

                Toast t = Toast.makeText(getApplicationContext(),
                                         new_list.size() + " new application(s) detected.",
                                         Toast.LENGTH_SHORT);
                t.show();

                if (new_list.size() > 0)            // nor overwriting existing data.
                {
                    // TODO: UNTESTED + Replace by Intent
                    for (InstalledApp app : new_list)
                    {
                        // Save the newly detected applications in the database.
                        persistence.persistApp(app);

                        // Put the application in the right list: it may be hidden.
                        if (app.isSystemApp() && !adapter.isShowSystem()) {
                            adapter.getHiddenApps().add(app);
                        }
                        else {
                            installed_apps.add(app);
                        }
                    }

                    installed_apps.addAll(new_list);
                    Collections.sort(installed_apps);
                    ((AppAdapter) getListAdapter()).notifyDataSetChanged();
                }
                return true;

            case R.id.show_system:
                if (!adapter.isShowSystem())
                {
                    adapter.showSystemApps();
                    item.setTitle("Hide system applications");
                }
                else
                {
                    adapter.hideSystemApps();
                    item.setTitle("Show system applications");
                }
                adapter.notifyDataSetChanged();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        InstalledApp app = (InstalledApp) getListView().getItemAtPosition(position);
        performVersionCheck(app);
    }

    private void performVersionCheck(InstalledApp app)
    {
        if (app != null && !app.isCurrentlyChecking())
        {
            // The loader icon will be displayed from here on
            app.setCurrentlyChecking(true);
            ((AppAdapter) getListAdapter()).notifyDataSetChanged();
            new AsyncStoreGet(app, getApplicationContext(), (AppAdapter) getListAdapter(), persistence).execute(app.getPackageName());
        }
    }

    private boolean isSystemPackage(PackageInfo pkgInfo)
    {
        return !(pkgInfo == null || pkgInfo.applicationInfo == null) && ((pkgInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
}

