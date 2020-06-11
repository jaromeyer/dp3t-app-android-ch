/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package ch.admin.bag.dp3t.networking;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.dpppt.android.sdk.DP3T;
import org.dpppt.android.sdk.internal.backend.StatusCodeException;
import org.dpppt.android.sdk.internal.logger.Logger;
import org.dpppt.android.sdk.models.ExposeeAuthMethodAuthorization;

import ch.admin.bag.dp3t.networking.errors.ResponseError;
import ch.admin.bag.dp3t.networking.models.AuthenticationCodeRequestModel;
import ch.admin.bag.dp3t.networking.models.AuthenticationCodeResponseModel;
import ch.admin.bag.dp3t.util.ExponentialDistribution;

public class FakeWorker extends Worker {

	private static final String TAG = "FakeWorker";
	private static final String WORK_TAG = "ch.admin.bag.dp3t.FakeWorker";
	private static final String FAKE_AUTH_CODE = "000000000000";

	private static final float SAMPLING_RATE = 0.2f;
	private static final long FACTOR_DAY_MILLIS = 24 * 60 * 60 * 1000L;

	public static final String INPUT_REQUEST_TIME_MILLIS = "INPUT_REQUEST_TIME_MILLIS";
	public static final String INPUT_REPEATING_EXECUTION = "INPUT_REPEATING_EXECUTION";

	public static void safeStartFakeWorker(Context context) {
		startFakeWorker(context, ExistingWorkPolicy.KEEP);
	}

	private static void startFakeWorker(Context context, ExistingWorkPolicy policy) {
		double newDelayDays = ExponentialDistribution.sampleFromStandard() / SAMPLING_RATE;
		long newDelayMillis = Math.round(FACTOR_DAY_MILLIS * newDelayDays);

		Logger.d(TAG, "scheduled for execution in " + newDelayDays + " days");

		Constraints constraints = new Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build();

		OneTimeWorkRequest fakeWorker = new OneTimeWorkRequest.Builder(FakeWorker.class)
				.setConstraints(constraints)
				.setInitialDelay(newDelayMillis, TimeUnit.MILLISECONDS)
				.build();
		WorkManager.getInstance(context).enqueueUniqueWork(WORK_TAG, policy, fakeWorker);

		DP3T.fakeWorkerScheduled(context);
	}

	public FakeWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);
	}

	@NonNull
	@Override
	public ListenableWorker.Result doWork() {
		Logger.d(TAG, "start");
		try {
			long requestTimeMillis = getInputData().getLong(INPUT_REQUEST_TIME_MILLIS, System.currentTimeMillis());
			boolean repeatingExecution = getInputData().getBoolean(INPUT_REPEATING_EXECUTION, true);
			executeFakeRequest(getApplicationContext(), requestTimeMillis);
			if (repeatingExecution) startFakeWorker(getApplicationContext(), ExistingWorkPolicy.APPEND);
		} catch (IOException | ResponseError | StatusCodeException e) {
			Logger.e(TAG, "Failed sending fake request", e);
			return Result.retry();
		}
		Logger.d(TAG, "Successfully sent fake request");
		return Result.success();
	}

	private void executeFakeRequest(Context context, long requestTimeMillis)
			throws IOException, ResponseError, StatusCodeException {
		AuthCodeRepository authCodeRepository = new AuthCodeRepository(context);
		AuthenticationCodeResponseModel accessTokenResponse =
				authCodeRepository.getAccessTokenSync(new AuthenticationCodeRequestModel(FAKE_AUTH_CODE, 1));
		String accessToken = accessTokenResponse.getAccessToken();

		DP3T.sendFakeInfectedRequest(context, new ExposeeAuthMethodAuthorization(getAuthorizationHeader(accessToken)), requestTimeMillis);
	}

	private String getAuthorizationHeader(String accessToken) {
		return "Bearer " + accessToken;
	}

}
