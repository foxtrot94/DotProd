package me.dotteam.dotprod.loc;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Location Requests to GoogleApiClient and provides an easy way for any object to obtain Location Updates using one common entity.
 *
 * Created by EricTremblay on 15-11-05.
 */
public class HikeLocationEntity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    /**
     * Class TAG
     */
    private final String TAG = "HikeLocationEntity";

    /**
     * Default Values for Location Updates
     */
    private final int DEFAULT_INTERVAL = 5000;
    private final int DEFAULT_FASTEST_INTERVAL = 1000;
    private final int DEFAULT_PRIORITY = LocationRequest.PRIORITY_HIGH_ACCURACY;

    /**
     * Location Request Object
     */
    private LocationRequest mLocationRequest;

    /**
     * Actual Values for Location Updates
     */
    private int mInterval;
    private int mFastestInterval;
    private int mPriority;

    /**
     * HikeLocationEntity Singleton Reference
     */
    private static HikeLocationEntity mInstance;

    /**
     * Activity Context Object
     */
    private Context mContext;

    /**
     * Reference to Google API Client Object
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * List of Currently Subscribed LocationListeners
     */
    private List<LocationListener> mLocationListeners;

    /**
     * Boolean to save the GoogleApiClient connection state
     */
    private boolean mGoogleApiClientConnected = false;

    /**
     * Boolean to save whether updates are on or off
     */
    private boolean mRequestingLocationUpdates = false;

    /**
     * Singleton method to obtain or generate current instance
     * @param context Context from which the instance is being requested
     * @return Singleton Object instance of HikeLocationEntity
     */
    public static HikeLocationEntity getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new HikeLocationEntity(context);
        }
        return mInstance;
    }

    /**
     * Private Constructor for HikeLocationEntity
     * @param context Context from which the object creation is being requested
     */
    private HikeLocationEntity(Context context) {
        // Create LocationListener List
        mLocationListeners = new ArrayList<>();

        // Save Context
        mContext = context;

        // Build GoogleApiClient
        buildGoogleApiClient();

        // Create Location Request and set variables to default values
        mLocationRequest = new LocationRequest();
        mInterval = DEFAULT_INTERVAL;
        mFastestInterval = DEFAULT_FASTEST_INTERVAL;
        mPriority = DEFAULT_PRIORITY;
        mLocationRequest.setInterval(mInterval);
        mLocationRequest.setFastestInterval(mFastestInterval);
        mLocationRequest.setPriority(mPriority);
    }

    /**
     * Starts location updates for all registered listeners
     */
    public void startLocationUpdates(Context context) {
        Log.d(TAG, "Starting location updates");

        // Update Context
        mContext = context;

        mRequestingLocationUpdates = true;
        if (mGoogleApiClientConnected) {

            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(mLocationRequest);

            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

            result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(LocationSettingsResult result) {
                    final Status status = result.getStatus();
                    final LocationSettingsStates states = result.getLocationSettingsStates();
                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            // All location settings are satisfied. The client can initialize location
                            // requests here.
                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the user
                            // a dialog.
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().

                            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
                            dialogBuilder.setTitle("Turn on Location?")
                                    .setMessage("In order for an optimal .Hike experience, please turn on your cellphone's location services. Do you wish to turn on location services now?")
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mContext.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                        }
                                    })
                                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            stopLocationUpdates();
                                        }
                                    });
                            dialogBuilder.create().show();

                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            break;
                    }
                }
            });

            for (int i = 0; i < mLocationListeners.size(); i++) {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, mLocationListeners.get(i));
            }
        } else {
            Log.i(TAG, "GoogleApiClient is not connected. Unable to start location updates");
        }
    }

    /**
     * Stops location updates for all registered listeners
     */
    public void stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates");
        mRequestingLocationUpdates = false;
        if (mGoogleApiClientConnected) {
            for (int i = 0; i < mLocationListeners.size(); i++) {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, mLocationListeners.get(i));
            }
        } else {
            Log.i(TAG, "GoogleApiClient is not connected. Unable to stop location updates");
        }
    }

    /**
     * Applies changes to location entity by calling {@link #stopLocationUpdates()}
     * and {@link #startLocationUpdates(Context)}
     */
    public void resetLocationUpdates() {
        if (mGoogleApiClientConnected) {
            stopLocationUpdates();
            startLocationUpdates(mContext);
        } else {
            Log.i(TAG, "GoogleApiClient is not connected. Unable to reset location updates");
        }
    }

    /**
     * Register LocationListener to HikeLocationEntity.
     * Start location updates for the LocationListener if the GoogleApiClient is connected and the location updates have been started
     * @param listener LocationListener to be registered
     */
    public void addListener(LocationListener listener) {
        Log.d(TAG, "Adding listener " + listener.toString());
        mLocationListeners.add(listener);
        if (mGoogleApiClientConnected && mRequestingLocationUpdates) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, listener);
        }
    }

    /**
     * Unregister LocationListener from HikeLocationEntity.
     * This LocationListener will cease receiving any location updates
     * @param listener LocationListener to unregister
     */
    public void removeListener(LocationListener listener) {
        Log.d(TAG, "Removing listener " + listener.toString());
        mLocationListeners.remove(listener);
        if (mGoogleApiClientConnected && mRequestingLocationUpdates) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, listener);
        }
    }

    /**
     * Method to build GoogleApiClient object from within HikeLocationEntity object
     */
    private synchronized void buildGoogleApiClient() {
        Log.d(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    /**
     * Method to set the preferred rate at which the application receives location updates
     * @param mInterval New interval between location updates in milliseconds
     */
    public void setInterval(int mInterval) {
        this.mInterval = mInterval;
        mLocationRequest.setInterval(mInterval);
    }

    /**
     * Method to set the fastest rate at which the application can handle location updates
     * @param mFastestInterval New fastest interval between location updates in milliseconds
     */
    public void setFastestInterval(int mFastestInterval) {
        this.mFastestInterval = mFastestInterval;
        mLocationRequest.setFastestInterval(mFastestInterval);
    }

    /**
     * Method to set the priority of the location request. This will allow the Google Play services to determine which location sources to use.
     * Possible values for priority: PRIORITY_BALANCED_POWER_ACCURACY,
     * PRIORITY_HIGH_ACCURACY, PRIORITY_LOW_POWER, and PRIORITY_NO_POWER.
     * @param mPriority New priority for location request
     */
    public void setPriority(int mPriority) {
        this.mPriority = mPriority;
        mLocationRequest.setPriority(mPriority);
    }

    /**
     * Method to obtain the current preferred interval between location updates.
     * @return Current preferred interval value
     */
    public int getInterval() {
        return mInterval;
    }

    /**
     * Method to obtain the current fastest interval between location updates.
     * @return Current fastest interval value
     */
    public int getFastestInterval() {
        return mFastestInterval;
    }

    /**
     * Method to obtain the current priority of the location request.
     * @return Current priority value
     */
    public int getPriority() {
        return mPriority;
    }

    /**
     * Method to obtain the current state of the GoogleApiClient Connection
     * @return True indicates the GoogleApiClient is connected, false indicates it is disconnected
     */
    public boolean isGoogleApiClientConnected() {
        return mGoogleApiClientConnected;
    }

    /**
     * Method to obtain the current state of the location updates.
     * @return True indicates the location updates are on, false indicates that they are off.
     */
    public boolean isRequestingLocationUpdates() {
        return mRequestingLocationUpdates;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");
        mGoogleApiClientConnected = true;
        if (mRequestingLocationUpdates) {
            startLocationUpdates(mContext);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        String cause = "CAUSE_UNKNOWN";
        switch (i) {
            case CAUSE_SERVICE_DISCONNECTED: {
                cause = "CAUSE_SERVICE_DISCONNECTED";
                break;
            }
            case CAUSE_NETWORK_LOST: {
                cause = "CAUSE_NETWORK_LOST";
                break;
            }
        }

        Log.i(TAG, "GoogleApiClient Connection Suspended. Cause: " + cause);
        mGoogleApiClientConnected = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "GoogleApiClient connection failed" + connectionResult);
        mGoogleApiClientConnected = false;

    }
}
