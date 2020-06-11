package ch.admin.bag.dp3t.networking;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dpppt.android.sdk.internal.SyncWorker;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class FakeWorkerTest {
	private Context context;
	private ExecutorService executor;


	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		executor = Executors.newSingleThreadExecutor();
	}

	@Test
	public void doWork() {
		GregorianCalendar calendar = new GregorianCalendar();
		long currentTime = calendar.getTimeInMillis();
		Data fakeReqInputData = new Data.Builder()
				.putLong(FakeWorker.INPUT_REQUEST_TIME_MILLIS, currentTime)
				.putBoolean(FakeWorker.INPUT_REPEATING_EXECUTION, false)
				.build();

		FakeWorker fakeWorker = (FakeWorker) TestWorkerBuilder.from(context, FakeWorker.class, executor)
				.setInputData(fakeReqInputData)
				.build();

		ListenableWorker.Result fakeResults = fakeWorker.doWork();
		assertEquals(fakeResults, ListenableWorker.Result.success());

		if (calendar.get(Calendar.HOUR_OF_DAY) >= 6) calendar.add(Calendar.DAY_OF_YEAR, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 6);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		currentTime = calendar.getTimeInMillis();
		Data syncInputData = new Data.Builder()
				.putLong(SyncWorker.INPUT_SYNC_TIME_MILLIS, currentTime)
				.build();

		SyncWorker syncWorker = (SyncWorker) TestWorkerBuilder.from(context, SyncWorker.class, executor)
				.setInputData(syncInputData)
				.build();

		ListenableWorker.Result syncResult = syncWorker.doWork();
		assertEquals(syncResult, ListenableWorker.Result.success());
	}

}