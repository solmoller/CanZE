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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
//import android.widget.ProgressBar;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

import lu.fisch.canze.R;
import lu.fisch.canze.actors.Field;
import lu.fisch.canze.interfaces.FieldListener;

public class DrivingActivity extends CanzeActivity implements FieldListener {

    // for ISO-TP optimization to work, group all identical CAN ID's together when calling addListener

    // free data
    public static final String SID_Pedal                                = "186.40"; //EVC
    public static final String SID_MeanEffectiveTorque                  = "186.16"; //EVC
    public static final String SID_RealSpeed                            = "5d7.0";  //ESC-ABS
    public static final String SID_SoC                                  = "654.25"; //EVC
    public static final String SID_RangeEstimate                        = "654.42"; //EVC
    public static final String SID_DriverBrakeWheel_Torque_Request      = "130.44"; //UBP braking wheel torque the driver wants
    public static final String SID_ElecBrakeWheelsTorqueApplied         = "1f8.28"; //UBP 10ms

    // ISO-TP data
//  public static final String SID_EVC_SoC                              = "7ec.622002.24"; //  (EVC)
//  public static final String SID_EVC_RealSpeed                        = "7ec.622003.24"; //  (EVC)
    public static final String SID_EVC_Odometer                         = "7ec.622006.24"; //  (EVC)
//  public static final String SID_EVC_Pedal                            = "7ec.62202e.24"; //  (EVC)
    public static final String SID_EVC_TractionBatteryVoltage           = "7ec.623203.24"; //  (EVC)
    public static final String SID_EVC_TractionBatteryCurrent           = "7ec.623204.24"; //  (EVC)

    private double dcVolt                           = 0; // holds the DC voltage, so we can calculate the power when the amps come in
    private int    odo                              = 0;
    private int    destOdo                          = 0; // have to init from save file
    private double realSpeed                        = 0;
    private double driverBrakeWheel_Torque_Request  = 0;

