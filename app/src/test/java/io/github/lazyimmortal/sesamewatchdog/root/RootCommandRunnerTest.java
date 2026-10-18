package io.github.lazyimmortal.sesamewatchdog.root;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.lazyimmortal.sesamewatchdog.ipc.WatchdogContract;

public final class RootCommandRunnerTest {

    @Test
    public void recoverCommandContainsFastSleepFlagTrue() {
        String cmd = RootCommandRunner.buildRecoverCommand(0, true, 12345L, true);
        assertTrue(cmd.startsWith("input keyevent 26 && am force-stop --user 0 com.eg.android.AlipayGphone"));
        assertTrue(cmd.contains("--ez " + WatchdogContract.EXTRA_FOREGROUND_RECOVERY + " true"));
        assertTrue(cmd.contains("--el " + WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID + " 12345"));
        assertTrue(cmd.contains("--ez " + WatchdogContract.EXTRA_FAST_SLEEP_ENABLED + " true"));
        assertTrue(cmd.endsWith("-n com.eg.android.AlipayGphone/.AlipayLogin"));
    }

    @Test
    public void recoverCommandContainsFastSleepFlagFalse() {
        String cmd = RootCommandRunner.buildRecoverCommand(0, false, 12345L, false);
        assertFalse(cmd.contains("input keyevent 26"));
        assertTrue(cmd.startsWith("am force-stop --user 0 com.eg.android.AlipayGphone"));
        assertTrue(cmd.contains("--ez " + WatchdogContract.EXTRA_FOREGROUND_RECOVERY + " true"));
        assertTrue(cmd.contains("--el " + WatchdogContract.EXTRA_FOREGROUND_RECOVERY_REQUEST_ID + " 12345"));
        assertTrue(cmd.contains("--ez " + WatchdogContract.EXTRA_FAST_SLEEP_ENABLED + " false"));
        assertTrue(cmd.endsWith("-n com.eg.android.AlipayGphone/.AlipayLogin"));
    }

    @Test
    public void bringToFrontCommandContainsFastSleepFlag() {
        String cmdEnabled = RootCommandRunner.buildBringToFrontCommand(0, 99999L, false, true);
        assertTrue(cmdEnabled.contains("--ez " + WatchdogContract.EXTRA_FAST_SLEEP_ENABLED + " true"));

        String cmdDisabled = RootCommandRunner.buildBringToFrontCommand(0, 99999L, false, false);
        assertTrue(cmdDisabled.contains("--ez " + WatchdogContract.EXTRA_FAST_SLEEP_ENABLED + " false"));
    }
}
