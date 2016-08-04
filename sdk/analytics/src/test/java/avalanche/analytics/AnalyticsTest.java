package avalanche.analytics;

import android.os.SystemClock;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import avalanche.analytics.channel.SessionTracker;
import avalanche.analytics.ingestion.models.EventLog;
import avalanche.analytics.ingestion.models.PageLog;
import avalanche.analytics.ingestion.models.StartSessionLog;
import avalanche.analytics.ingestion.models.json.EventLogFactory;
import avalanche.analytics.ingestion.models.json.PageLogFactory;
import avalanche.analytics.ingestion.models.json.StartSessionLogFactory;
import avalanche.core.channel.AvalancheChannel;
import avalanche.core.ingestion.models.Log;
import avalanche.core.ingestion.models.json.LogFactory;
import avalanche.core.utils.StorageHelper;

import static avalanche.core.utils.PrefStorageConstants.KEY_ENABLED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@SuppressWarnings("unused")
@RunWith(PowerMockRunner.class)
@PrepareForTest({SystemClock.class, StorageHelper.PreferencesStorage.class})
public class AnalyticsTest {

    @Before
    public void setUp() {
        Analytics.unsetInstance();
        mockStatic(SystemClock.class);

        /* First call to avalanche.isInstanceEnabled shall return true, initial state. */
        mockStatic(StorageHelper.PreferencesStorage.class);
        final String key = KEY_ENABLED + "_group_analytics";
        when(StorageHelper.PreferencesStorage.getBoolean(key, true)).thenReturn(true);

        /* Then simulate further changes to state. */
        PowerMockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {

                /* Whenever the new state is persisted, make further calls return the new state. */
                boolean enabled = (Boolean) invocation.getArguments()[1];
                when(StorageHelper.PreferencesStorage.getBoolean(key, true)).thenReturn(enabled);
                return null;
            }
        }).when(StorageHelper.PreferencesStorage.class);
        StorageHelper.PreferencesStorage.putBoolean(eq(key), anyBoolean());
    }

    @Test
    public void singleton() {
        Assert.assertSame(Analytics.getInstance(), Analytics.getInstance());
    }

    @Test
    public void checkFactories() {
        Map<String, LogFactory> factories = Analytics.getInstance().getLogFactories();
        assertNotNull(factories);
        assertTrue(factories.remove(StartSessionLog.TYPE) instanceof StartSessionLogFactory);
        assertTrue(factories.remove(PageLog.TYPE) instanceof PageLogFactory);
        assertTrue(factories.remove(EventLog.TYPE) instanceof EventLogFactory);
        assertTrue(factories.isEmpty());
    }

    @Test
    public void notInit() {

        /* Just check log is discarded without throwing any exception. */
        Analytics.trackPage("test", new HashMap<String, String>());
    }

    private void activityResumed(final String expectedName, android.app.Activity activity) {
        Analytics analytics = Analytics.getInstance();
        AvalancheChannel channel = mock(AvalancheChannel.class);
        analytics.onChannelReady(channel);
        analytics.onActivityResumed(activity);
        analytics.onActivityPaused(activity);
        verify(channel).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof PageLog) {
                    PageLog pageLog = (PageLog) item;
                    return expectedName.equals(pageLog.getName());
                }
                return false;
            }
        }), eq(analytics.getGroupName()));
    }

    @Test
    public void activityResumedWithSuffix() {
        activityResumed("My", new MyActivity());
    }

    @Test
    public void activityResumedNoSuffix() {
        activityResumed("SomeScreen", new SomeScreen());
    }

    @Test
    public void activityResumedNamedActivity() {
        activityResumed("Activity", new Activity());
    }

    @Test
    public void disableAutomaticPageTracking() {
        Analytics analytics = Analytics.getInstance();
        assertTrue(Analytics.isAutoPageTrackingEnabled());
        Analytics.setAutoPageTrackingEnabled(false);
        assertFalse(Analytics.isAutoPageTrackingEnabled());
        AvalancheChannel channel = mock(AvalancheChannel.class);
        analytics.onChannelReady(channel);
        analytics.onActivityResumed(new MyActivity());
        verify(channel, never()).enqueue(any(Log.class), anyString());
        Analytics.setAutoPageTrackingEnabled(true);
        assertTrue(Analytics.isAutoPageTrackingEnabled());
        analytics.onActivityResumed(new SomeScreen());
        verify(channel).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof PageLog) {
                    PageLog pageLog = (PageLog) item;
                    return "SomeScreen".equals(pageLog.getName());
                }
                return false;
            }
        }), eq(analytics.getGroupName()));
    }

    @Test
    public void trackEvent() {
        Analytics analytics = Analytics.getInstance();
        AvalancheChannel channel = mock(AvalancheChannel.class);
        analytics.onChannelReady(channel);
        final String name = "testEvent";
        final HashMap<String, String> properties = new HashMap<>();
        properties.put("a", "b");
        Analytics.trackEvent(name, properties);
        verify(channel).enqueue(argThat(new ArgumentMatcher<Log>() {

            @Override
            public boolean matches(Object item) {
                if (item instanceof EventLog) {
                    EventLog eventLog = (EventLog) item;
                    return name.equals(eventLog.getName()) && properties.equals(eventLog.getProperties()) && eventLog.getId() != null;
                }
                return false;
            }
        }), eq(analytics.getGroupName()));
    }

    @Test
    public void setEnabled() {
        Analytics analytics = Analytics.getInstance();
        AvalancheChannel channel = mock(AvalancheChannel.class);
        assertTrue(Analytics.isEnabled());
        Analytics.setEnabled(true);
        assertTrue(Analytics.isEnabled());
        Analytics.setEnabled(false);
        assertFalse(Analytics.isEnabled());
        analytics.onChannelReady(channel);
        verify(channel).clear(analytics.getGroupName());
        verify(channel).removeGroup(eq(analytics.getGroupName()));
        Analytics.trackEvent("test", null);
        Analytics.trackPage("test", null);
        analytics.onActivityResumed(new Activity());
        analytics.onActivityPaused(new Activity());
        verifyNoMoreInteractions(channel);

        /* Enable back, testing double calls. */
        Analytics.setEnabled(true);
        assertTrue(Analytics.isEnabled());
        Analytics.setEnabled(true);
        assertTrue(Analytics.isEnabled());
        verify(channel).addGroup(eq(analytics.getGroupName()), anyInt(), anyInt(), anyInt(), any(AvalancheChannel.GroupListener.class));
        verify(channel).addListener(any(SessionTracker.class));
        Analytics.trackEvent("test", null);
        Analytics.trackPage("test", null);
        verify(channel, times(2)).enqueue(any(Log.class), eq(analytics.getGroupName()));

        /* Disable again. */
        Analytics.setEnabled(false);
        assertFalse(Analytics.isEnabled());
        /* clear and removeGroup are being called in this test method. */
        verify(channel, times(2)).clear(analytics.getGroupName());
        verify(channel, times(2)).removeGroup(eq(analytics.getGroupName()));
        verify(channel).removeListener(any(SessionTracker.class));
        Analytics.trackEvent("test", null);
        Analytics.trackPage("test", null);
        analytics.onActivityResumed(new Activity());
        analytics.onActivityPaused(new Activity());
        verifyNoMoreInteractions(channel);
    }

    /**
     * Activity with page name automatically resolving to "My" (no "Activity" suffix).
     */
    private static class MyActivity extends android.app.Activity {
    }

    /**
     * Activity with page name automatically resolving to "SomeScreen".
     */
    private static class SomeScreen extends android.app.Activity {
    }

    /**
     * Activity with page name automatically resolving to "Activity", because name == suffix.
     */
    private static class Activity extends android.app.Activity {
    }
}