    private ArrayList<Field> subscribedFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driving);

        final TextView distkmToDest = (TextView) findViewById(R.id.LabelDistToDest);
        distkmToDest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDistanceToDestination();
            }
        });

        if (MainActivity.milesMode) {
            TextView tv;
            tv = (TextView) findViewById(R.id.textSpeedUnit);
            tv.setText(getResources().getString(R.string.unit_SpeedMi));
            tv = (TextView) findViewById(R.id.textConsumptionUnit);
            tv.setText(getResources().getString(R.string.unit_ConsumptionMi));
        }
        initListeners();
    }

    void setDistanceToDestination () {
        // don't react if we do not have a live odo yet
        if (odo == 0) return;
        final Context context = DrivingActivity.this;
        final EditText textEdit = new EditText(this);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        LayoutInflater inflater = getLayoutInflater();
        final View distToDestView = inflater.inflate(R.layout.set_dist_to_dest, null);

        // set dialog message
        alertDialogBuilder
                .setView(distToDestView)
                .setTitle("REMAINING DISTANCE")
                .setMessage("Please enter the distance to your destination. The display will estimate " +
                        "the remaining driving distance available in your battery on arrival")

                .setCancelable(true)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,0);
                        EditText dialogDistToDest = (EditText) distToDestView.findViewById(R.id.dialog_dist_to_dest);
                        if (dialogDistToDest != null) {
                            saveDestOdo(odo + Integer.parseInt(dialogDistToDest.getText().toString()));
                        }
                        dialog.cancel();
                    }
                })
                .setNeutralButton("Double", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,0);
                        EditText dialogDistToDest = (EditText) distToDestView.findViewById(R.id.dialog_dist_to_dest);
                        if (dialogDistToDest != null) {
                            saveDestOdo(odo + 2 * Integer.parseInt(dialogDistToDest.getText().toString()));
                        }
                        dialog.cancel();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT,0);
                        dialog.cancel();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

        // show it
        alertDialog.show();
    }

    private void saveDestOdo (int d) {
        SharedPreferences settings = getSharedPreferences(MainActivity.PREFERENCES_FILE, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("destOdo", d);
        editor.commit();
        destOdo = d;
        Field field = MainActivity.fields.getBySID(SID_RangeEstimate);
        int distInBat = (int) field.getValue();
        if (destOdo > odo) {
            setDestToDest("" + (destOdo - odo), "" + (distInBat - destOdo + odo));
        } else {
            setDestToDest("0", "0");
        }
    }

    private void getDestOdo () {
        SharedPreferences settings = getSharedPreferences(MainActivity.PREFERENCES_FILE, 0);
        destOdo = settings.getInt("destOdo", 0);
        // get last persistent odo to calc
        Field field = MainActivity.fields.getBySID(SID_EVC_Odometer);
        odo = (int)field.getValue();
        field = MainActivity.fields.getBySID(SID_RangeEstimate);
        int distInBat = (int) field.getValue();
        if (destOdo > odo) {
            setDestToDest("" + (destOdo - odo), "" + (distInBat - destOdo + odo));
        } else {
            setDestToDest("0", "0");
        }
    }

    private void setDestToDest(String distance1, String distance2) {
        TextView tv;
        tv = (TextView) findViewById(R.id.textDistToDest);
        tv.setText(distance1);
        tv = (TextView) findViewById(R.id.textDistAVailAtDest);
        tv.setText(distance2);
    }

    private void addListener(String sid, int intervalMs) {
        Field field;
        field = MainActivity.fields.getBySID(sid);
        if (field != null) {
            field.addListener(this);
            MainActivity.device.addActivityField(field, intervalMs);
            subscribedFields.add(field);
        }
        else
        {
            MainActivity.toast("sid " + sid + " does not exist in class Fields");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // free up the listeners again
        for (Field field : subscribedFields) {
            field.removeListener(this);
        }
        subscribedFields.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // initialise the widgets
        initListeners();
    }

    private void initListeners() {

        subscribedFields = new ArrayList<>();

        getDestOdo();

        // Make sure to add ISO-TP listeners grouped by ID

        addListener(SID_Pedal, 0);
        addListener(SID_MeanEffectiveTorque, 0);
        addListener(SID_DriverBrakeWheel_Torque_Request, 0);
        addListener(SID_ElecBrakeWheelsTorqueApplied, 0);
        addListener(SID_RealSpeed, 0);
        addListener(SID_SoC, 3600);
        addListener(SID_RangeEstimate, 3600);

        //addListener(SID_EVC_SoC);
        addListener(SID_EVC_Odometer, 6000);
        addListener(SID_EVC_TractionBatteryVoltage, 5000);
        addListener(SID_EVC_TractionBatteryCurrent, 0);
        //addListener(SID_PEB_Torque);
    }


    // This is the event fired as soon as this the registered fields are
    // getting updated by the corresponding reader class.
    @Override
    public void onFieldUpdateEvent(final Field field) {
        // the update has to be done in a separate thread
        // otherwise the UI will not be repainted
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String fieldId = field.getSID();
                TextView tv = null;
                ProgressBar pb;

                // get the text field
                switch (fieldId) {
                    case SID_SoC:
//                  case SID_EVC_SoC:
                        tv = (TextView) findViewById(R.id.textSOC);
                        break;
                    case SID_Pedal:
//                  case SID_EVC_Pedal:
                        pb = (ProgressBar) findViewById(R.id.pedalBar);
                        pb.setProgress((int) field.getValue());
                        break;
                    case SID_MeanEffectiveTorque:
                        pb = (ProgressBar) findViewById(R.id.MeanEffectiveTorque);
                        pb.setProgress((int) field.getValue());
                        break;
                    case SID_EVC_Odometer:
                        odo = (int ) field.getValue();
                        //odo = (int) Utils.kmOrMiles(field.getValue());
                        tv = null;
                        break;
                    case SID_RealSpeed:
//                  case SID_EVC_RealSpeed:
                        //realSpeed = (Math.round(Utils.kmOrMiles(field.getValue()) * 10.0) / 10.0);
                        realSpeed = (Math.round(field.getValue() * 10.0) / 10.0);
                        tv = (TextView) findViewById(R.id.textRealSpeed);
                        break;
                    //case SID_PEB_Torque:
                    //    tv = (TextView) findViewById(R.id.textTorque);
                    //    break;
                    case SID_EVC_TractionBatteryVoltage: // DC volts
                        // save DC voltage for DC power purposes
                        dcVolt = field.getValue();
                        break;
                    case SID_EVC_TractionBatteryCurrent: // DC amps
                        // calculate DC power
                        double dcPwr = Math.round(dcVolt * field.getValue() / 100.0) / 10.0;
                        tv = (TextView) findViewById(R.id.textDcPwr);
                        tv.setText("" + (dcPwr));
                        tv = (TextView) findViewById(R.id.textConsumption);
                        if (realSpeed > 5) {
                            tv.setText("" + (Math.round(1000.0 * dcPwr / realSpeed) / 10.0));
                        } else {
                            tv.setText("-");
                        }
                        tv = null;
                        break;
                    case SID_RangeEstimate:
                        //int rangeInBat = (int) Utils.kmOrMiles(field.getValue());
                        int rangeInBat = (int) field.getValue();
                        if (rangeInBat > 0 && odo > 0 && destOdo > 0) { // we update only if there are no weird values
                            try {
                                if (destOdo > odo) {
                                    setDestToDest("" + (destOdo - odo), "" + (rangeInBat - destOdo + odo));
                                } else {
                                    setDestToDest("0", "0");
                                }
                            } catch (Exception e) {
                                // empty
                            }
                        }
                        tv = null;
                        break;
                    case SID_DriverBrakeWheel_Torque_Request:
                        driverBrakeWheel_Torque_Request = field.getValue();
                        tv = null;
                        break;
                    case SID_ElecBrakeWheelsTorqueApplied:
                        double frictionBrakeTorque = driverBrakeWheel_Torque_Request - field.getValue();
                        // a fair full red bar is estimated @ 1000 Nm
                        pb = (ProgressBar) findViewById(R.id.FrictionBreaking);
                        pb.setProgress((int) (frictionBrakeTorque * realSpeed));
                        break;
                }
                // set regular new content, all exeptions handled above
                if (tv != null) {
                    tv.setText("" + (Math.round(field.getValue() * 10.0) / 10.0));
                }

                tv = (TextView) findViewById(R.id.textDebug);
                tv.setText(fieldId);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_driving, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_setDistanceToDestination) {
            setDistanceToDestination();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}