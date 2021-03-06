/*
    CanZE
    Take a closer look at your ZE car

    Copyright (C) 2015 - The CanZE Team
    http://canze.fisch.lu

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any
    later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package lu.fisch.canze.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lu.fisch.canze.R;
import lu.fisch.canze.actors.Field;
import lu.fisch.canze.bluetooth.BluetoothManager;
import lu.fisch.canze.widgets.Drawable;
import lu.fisch.canze.widgets.WidgetView;

/**
 * Created by robertfisch on 30.09.2015.
 */
public class CanzeActivity extends AppCompatActivity {

    private boolean iLeftMyOwn = false;
    private boolean back = false;

    protected boolean widgetView = false;
    protected boolean widgetClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(MainActivity.device!=null)
            if(!BluetoothManager.getInstance().isConnected())
                // restart Bluetooth
                BluetoothManager.getInstance().connect();
        MainActivity.debug("CanzeActivity: onCreate");
        if(!widgetView) {
            // register all fields
            // --> not needed as these frames are now application bound and will not be cleared anyway
            // MainActivity.registerFields();
            // initialise the widgets (if any present)
            // --> not needed as onResume will call it!
            //initWidgets();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainActivity.debug("CanzeActivity: onPause");

        // stop here if BT should stay on!
        if(MainActivity.bluetoothBackgroundMode)
        {
            return;
        }

        // if we are not coming back from somewhere, stop Bluetooth
        if(!back && !widgetClicked) {
            MainActivity.debug("CanzeActivity: onPause > stopBluetooth");
            MainActivity.getInstance().stopBluetooth(false);
        }
        if(!widgetClicked) {
            // remember we paused ourselves
            iLeftMyOwn=true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivity.debug("CanzeActivity: onResume");
        // if we paused ourselvers
        if (iLeftMyOwn && !widgetClicked) {
            MainActivity.debug("CanzeActivity: onResume > reloadBluetooth");
            // restart Bluetooth
            MainActivity.getInstance().reloadBluetooth(false);
            iLeftMyOwn=false;
        }
        if(!widgetClicked) {
            MainActivity.debug("CanzeActivity: onResume > initWidgets");
            // initialise the widgets (if any present)
            initWidgets();
        }
        widgetClicked=false;
    }

    @Override
    protected void onDestroy() {
        MainActivity.debug("CanzeActivity: onDestroy");
        if(!widgetView) {
            // free the widget listerners
            freeWidgetListeners();
            if (isFinishing()) {
                MainActivity.debug("CanzeActivity: onDestroy (finishing)");
                // clear filters
                MainActivity.device.clearFields();
                //MainActivity.registerFields();
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(MainActivity.isSafe()) {
            super.onBackPressed();
            back = true;
        }
    }

    /********************************************************/

    protected void initWidgets()
    {
        final ArrayList<WidgetView> widgets = getWidgetViewArrayList((ViewGroup) findViewById(android.R.id.content));
        if(!widgets.isEmpty())
            MainActivity.toast("Initialising widgets and loading data ...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                // sleep a bit to give the UI time to initialise
                //try { Thread.sleep(1000); }
                //catch (Exception e) {}

                // load data
                //SharedPreferences settings = getSharedPreferences(MainActivity.DATA_FILE, 0);

                // connect the widgets to the respective fields
                // and add the filters to the reader

                for (int i = 0; i < widgets.size(); i++) {
                    final WidgetView wv = widgets.get(i);

                    MainActivity.debug("Widget: "+wv.getDrawable().getTitle()+" ("+wv.getFieldSID()+")");

                    // connect widgets to fields
                    if (wv == null) {
                        throw new ExceptionInInitializerError("Widget <" + wv.getId() + "> is NULL!");
                    }

                    /*String sid = wv.getFieldSID(); --> done in WidgetView
                    String[] sids = sid.split(",");
                    for(int s=0; s<sids.length; s++) {
                        Field field = MainActivity.fields.getBySID(sids[s]);
                        if (field == null) {
                            MainActivity.debug("!!! >> Field with following SID <" + sids[s] + "> not found!");
//                            Toast.makeText(CanzeActivity.this, "Field with following SID <" + sids[s] + "> not found!", Toast.LENGTH_SHORT).show();
                            //throw new ExceptionInInitializerError("Field with following SID <" + wv.getFieldSID() + "> not found!");
                        }
                        else {
                            Drawable drawable = wv.getDrawable();
                            // add field to list of registered sids for this widget
                            drawable.addField(field.getSID());
                            // add listener
                            field.addListener(wv.getDrawable());
                            // add filter to reader
                            int interval = drawable.getIntervals().getInterval(field.getSID());
                            if(interval==-1)
                                MainActivity.device.addActivityField(field);
                            else
                                MainActivity.device.addActivityField(field,interval);
                        }
                    }*/

                    // load data --> replaced by loading from the database
                    /*
                    String id = wv.getDrawable().getClass().getSimpleName()+"."+wv.getFieldSID();
                    String json = "";
                    try {
                        json=decompress(settings.getString(id, ""));
                    }
                    catch(Exception e)
                    {
                        //e.printStackTrace();
                    }

                    if(!json.trim().isEmpty())
                    {
                        wv.getDrawable().dataFromJson(json);
                    }
                    */

                    // --> done in drawable
                    //wv.getDrawable().loadValuesFromDatabase();

                    //wv.repaint();

                    // touching a widget makes a "bigger" version appear
                    //wv.setOnTouchListener(wv);
                    /*wv.setOnTouchBackListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            // get masked (not specific to a pointer) action
                            int maskedAction = event.getActionMasked();

                            switch (maskedAction) {
                                case MotionEvent.ACTION_UP:
                                case MotionEvent.ACTION_POINTER_UP: {
                                    MainActivity.debug("Done!");
                                    widgetClicked=true;
                                    break;
                                }
                                default: {
                                    break;
                                }
                            }
                            return true;
                        }
                    });*/

                    // touching a widget makes a "bigger" version appear
                    /*wv.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if(((WidgetView) v).isClickable()) {
                                // get pointer index from the event object
                                int pointerIndex = event.getActionIndex();

                                // get pointer ID
                                int pointerId = event.getPointerId(pointerIndex);

                                // get masked (not specific to a pointer) action
                                int maskedAction = event.getActionMasked();

                                switch (maskedAction) {
                                    case MotionEvent.ACTION_DOWN:
                                    case MotionEvent.ACTION_POINTER_DOWN: {
                                        widgetClicked=true;
                                        Intent intent = new Intent(CanzeActivity.this, WidgetActivity.class);
                                        //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        WidgetView.selectedDrawable = wv.getDrawable();
                                        CanzeActivity.this.startActivity(intent);
                                        break;
                                    }
                                    case MotionEvent.ACTION_MOVE:
                                    case MotionEvent.ACTION_UP:
                                    case MotionEvent.ACTION_POINTER_UP:
                                    case MotionEvent.ACTION_CANCEL: {
                                        break;
                                    }
                                }

                                wv.invalidate();

                                return true;
                            }
                            else return false;
                        }
                    });*/

                }
            }
        }).start();
    }

        /*
    protected void saveWidgetData()
    {
        // free up the listener again
        ArrayList<WidgetView> widgets = getWidgetViewArrayList((ViewGroup) findViewById(R.id.table));
        // save widget data
        SharedPreferences settings = getSharedPreferences(MainActivity.DATA_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        for(int i=0; i<widgets.size(); i++) {
            WidgetView wv = widgets.get(i);
            // save widget data
            String id = wv.getDrawable().getClass().getSimpleName() + "." + wv.getFieldSID();
            String data = "";
            {
                try {
                    data = compress(wv.getDrawable().dataToJson());
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
            editor.putString(id, data);
        }
        editor.commit();
    }
        */

    protected void freeWidgetListeners()
    {
        // free up the listener again
        ArrayList<WidgetView> widgets = getWidgetViewArrayList((ViewGroup) findViewById(R.id.table));
        for(int i=0; i<widgets.size(); i++) {
            WidgetView wv = widgets.get(i);
            String sid = wv.getFieldSID();
            if(sid!=null) {
                String[] sids = sid.split(",");
                for (int s = 0; s < sids.length; s++) {
                    Field field = MainActivity.fields.getBySID(sids[s]);
                    if (field != null) {
                        field.removeListener(wv.getDrawable());
                    }
                }
            }
        }
    }


    protected ArrayList<WidgetView> getWidgetViewArrayList(ViewGroup viewGroup)
    {
        ArrayList<WidgetView> result = new ArrayList<WidgetView>();

        if(viewGroup!=null)
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof ViewGroup) {
                result.addAll(getWidgetViewArrayList((ViewGroup) v));
            }
            else if (v instanceof WidgetView)
            {
                result.add((WidgetView)v);
            }
        }

        return result;
    }


    public static String compress(String string) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(string.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        os.close();
        return Base64.encodeToString(compressed, Base64.NO_WRAP);
    }

    public static String decompress(String zipText) throws IOException {
        byte[] compressed = Base64.decode(zipText,Base64.NO_WRAP);
        final int BUFFER_SIZE = 32;
        ByteArrayInputStream is = new ByteArrayInputStream(compressed);
        GZIPInputStream gis = new GZIPInputStream(is, BUFFER_SIZE);
        StringBuilder string = new StringBuilder();
        byte[] data = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = gis.read(data)) != -1) {
            string.append(new String(data, 0, bytesRead));
        }
        gis.close();
        is.close();
        return string.toString();
    }

}

