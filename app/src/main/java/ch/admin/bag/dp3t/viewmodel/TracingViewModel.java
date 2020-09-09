/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package ch.admin.bag.dp3t.viewmodel;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.TracingStatus;
import org.dpppt.android.sdk.internal.BroadcastHelper;
import org.dpppt.android.sdk.internal.history.HistoryEntry;

import ch.admin.bag.dp3t.MainApplication;
import ch.admin.bag.dp3t.debug.TracingStatusWrapper;
import ch.admin.bag.dp3t.home.model.TracingStatusInterface;
import ch.admin.bag.dp3t.util.DeviceFeatureHelper;

public class TracingViewModel extends AndroidViewModel {

	private final MutableLiveData<TracingStatus> tracingStatusLiveData = new MutableLiveData<>();
	private BroadcastReceiver tracingStatusBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			invalidateTracingStatus();
			loadHistoryEntries();
		}
	};

	private final MutableLiveData<Boolean> tracingEnabledLiveData = new MutableLiveData<>();
	private final MutableLiveData<Pair<Boolean, Boolean>> exposedLiveData = new MutableLiveData<>();
	private final MutableLiveData<Collection<TracingStatus.ErrorState>> errorsLiveData =
			new MutableLiveData<>(Collections.emptyList());
	private final MutableLiveData<TracingStatusInterface> appStatusLiveData = new MutableLiveData<>();

	private TracingStatusInterface tracingStatusInterface = new TracingStatusWrapper();

	private final MutableLiveData<Boolean> bluetoothEnabledLiveData = new MutableLiveData<>();
	private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
				invalidateBluetoothState();
				invalidateTracingStatus();
			}
		}
	};

	private final MutableLiveData<List<HistoryEntry>> historyMutableLiveData = new MutableLiveData<>();

	public TracingViewModel(@NonNull Application application) {
		super(application);

		tracingStatusLiveData.observeForever(status -> {
			errorsLiveData.setValue(status.getErrors());
			tracingEnabledLiveData.setValue(status.isTracingEnabled());
			tracingStatusInterface.setStatus(status);

			exposedLiveData
					.setValue(new Pair<>(tracingStatusInterface.isReportedAsInfected(),
							tracingStatusInterface.wasContactReportedAsExposed()));

			appStatusLiveData.setValue(tracingStatusInterface);
		});

		invalidateBluetoothState();
		invalidateTracingStatus();

		application.registerReceiver(tracingStatusBroadcastReceiver, DP3T.getUpdateIntentFilter());
		application.registerReceiver(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
	}

	public void resetSdk() {
		if (tracingEnabledLiveData.getValue()) {
			DP3T.stop(getApplication());
		}
		DP3T.clearData(getApplication());
		MainApplication.initDP3T(getApplication());
	}

	public void invalidateTracingStatus() {
		TracingStatus status = DP3T.getStatus(getApplication());
		tracingStatusLiveData.setValue(status);
	}

	public LiveData<Boolean> getTracingEnabledLiveData() {
		return tracingEnabledLiveData;
	}

	public LiveData<Pair<Boolean, Boolean>> getSelfOrContactExposedLiveData() {
		return exposedLiveData;
	}

	public LiveData<Collection<TracingStatus.ErrorState>> getErrorsLiveData() {
		return errorsLiveData;
	}

	public LiveData<TracingStatusInterface> getAppStatusLiveData() {
		return appStatusLiveData;
	}

	public LiveData<TracingStatus> getTracingStatusLiveData() {
		return tracingStatusLiveData;
	}

	public LiveData<Boolean> getBluetoothEnabledLiveData() {
		return bluetoothEnabledLiveData;
	}

	public void enableTracing(Activity activity, Runnable successCallback, Consumer<Exception> errorCallback,
			Runnable cancelledCallback) {
		DP3T.start(activity,
				() -> {
					sync();
					successCallback.run();
				},
				errorCallback,
				cancelledCallback);
	}

	public void disableTracing() {
		DP3T.stop(getApplication());
	}

	public TracingStatusInterface getTracingStatusInterface() {
		return tracingStatusInterface;
	}

	public void sync() {
		if (!DP3T.isTracingEnabled(getApplication())) {
			//This broadcast prevents the HomeFragment from entering an endless loading state
			BroadcastHelper.sendUpdateAndErrorBroadcast(getApplication());
			return;
		}

		new Thread() {
			@Override
			public void run() {
				DP3T.sync(getApplication());
			}
		}.start();
	}

	private void invalidateBluetoothState() {
		bluetoothEnabledLiveData.setValue(DeviceFeatureHelper.isBluetoothEnabled());
	}

	@Override
	protected void onCleared() {
		getApplication().unregisterReceiver(tracingStatusBroadcastReceiver);
		getApplication().unregisterReceiver(bluetoothReceiver);
	}

	public void loadHistoryEntries() {
		new Thread(() -> {
			List<HistoryEntry> historyEntries = DP3T.getHistoryEntries(getApplication());
			historyMutableLiveData.postValue(historyEntries);
		}).start();
	}

	public LiveData<List<HistoryEntry>> getHistoryLiveDate() {
		return historyMutableLiveData;
	}

}
