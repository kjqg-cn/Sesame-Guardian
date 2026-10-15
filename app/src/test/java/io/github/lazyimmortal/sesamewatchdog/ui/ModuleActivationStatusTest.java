package io.github.lazyimmortal.sesamewatchdog.ui;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class ModuleActivationStatusTest {

    @Test
    public void defaultStatus_isNotActivated() {
        assertFalse("默认桩方法应返回 false，未激活状态", ModuleActivationStatus.isHookActivated());
        assertFalse("null Context 时应返回 false", ModuleActivationStatus.isModuleActive(null));
    }
}
