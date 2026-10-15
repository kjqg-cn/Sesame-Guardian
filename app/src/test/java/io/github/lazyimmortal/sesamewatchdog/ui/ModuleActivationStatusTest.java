package io.github.lazyimmortal.sesamewatchdog.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ModuleActivationStatusTest {

    @Test
    public void defaultStatus_isNotActivated() {
        assertFalse("默认桩方法应返回 false，未激活状态", ModuleActivationStatus.isHookActivated());
        assertFalse("null Context 时应返回 false", ModuleActivationStatus.isModuleActive(null));
    }

    @Test
    public void isRecentInteraction_validWithinThreshold() {
        long bootTime = 1_700_000_000_000L;
        long recordedElapsed = 100_000L;
        long nowElapsed = recordedElapsed + (10L * 60L * 1000L); // 10 分钟后
        assertTrue("同开机周期且在阈值内应有效",
                ModuleActivationStatus.isRecentInteraction(
                        recordedElapsed, bootTime, nowElapsed, bootTime));
    }

    @Test
    public void isRecentInteraction_invalidBeyondThreshold() {
        long bootTime = 1_700_000_000_000L;
        long recordedElapsed = 100_000L;
        long nowElapsed = recordedElapsed + (31L * 60L * 1000L); // 31 分钟后，超阈值
        assertFalse("超过 30 分钟阈值应判定失效",
                ModuleActivationStatus.isRecentInteraction(
                        recordedElapsed, bootTime, nowElapsed, bootTime));
    }

    @Test
    public void isRecentInteraction_invalidWhenRebooted() {
        long oldBootTime = 1_700_000_000_000L;
        long newBootTime = oldBootTime + (3_600_000L); // 重启后新的开机基准时间
        long recordedElapsed = 500_000L;
        long nowElapsed = 60_000L; // 新开机周期刚启动 1 分钟
        assertFalse("跨开机周期（开机时间基准改变）应判定失效",
                ModuleActivationStatus.isRecentInteraction(
                        recordedElapsed, oldBootTime, nowElapsed, newBootTime));
    }

    @Test
    public void isRecentInteraction_invalidWhenElapsedReverses() {
        long bootTime = 1_700_000_000_000L;
        long recordedElapsed = 500_000L;
        long nowElapsed = 400_000L; // 单调时间倒退异常
        assertFalse("单调时间倒退应判定失效",
                ModuleActivationStatus.isRecentInteraction(
                        recordedElapsed, bootTime, nowElapsed, bootTime));
    }

    @Test
    public void isRecentInteraction_invalidZeroOrNegativeInputs() {
        assertFalse("零值记录应判定失效",
                ModuleActivationStatus.isRecentInteraction(0L, 0L, 1000L, 1000L));
        assertFalse("负值记录应判定失效",
                ModuleActivationStatus.isRecentInteraction(-1L, -1L, 1000L, 1000L));
    }
}
